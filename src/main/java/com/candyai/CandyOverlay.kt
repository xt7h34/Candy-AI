package com.candyai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.abs

/**
 * Candy Edge, the floating Candy: she peeks from the edge of the phone's screen, over every app.
 * It needs "Display over other apps" (the person allows it once). Android makes any service that keeps running show an
 * ongoing notification, so it is made as quiet as possible: lowest importance, no badge, hidden on the lock screen, and the
 * app never asks for the notification permission (on Android 13+ that keeps it out of the notification shade). She hides while one of this app's own screens is open (inside the app the
 * in-app Candy is there instead).
 */
object CandyOverlay {
    /** How many of this app's activities are on screen right now. While there is one, the overlay Candy steps aside. */
    var foreground = 0
        private set

    internal var listener: (() -> Unit)? = null

    fun activityStarted() {
        foreground++
        listener?.invoke()
    }

    fun activityStopped() {
        if (foreground > 0) foreground--
        listener?.invoke()
    }

    fun canDraw(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun start(ctx: Context) {
        try {
            ctx.startForegroundService(Intent(ctx, CandyOverlayService::class.java))
        } catch (_: Exception) {
            // Android refused to start it right now (the app is not on screen); it starts the next time the app opens
        }
    }

    fun stop(ctx: Context) {
        ctx.stopService(Intent(ctx, CandyOverlayService::class.java))
    }

    /** Called when the app opens: brings her back if she is switched on and the permission is there. */
    fun ensure(ctx: Context) {
        if (Store.overlayOn && canDraw(ctx)) start(ctx)
    }
}

/** Where she sits on the phone's screen. Kept on the phone. */
private object OverlayPrefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("candy_overlay", Context.MODE_PRIVATE)
    fun left(ctx: Context): Boolean = p(ctx).getBoolean("left", false)
    fun y(ctx: Context): Float = p(ctx).getFloat("y", 0.55f)
    fun hidden(ctx: Context): Boolean = p(ctx).getBoolean("hidden", false)
    /** The tab pulled away from the edge: it floats as a circle (position as a fraction of the free screen area). */
    fun floating(ctx: Context): Boolean = p(ctx).getBoolean("floating", false)
    /** She came out of a floating circle, so tucking her away again puts the circle back where it was. */
    fun was(ctx: Context): Boolean = p(ctx).getBoolean("was_floating", false)
    fun fx(ctx: Context): Float = p(ctx).getFloat("fx", 0.5f)
    fun fy(ctx: Context): Float = p(ctx).getFloat("fy", 0.5f)
    fun saveFloat(ctx: Context, floating: Boolean, was: Boolean, fx: Float, fy: Float) {
        p(ctx).edit().putBoolean("floating", floating).putBoolean("was_floating", was).putFloat("fx", fx).putFloat("fy", fy).apply()
    }
    fun save(ctx: Context, left: Boolean, y: Float, hidden: Boolean) {
        p(ctx).edit().putBoolean("left", left).putFloat("y", y).putBoolean("hidden", hidden).apply()
    }
}

private val PEEK_W = 104.dp
private val PEEK_H = 144.dp
private val TAB_W = 28.dp
private val TAB_H = 56.dp
private val TAB_WIN_W = 52.dp   // the tab's window is a little wider than the tab so it can grow into a circle
private const val FLOAT_DIST_DP = 36f   // pulled this far from the edge, the tab becomes a floating circle

class CandyOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    // a Compose view needs these three owners, and a service has none of them, so it plays all three
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    private lateinit var wm: WindowManager
    private lateinit var lp: WindowManager.LayoutParams
    private var root: FrameLayout? = null
    private val handler = Handler(Looper.getMainLooper())

    private var edgeLeft by mutableStateOf(false)
    private var hidden by mutableStateOf(false)
    private var yFrac = 0.55f
    private var floating = false              // hidden, and sitting away from the edge as a circle
    private var wasFloating = false
    private var fx = 0.5f
    private var fy = 0.5f
    private var skin by mutableStateOf(TabSkin.Rect)
    private var floatLook by mutableStateOf(false)   // what the tab looks like right now (circle or edge tab), also while dragging

    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var moved = false
    private var longFired = false
    private val longPress = Runnable {
        longFired = true
        root?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        openApp()
    }

    override fun onCreate() {
        super.onCreate()
        Store.init(applicationContext)
        ThemeMode.load()
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        edgeLeft = OverlayPrefs.left(this)
        yFrac = OverlayPrefs.y(this)
        hidden = OverlayPrefs.hidden(this)
        floating = hidden && OverlayPrefs.floating(this)
        wasFloating = OverlayPrefs.was(this)
        fx = OverlayPrefs.fx(this)
        fy = OverlayPrefs.fy(this)
        floatLook = floating
        skin = TabSkin.of(Store.tabSkin)
        CandyOverlay.listener = { syncVisibility() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Store.overlayOn = false
            stopSelf()
            return START_NOT_STICKY
        }
        goForeground()
        if (!Store.overlayOn || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (root == null) showOverlay()
        return START_STICKY
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (root != null) applyLayout()
    }

    override fun onDestroy() {
        CandyOverlay.listener = null
        handler.removeCallbacksAndMessages(null)
        root?.let { try { wm.removeView(it) } catch (_: Exception) { } }
        root = null
        registry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        super.onDestroy()
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        // the earlier channel could not be changed any more, so the quieter one has a new id
        nm.deleteNotificationChannel("candy_overlay")
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.overlay_channel), NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            },
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, CandyOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_edge_tile)
            .setContentTitle(getString(R.string.overlay_notif_title))
            .setContentText(getString(R.string.overlay_notif_text))
            .setContentIntent(open)
            .addAction(0, getString(R.string.overlay_notif_stop), stop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun showOverlay() {
        lp = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.LEFT }

        val animated = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        val frame = FrameLayout(this)
        val compose = ComposeView(this)
        compose.setContent { CandyTheme { OverlayContent(hidden, edgeLeft, animated, floatLook, skin) } }
        frame.addView(compose)
        frame.setViewTreeLifecycleOwner(this)
        frame.setViewTreeSavedStateRegistryOwner(this)
        frame.setViewTreeViewModelStoreOwner(this)
        // the Compose content has no touch handling of its own, so every touch comes here: tap, long press and drag
        frame.setOnTouchListener { v, e -> onTouch(v, e) }
        root = frame

        applyLayout(update = false)
        wm.addView(frame, lp)
        registry.currentState = Lifecycle.State.RESUMED
        syncVisibility()
    }

    /** Puts the window where she is: on the left or right edge, at her height, big or just the small tab. */
    private fun applyLayout(update: Boolean = true) {
        val dm = resources.displayMetrics
        val d = dm.density
        val peekH = (PEEK_H.value * d).toInt()
        val tabH = (TAB_H.value * d).toInt()
        val w = ((if (hidden) TAB_WIN_W else PEEK_W).value * d).toInt()
        val h = if (hidden) tabH else peekH
        val yMax = (dm.heightPixels - peekH - (48 * d).toInt()).coerceAtLeast(1)
        val baseY = (yFrac * yMax).toInt().coerceIn(0, yMax)
        lp.width = w
        lp.height = h
        if (hidden && floating) {
            lp.x = (fx * (dm.widthPixels - w).coerceAtLeast(1)).toInt().coerceIn(0, (dm.widthPixels - w).coerceAtLeast(0))
            lp.y = (fy * (dm.heightPixels - h).coerceAtLeast(1)).toInt().coerceIn(0, (dm.heightPixels - h).coerceAtLeast(0))
        } else {
            lp.x = if (edgeLeft) 0 else dm.widthPixels - w
            lp.y = if (hidden) baseY + (peekH - tabH) / 2 else baseY
        }
        floatLook = hidden && floating
        if (update) root?.let { try { wm.updateViewLayout(it, lp) } catch (_: Exception) { } }
    }

    private fun onTouch(v: View, e: MotionEvent): Boolean {
        CandyIdle.touched()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX
                downY = e.rawY
                startX = lp.x
                startY = lp.y
                moved = false
                longFired = false
                handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - downX
                val dy = e.rawY - downY
                val slop = ViewConfiguration.get(this).scaledTouchSlop
                if (!moved && (abs(dx) > slop || abs(dy) > slop)) {
                    moved = true
                    handler.removeCallbacks(longPress)
                }
                if (moved) {
                    val dm = resources.displayMetrics
                    if (hidden) {
                        // the tab follows the finger anywhere; far from the edge it turns into a circle, near the edge back into the tab
                        lp.x = (startX + dx).toInt().coerceIn(0, (dm.widthPixels - lp.width).coerceAtLeast(0))
                        val edgeDist = minOf(lp.x, dm.widthPixels - lp.width - lp.x)
                        floatLook = edgeDist > FLOAT_DIST_DP * dm.density
                    } else {
                        lp.x = (startX + dx).toInt()
                    }
                    lp.y = (startY + dy).toInt().coerceIn(0, (dm.heightPixels - lp.height).coerceAtLeast(0))
                    try { wm.updateViewLayout(v, lp) } catch (_: Exception) { }
                }
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress)
                if (moved) {
                    finishDrag()
                } else if (!longFired) {
                    if (hidden) {
                        // she comes out of the nearest edge, at the height of the tab or circle
                        val dm = resources.displayMetrics
                        val d = dm.density
                        val peekH = (PEEK_H.value * d).toInt()
                        val yMax = (dm.heightPixels - peekH - (48 * d).toInt()).coerceAtLeast(1)
                        yFrac = (((lp.y + lp.height / 2f) - peekH / 2f) / yMax).coerceIn(0f, 1f)
                        edgeLeft = lp.x + lp.width / 2 < dm.widthPixels / 2
                        wasFloating = floating
                        hidden = false
                        floating = false
                        OverlayPrefs.save(this, edgeLeft, yFrac, false)
                        OverlayPrefs.saveFloat(this, false, wasFloating, fx, fy)
                        applyLayout()
                    } else {
                        openPanel()
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPress)
                if (moved) finishDrag()
            }
        }
        return true
    }

    /** Let go after dragging: she snaps to the nearest edge, or tucks away when pushed into the edge she is on. */
    private fun finishDrag() {
        moved = false
        val dm = resources.displayMetrics
        val d = dm.density
        val peekH = (PEEK_H.value * d).toInt()
        if (hidden) {
            if (floatLook) {
                // let go away from the edge: she stays as a floating circle right here
                floating = true
                fx = (lp.x.toFloat() / (dm.widthPixels - lp.width).coerceAtLeast(1)).coerceIn(0f, 1f)
                fy = (lp.y.toFloat() / (dm.heightPixels - lp.height).coerceAtLeast(1)).coerceIn(0f, 1f)
            } else {
                // let go near an edge: back to the tab on the nearest edge
                floating = false
                edgeLeft = lp.x + lp.width / 2 < dm.widthPixels / 2
                val yMax0 = (dm.heightPixels - peekH - (48 * d).toInt()).coerceAtLeast(1)
                yFrac = (((lp.y + lp.height / 2f) - peekH / 2f) / yMax0).coerceIn(0f, 1f)
            }
            OverlayPrefs.save(this, edgeLeft, yFrac, true)
            OverlayPrefs.saveFloat(this, floating, wasFloating, fx, fy)
            applyLayout()
            return
        }
        val w = lp.width
        val baseX = if (edgeLeft) 0 else dm.widthPixels - w
        val dx = lp.x - baseX
        val pushedIn = if (edgeLeft) dx < -w * 0.4f else dx > w * 0.4f
        val yMax = (dm.heightPixels - peekH - (48 * d).toInt()).coerceAtLeast(1)
        val centerY = lp.y + lp.height / 2f
        yFrac = ((centerY - peekH / 2f) / yMax).coerceIn(0f, 1f)
        if (!hidden && pushedIn) {
            hidden = true
            floating = false   // pushed back into the edge she always becomes the tab again, even if she came out of a circle
        } else {
            edgeLeft = lp.x + w / 2 < dm.widthPixels / 2
        }
        OverlayPrefs.save(this, edgeLeft, yFrac, hidden)
        OverlayPrefs.saveFloat(this, floating, wasFloating, fx, fy)
        applyLayout()
    }

    /** She steps aside while one of this app's screens is open, and comes back after. */
    private fun syncVisibility() {
        val r = root ?: return
        skin = TabSkin.of(Store.tabSkin)   // the shape may have just been changed in Settings
        val show = CandyOverlay.foreground == 0
        r.visibility = if (show) View.VISIBLE else View.GONE
        lp.flags = if (show) {
            lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        try { wm.updateViewLayout(r, lp) } catch (_: Exception) { }
    }

    /** Tap: the ask panel opens over whatever app is open. */
    private fun openPanel() {
        try {
            startActivity(Intent(this, EdgeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { }
    }

    /** Long press: the app itself. */
    private fun openApp() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        } catch (_: Exception) { }
    }

    private companion object {
        const val CHANNEL = "candy_overlay_quiet"
        const val NOTIF_ID = 4801
        const val ACTION_STOP = "com.candyai.OVERLAY_STOP"
    }
}

@Composable
private fun OverlayContent(hidden: Boolean, left: Boolean, animated: Boolean, circle: Boolean, skin: TabSkin) {
    // her place and shape depend only on the edge she sits on, never on the app language
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        if (hidden && skin != TabSkin.Rect) {
            SkinBubble(skin, left, circle, animated)
        } else if (hidden) {
            // the tab: flat on the edge, or a round bubble when it is pulled away (it morphs between the two)
            // no overshoot: a corner radius below zero must never happen
            val spec = if (animated) spring<Dp>(dampingRatio = 1f, stiffness = 500f) else snap<Dp>()
            val w by animateDpAsState(if (circle) 48.dp else TAB_W, spec, label = "w")
            val h by animateDpAsState(if (circle) 48.dp else TAB_H, spec, label = "h")
            val edgeR by animateDpAsState(if (circle) 24.dp else 0.dp, spec, label = "er")
            val innerR by animateDpAsState(if (circle) 24.dp else 16.dp, spec, label = "ir")
            val face by animateDpAsState(if (circle) 38.dp else 22.dp, spec, label = "face")
            Box(
                Modifier.size(TAB_WIN_W, TAB_H),
                contentAlignment = if (circle) Alignment.Center else if (left) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .size(w.coerceAtLeast(1.dp), h.coerceAtLeast(1.dp))
                        .clip(
                            if (left) RoundedCornerShape(topStart = edgeR.coerceAtLeast(0.dp), bottomStart = edgeR.coerceAtLeast(0.dp), topEnd = innerR.coerceAtLeast(0.dp), bottomEnd = innerR.coerceAtLeast(0.dp))
                            else RoundedCornerShape(topStart = innerR.coerceAtLeast(0.dp), bottomStart = innerR.coerceAtLeast(0.dp), topEnd = edgeR.coerceAtLeast(0.dp), bottomEnd = edgeR.coerceAtLeast(0.dp)),
                        )
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.candy_ai_avatar),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(face.coerceAtLeast(1.dp)).clip(CircleShape),
                    )
                }
            }
        } else {
            CandyPeek(PEEK_W, PEEK_H, animated, 0, 0L, mirror = left)
        }
    }
}

/** The donut, crown or bear: whole and round-ish when floating, and on the edge the donut is half hidden while the crown and bear stay whole (mirrored on the left). */
@Composable
private fun SkinBubble(skin: TabSkin, left: Boolean, circle: Boolean, animated: Boolean) {
    val spec = if (animated) spring<Dp>(dampingRatio = 1f, stiffness = 500f) else snap<Dp>()
    val half = if (skin == TabSkin.Donut && !circle) 24.dp else 0.dp
    val shift by animateDpAsState(if (left) -half else half, spec, label = "shift")
    val size by animateDpAsState(if (circle) 48.dp else 44.dp, spec, label = "size")
    Box(
        Modifier.size(TAB_WIN_W, TAB_H),
        contentAlignment = if (circle) Alignment.Center else if (left) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        SkinArt(skin, size.coerceAtLeast(1.dp), Modifier.offset(x = shift), mirror = left && !circle && skin == TabSkin.Bear)
    }
}
