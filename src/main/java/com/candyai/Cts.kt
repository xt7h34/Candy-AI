package com.candyai

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.io.IOException
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Hands the screenshot from the assistant session to [CtsActivity] (a Bitmap is too big for an Intent). */
object CtsHolder {
    @Volatile var shot: Bitmap? = null
}

/**
 * Circle to Search, Candy style: the screen you were on is frozen, you draw a circle around something,
 * and Candy answers in a sheet from the bottom. Started by the assistant gesture (see EdgeSession).
 */
class CtsActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

    override fun onStart() {
        super.onStart()
        CandyOverlay.activityStarted()
    }

    override fun onStop() {
        CandyOverlay.activityStopped()
        super.onStop()
    }

    override fun onDestroy() {
        EdgeSession.current?.hide()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(applicationContext)
        ThemeMode.load()
        // the real screenshot from the assistant, or a picture picked in Settings to try the screen out (decoded off the main thread)
        val real = CtsHolder.shot.also { CtsHolder.shot = null }
        val uri = intent.data
        if (real == null && uri == null) {
            startActivity(Intent(this, EdgeActivity::class.java))
            finish()
            return
        }
        setContent {
            CandyTheme {
                CompositionLocalProvider(LocalLayoutDirection provides Lang.direction(this@CtsActivity)) {
                    var shot by remember { mutableStateOf(real) }
                    LaunchedEffect(Unit) {
                        if (shot == null && uri != null) {
                            val b = withContext(Dispatchers.IO) { fitToScreen(uri) }
                            if (b == null) {
                                startActivity(Intent(this@CtsActivity, EdgeActivity::class.java))
                                finish()
                            } else {
                                shot = b
                            }
                        }
                    }
                    shot?.let { s ->
                        CtsScreen(
                            shot = s,
                            onClose = { closeWith(s) },
                            onOpenFull = { id ->
                                val open = Intent(this@CtsActivity, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                if (id != null) open.putExtra("open_conv", id)
                                startActivity(open)
                                closeWith(s)
                            },
                        )
                    }
                }
            }
        }
    }

    /** The screenshot is only kept in memory. If the person chose to keep screenshots, it is saved to Pictures/Candy as the screen closes. */
    private fun closeWith(shot: Bitmap) {
        if (Store.ctsSave && intent.data == null) saveShot(applicationContext, shot)
        finish()
    }

    /** A picture picked in Settings, drawn centered on a screen-sized canvas so it behaves like a real screenshot. */
    private fun fitToScreen(uri: Uri): Bitmap? = try {
        val dm = resources.displayMetrics
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1800) sample *= 2
        val src = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        if (src == null) null else {
            val out = Bitmap.createBitmap(dm.widthPixels, dm.heightPixels, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(out)
            canvas.drawColor(android.graphics.Color.BLACK)
            val k = minOf(out.width.toFloat() / src.width, out.height.toFloat() / src.height)
            val w = (src.width * k).toInt()
            val h = (src.height * k).toInt()
            val dst = android.graphics.Rect((out.width - w) / 2, (out.height - h) / 2, (out.width + w) / 2, (out.height + h) / 2)
            canvas.drawBitmap(src, null, dst, null)
            out
        }
    } catch (_: Exception) {
        null
    }
}

private fun saveShot(ctx: Context, bmp: Bitmap) {
    if (Build.VERSION.SDK_INT < 29) return   // older phones need a storage permission the app does not ask for
    Thread {
        try {
            val v = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "candy_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Candy")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: return@Thread
            ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (_: Exception) {
        }
    }.start()
}

@Composable
fun CtsScreen(shot: Bitmap, onClose: () -> Unit, onOpenFull: (String?) -> Unit) {
    val ctx = LocalContext.current
    val c = LocalCandy.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val chat = remember { SeeChat(ctx, scope, speakReplies = false, convTitle = ctx.getString(R.string.cts_conv_title)) }
    if (chat.actions.isNotEmpty()) ActionConfirm(chat.actions) { chat.actions = emptyList() }
    val img = remember(shot) { shot.asImageBitmap() }

    val stroke = remember { ArrayList<Offset>() }                   // what the finger is drawing (not observable: redraws are driven by [tick])
    val livePath = remember { Path() }
    var tick by remember { mutableIntStateOf(0) }
    var drawingNow by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<Path?>(null) }       // the closed shape once the finger is up
    var box by remember { mutableStateOf(IntSize.Zero) }
    var region by remember { mutableStateOf<Pending?>(null) }       // the circled part, packed to send
    var attached by remember { mutableStateOf(false) }              // is the current picture already in the conversation?
    val hasChat = chat.hasChat

    fun fail() { chat.error = ctx.getString(R.string.attach_fail) }

    /** Sends text. The picture (the circle, or the whole screen if nothing was circled) goes along only the first time. */
    fun askWith(text: String, extra: List<Pending> = emptyList()) {
        if (chat.sending) return
        scope.launch {
            val first = if (attached) emptyList() else listOfNotNull(region ?: AttachKit.fromBitmap(shot, "screen.jpg"))
            if (!attached && first.isEmpty()) { fail(); return@launch }
            attached = true
            chat.ask(text, first + extra)
        }
    }

    fun onCircled(pts: List<Offset>) {
        if (pts.size < 8 || box.width == 0) { stroke.clear(); livePath.reset(); tick++; return }
        val minX = pts.minOf { it.x }; val maxX = pts.maxOf { it.x }
        val minY = pts.minOf { it.y }; val maxY = pts.maxOf { it.y }
        if (maxX - minX < 40f && maxY - minY < 40f) { stroke.clear(); livePath.reset(); tick++; return }   // a tap, not a circle
        selection = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
            close()
        }
        val pad = with(density) { 20.dp.toPx() }
        val sx = shot.width.toFloat() / box.width
        val sy = shot.height.toFloat() / box.height
        val l = ((minX - pad) * sx).coerceIn(0f, shot.width - 2f)
        val t = ((minY - pad) * sy).coerceIn(0f, shot.height - 2f)
        val r = ((maxX + pad) * sx).coerceIn(l + 1f, shot.width.toFloat())
        val b = ((maxY + pad) * sy).coerceIn(t + 1f, shot.height.toFloat())
        scope.launch {
            // a new circle while Candy is still answering the old one: stop the old answer first
            if (chat.sending) {
                chat.stop()
                withTimeoutOrNull(4000) { while (chat.sending) delay(100) }
            }
            val crop = withContext(Dispatchers.Default) {
                try { Bitmap.createBitmap(shot, l.toInt(), t.toInt(), (r - l).toInt().coerceAtLeast(1), (b - t).toInt().coerceAtLeast(1)) } catch (_: Exception) { null }
            }
            val packed = crop?.let { AttachKit.fromBitmap(it, "region.jpg") }
            if (packed == null) { fail(); return@launch }
            region = packed
            attached = true
            // like Google: the answer starts as soon as the circle is closed
            chat.ask(ctx.getString(R.string.cts_default_prompt), listOf(packed))
        }
    }

    BackHandler { onClose() }

    // a soft glow around the screen when it freezes, then it fades
    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        intro.animateTo(1f, tween(450))
        delay(350)
        intro.animateTo(0f, tween(700))
    }
    val scrim by animateFloatAsState(if (selection == null) 0.28f else 0f, tween(300), label = "scrim")
    val glow = remember { Animatable(1f) }
    LaunchedEffect(selection) {
        if (selection != null) {
            glow.snapTo(1f)
            glow.animateTo(0.55f, tween(900))
            glow.animateTo(1f, tween(900))
        }
    }

    Box(Modifier.fillMaxSize().onSizeChanged { box = it }) {
        // the frozen screen
        Image(img, null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = scrim }.background(Color.Black))

        // drawing surface: the circle, with Candy's glow
        // everything below is read while drawing, not while composing, so drawing does not recompose the screen
        val outside = remember(selection, box) {
            selection?.let { s ->
                Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(0f, 0f, box.width.toFloat(), box.height.toFloat()))
                    addPath(s)
                }
            }
        }
        Canvas(
            Modifier.fillMaxSize().pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    selection = null
                    stroke.clear()
                    livePath.reset()
                    livePath.moveTo(down.position.x, down.position.y)
                    stroke.add(down.position)
                    drawingNow = true
                    tick++
                    do {
                        val ev = awaitPointerEvent()
                        for (ch in ev.changes) {
                            if (!ch.pressed) continue
                            val last = stroke.last()
                            val dx = ch.position.x - last.x
                            val dy = ch.position.y - last.y
                            if (dx * dx + dy * dy >= 16f) {   // skip points less than 4px apart
                                stroke.add(ch.position)
                                livePath.lineTo(ch.position.x, ch.position.y)
                                tick++
                            }
                            ch.consume()
                        }
                    } while (ev.changes.any { it.pressed })
                    drawingNow = false
                    onCircled(stroke.toList())
                }
            },
        ) {
            tick   // read: a new point was added
            val sel = selection
            if (outside != null) drawPath(outside, Color.Black.copy(alpha = 0.55f))
            val shape = sel ?: if (stroke.size > 1) livePath else null
            if (shape != null) {
                val round = { w: Float -> Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round) }
                if (sel != null) drawPath(shape, c.accent.copy(alpha = 0.25f * glow.value), style = round(16.dp.toPx()))
                drawPath(shape, c.accent.copy(alpha = 0.6f * glow.value), style = round(8.dp.toPx()))
                drawPath(shape, Color.White, style = round(3.dp.toPx()))
            }
            if (intro.value > 0.01f) {
                drawRect(
                    brush = Brush.verticalGradient(listOf(c.accent, c.userBubble, c.accent)),
                    alpha = intro.value * 0.9f,
                    style = Stroke(width = 8.dp.toPx()),
                )
            }
        }

        // close
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.45f),
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp).size(CandyDims.tapLarge).clip(CircleShape).clickable { onClose() },
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, stringResource(R.string.cts_close), tint = Color.White) }
        }

        // hint, until the first circle
        if (selection == null && !drawingNow && !hasChat) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 18.dp).padding(horizontal = 64.dp),
            ) {
                Text(stringResource(R.string.cts_hint), color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }

        // the Candy See capsule: typing or "Ask anything" sends the whole screen (or the circle) and waits for the question
        SeeCapsule(
            chat = chat,
            hint = stringResource(R.string.see_ask_anything),
            onSubmit = { t, extra -> askWith(t, extra) },
            onOpenFull = onOpenFull,
            modifier = Modifier.align(Alignment.BottomCenter),
            frozen = true,
            onTranslateScreen = { askWith(ctx.getString(R.string.cts_p_translate)) },
        )
    }
}
