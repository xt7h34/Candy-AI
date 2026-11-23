package com.candyai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.Base64
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 * Candy's hands. It reads the screen as a list of numbered elements, sends it to the server, and does the step Claude picks
 * (tap, type, scroll, back, open an app). A pill at the top shows what she is doing with a Stop button; sensitive steps
 * (send, pay, delete...) always ask first. It only runs after the person asks and approves, and stops the moment they press Stop.
 */
class CandyAgentService : AccessibilityService() {
    companion object {
        @Volatile var inst: CandyAgentService? = null
        private const val MAX_NODES = 220

        fun enabled(ctx: Context): Boolean {
            val v = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val a = ComponentName(ctx, CandyAgentService::class.java).flattenToString()
            val b = ctx.packageName + "/" + CandyAgentService::class.java.name
            return v.split(':').any { it.equals(a, true) || it.equals(b, true) }
        }

        /** Words that make a tap need the person's OK even when the model did not ask for it. */
        private val sensitive = Regex("(send|pay|buy|order|purchase|delete|remove|post|publish|transfer|submit|confirm|place order|ارسال|إرسال|ابعت|أرسل|ادفع|دفع|شراء|اشتري|اطلب|احذف|حذف|نشر|انشر|تحويل|حوّل|تأكيد|تاكيد|إتمام|اتمام)", RegexOption.IGNORE_CASE)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var nodes: List<AccessibilityNodeInfo> = emptyList()
    private var shotScale = 1f
    private var pill: View? = null
    private var pillText: TextView? = null
    private var card: View? = null
    private val wm get() = getSystemService(Context.WINDOW_SERVICE) as WindowManager

    override fun attachBaseContext(base: Context) { super.attachBaseContext(Lang.wrap(base)) }
    override fun onServiceConnected() { inst = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { stop() }
    override fun onDestroy() { stop(); inst = null; super.onDestroy() }

    val running: Boolean get() = job?.isActive == true

    fun start(goal: String) {
        if (running) return
        job = scope.launch { run(goal) }
    }

    fun stop() {
        job?.cancel()
        job = null
        hideCard()
        hidePill()
    }

    // ───────────── the loop ─────────────

    private suspend fun run(goal: String) {
        var session: String? = null
        try {
            showPill(getString(R.string.agent_starting))
            session = Api.agentStart(goal)
            performGlobalAction(GLOBAL_ACTION_HOME)
            var last = "started; the phone is on the home screen"
            var wantShot = false
            while (true) {
                delay(700)
                val snap = snapshot()
                val shot = if (wantShot || snap.count < 4) screenshotJpeg()?.first else null
                wantShot = false
                val r = Api.agentStep(session, snap.text, shot, last, snap.pkg, "${snap.w}x${snap.h}")
                setPill(r.note.ifBlank { getString(R.string.agent_working) })
                val node = r.id?.let { nodes.getOrNull(it) }
                // sensitive steps always ask; the model asks for the ones it knows about, and tapping a "send / pay / delete" button is caught here too
                val label = node?.let { (it.text ?: it.contentDescription)?.toString() ?: "" } ?: ""
                val needsOk = r.confirm.isNotBlank() || (r.action == "click" && sensitive.containsMatchIn(label))
                if (needsOk) {
                    val msg = r.confirm.ifBlank { getString(R.string.agent_sensitive, label.take(40)) }
                    if (!confirm(msg)) { last = "the user declined this step; do not do it, choose another way or finish"; continue }
                }
                when (r.action) {
                    "finish" -> { notify(r.summary.ifBlank { getString(R.string.agent_done) }); return }
                    "ask" -> {
                        val ans = ask(r.question)
                        last = if (ans == null) "the user did not answer" else "the user answered: $ans"
                    }
                    "click" -> last = if (node == null) "failed: no element with that id" else if (clickNode(node)) "clicked [${r.id}]" else "failed to click [${r.id}]"
                    "type" -> last = when {
                        node == null -> "failed: no element with that id"
                        node.isPassword -> "refused: never type into a password field, ask the user"
                        else -> if (typeInto(node, r.text)) "typed into [${r.id}]" else "failed to type into [${r.id}]"
                    }
                    "scroll" -> last = if (scroll(r.direction, node)) "scrolled ${r.direction}" else "could not scroll"
                    "tap" -> last = if (tapShot(r.x, r.y)) "tapped (${r.x},${r.y})" else "tap failed"
                    "back" -> { performGlobalAction(GLOBAL_ACTION_BACK); last = "went back" }
                    "home" -> { performGlobalAction(GLOBAL_ACTION_HOME); last = "went home" }
                    "open_app" -> last = if (AgentActions.run(this, AgentAction("open", listOf(r.app)))) "opened ${r.app}" else "no app named ${r.app}"
                    "wait" -> { delay(r.seconds * 1000L); last = "waited" }
                    "look" -> { wantShot = true; last = "taking a screenshot" }
                    else -> last = "unknown action"
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.agent_err), Toast.LENGTH_LONG).show()
        } finally {
            val s = session
            if (s != null) CoroutineScope(Dispatchers.IO).launch { try { Api.agentEnd(s) } catch (_: Exception) { } }
            hideCard()
            hidePill()
        }
    }

    // ───────────── reading the screen ─────────────

    class Snap(val text: String, val pkg: String, val count: Int, val w: Int, val h: Int)

    private fun kind(n: AccessibilityNodeInfo): String {
        val c = n.className?.toString() ?: ""
        return when {
            n.isEditable || c.contains("EditText") -> "edit"
            c.contains("Button") -> "button"
            c.contains("CheckBox") || c.contains("Switch") || c.contains("Toggle") || c.contains("Radio") -> "toggle"
            c.contains("ImageView") || c.contains("Image") -> "image"
            c.contains("RecyclerView") || c.contains("ListView") || c.contains("ScrollView") || c.contains("ViewPager") -> "list"
            c.contains("TextView") -> "text"
            else -> "view"
        }
    }

    private fun snapshot(): Snap {
        val root = rootInActiveWindow
        val dm = resources.displayMetrics
        if (root == null) { nodes = emptyList(); return Snap("(the screen could not be read)", "?", 0, dm.widthPixels, dm.heightPixels) }
        val list = ArrayList<AccessibilityNodeInfo>()
        val sb = StringBuilder()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (list.size >= MAX_NODES || depth > 30 || !n.isVisibleToUser) return
            val raw = if (n.isPassword) "(password field)" else (n.text ?: n.contentDescription ?: if (Build.VERSION.SDK_INT >= 26) n.hintText else null)?.toString() ?: ""
            val label = raw.replace('\n', ' ').trim().take(80)
            val interactive = n.isClickable || n.isEditable || n.isScrollable || n.isCheckable
            if (label.isNotEmpty() || interactive) {
                val flags = ArrayList<String>()
                if (n.isClickable) flags.add("clickable")
                if (n.isEditable) flags.add("editable")
                if (n.isFocused) flags.add("focused")
                if (n.isCheckable) flags.add(if (n.isChecked) "checked" else "unchecked")
                if (n.isScrollable) flags.add("scrollable")
                if (n.isSelected) flags.add("selected")
                if (!n.isEnabled) flags.add("disabled")
                sb.append('[').append(list.size).append("] ").append(kind(n))
                if (label.isNotEmpty()) sb.append(" \"").append(label).append('"')
                if (flags.isNotEmpty()) sb.append(" (").append(flags.joinToString(", ")).append(')')
                sb.append('\n')
                list.add(n)
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        nodes = list
        return Snap(sb.toString().ifBlank { "(nothing readable on this screen)" }, root.packageName?.toString() ?: "?", list.size, dm.widthPixels, dm.heightPixels)
    }

    /** A full-size screenshot (Android 11+), or null. Also used by Candy See so it needs no "share your screen" prompt. */
    suspend fun screenshotBitmap(): Bitmap? {
        if (Build.VERSION.SDK_INT < 30) return null
        return suspendCancellableCoroutine { cont ->
            try {
                takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val hb = result.hardwareBuffer
                        val bmp = try { Bitmap.wrapHardwareBuffer(hb, result.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false) } catch (_: Exception) { null }
                        try { hb.close() } catch (_: Exception) { }
                        if (cont.isActive) cont.resume(bmp)
                    }
                    override fun onFailure(errorCode: Int) { if (cont.isActive) cont.resume(null) }
                })
            } catch (_: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    private suspend fun screenshotJpeg(): Pair<String, Float>? {
        // the pill is a separate window, so it can be in the picture; that is fine
        val bmp = screenshotBitmap() ?: return null
        return withContext(Dispatchers.Default) {
            val max = 1000f
            val scale = minOf(1f, max / maxOf(bmp.width, bmp.height))
            val small = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
            val out = ByteArrayOutputStream()
            small.compress(Bitmap.CompressFormat.JPEG, 60, out)
            shotScale = 1f / scale
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP) to shotScale
        }
    }

    // ───────────── doing things ─────────────

    private suspend fun gesture(path: Path, ms: Long): Boolean = suspendCancellableCoroutine { cont ->
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, ms)).build()
        val ok = dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) { if (cont.isActive) cont.resume(true) }
            override fun onCancelled(d: GestureDescription?) { if (cont.isActive) cont.resume(false) }
        }, null)
        if (!ok && cont.isActive) cont.resume(false)
    }

    private suspend fun tap(x: Float, y: Float): Boolean = gesture(Path().apply { moveTo(x, y) }, 60)

    private suspend fun tapShot(x: Int?, y: Int?): Boolean = if (x == null || y == null) false else tap(x * shotScale, y * shotScale)

    private suspend fun clickNode(n: AccessibilityNodeInfo): Boolean {
        var x: AccessibilityNodeInfo? = n
        var d = 0
        while (x != null && d < 6) {
            if (x.isClickable && x.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            x = x.parent
            d++
        }
        val r = Rect()
        n.getBoundsInScreen(r)
        return tap(r.exactCenterX(), r.exactCenterY())
    }

    private fun typeInto(n: AccessibilityNodeInfo, text: String): Boolean {
        n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val b = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    }

    private suspend fun scroll(dir: String, target: AccessibilityNodeInfo?): Boolean {
        val node = target?.takeIf { it.isScrollable } ?: nodes.filter { it.isScrollable }.maxByOrNull { n -> Rect().also { n.getBoundsInScreen(it) }.let { it.width() * it.height() } }
        if (node != null && Build.VERSION.SDK_INT >= 23) {
            val a = when (dir) {
                "up" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP
                "left" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT
                "right" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT
                else -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN
            }
            if (node.performAction(a.id)) return true
        }
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        val p = Path()
        when (dir) {
            "up" -> { p.moveTo(w / 2, h * 0.3f); p.lineTo(w / 2, h * 0.7f) }
            "left" -> { p.moveTo(w * 0.3f, h / 2); p.lineTo(w * 0.7f, h / 2) }
            "right" -> { p.moveTo(w * 0.7f, h / 2); p.lineTo(w * 0.3f, h / 2) }
            else -> { p.moveTo(w / 2, h * 0.7f); p.lineTo(w / 2, h * 0.3f) }
        }
        return gesture(p, 300)
    }

    // ───────────── the pill and the cards (drawn by the service, so they show over every app) ─────────────

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun bg(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }

    private fun showPill(text: String) {
        if (pill != null) { setPill(text); return }
        val tv = TextView(this).apply { this.text = text; setTextColor(Color.WHITE); textSize = 14f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; maxWidth = dp(220) }
        val stop = TextView(this).apply {
            this.text = getString(R.string.agent_stop); setTextColor(Color.WHITE); textSize = 14f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = bg(0xFFE5384F.toInt(), 14); setPadding(dp(14), dp(6), dp(14), dp(6))
            setOnClickListener { stop(); Toast.makeText(this@CandyAgentService, R.string.agent_stopped, Toast.LENGTH_SHORT).show() }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = bg(0xEE2B1B20.toInt(), 24); setPadding(dp(16), dp(8), dp(8), dp(8))
            layoutDirection = if (Lang.code(this@CandyAgentService) == "ar") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            addView(tv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(12) })
            addView(stop)
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(48) }
        try { wm.addView(row, lp); pill = row; pillText = tv } catch (_: Exception) { }
    }

    private fun setPill(text: String) { pillText?.text = text }

    private fun hidePill() {
        pill?.let { try { wm.removeView(it) } catch (_: Exception) { } }
        pill = null
        pillText = null
    }

    private fun hideCard() {
        card?.let { try { wm.removeView(it) } catch (_: Exception) { } }
        card = null
    }

    /** A card in the middle of the screen. [input] adds a text field. [onResult] gets (accepted, typed text). */
    private fun showCard(message: String, input: Boolean, okText: String, noText: String?, onResult: (Boolean, String) -> Unit) {
        hideCard()
        val rtl = Lang.code(this) == "ar"
        val msg = TextView(this).apply { text = message; setTextColor(0xFF2B1B20.toInt()); textSize = 16f; setLineSpacing(0f, 1.15f); textDirection = View.TEXT_DIRECTION_FIRST_STRONG }
        val field = EditText(this).apply {
            hint = getString(R.string.agent_ask_hint); setTextColor(0xFF2B1B20.toInt()); setHintTextColor(0x802B1B20.toInt())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; maxLines = 4
        }
        fun button(t: String, fill: Int, fg: Int, click: () -> Unit) = TextView(this).apply {
            text = t; setTextColor(fg); textSize = 15f; typeface = android.graphics.Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            background = bg(fill, 14); setPadding(dp(18), dp(10), dp(18), dp(10)); setOnClickListener { click() }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = bg(0xFFFFFAE3.toInt(), 22); setPadding(dp(20), dp(20), dp(20), dp(16))
            layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            addView(msg)
            if (input) addView(field, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
            val row = LinearLayout(this@CandyAgentService).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
            if (noText != null) row.addView(button(noText, 0x00000000, 0xFF8A6F78.toInt()) { hideCard(); onResult(false, "") })
            row.addView(button(okText, 0xFFF7567C.toInt(), Color.WHITE) { val t = field.text.toString().trim(); hideCard(); onResult(true, t) })
            addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })
        }
        val width = (resources.displayMetrics.widthPixels * 0.88f).toInt()
        val lp = WindowManager.LayoutParams(
            width, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            if (input) WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER; if (input) softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE }
        try { wm.addView(root, lp); card = root } catch (_: Exception) { onResult(false, "") }
    }

    private suspend fun confirm(message: String): Boolean = suspendCancellableCoroutine { c ->
        showCard(message, false, getString(R.string.agent_do), getString(R.string.agent_skip)) { ok, _ -> if (c.isActive) c.resume(ok) }
        c.invokeOnCancellation { hideCard() }
    }

    private suspend fun ask(question: String): String? = suspendCancellableCoroutine { c ->
        showCard(question, true, getString(R.string.agent_send), getString(R.string.agent_skip)) { ok, t -> if (c.isActive) c.resume(if (ok && t.isNotBlank()) t else null) }
        c.invokeOnCancellation { hideCard() }
    }

    private suspend fun notify(message: String) = suspendCancellableCoroutine<Unit> { c ->
        showCard(message, false, getString(R.string.agent_ok), null) { _, _ -> if (c.isActive) c.resume(Unit) }
        c.invokeOnCancellation { hideCard() }
    }
}
