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

/** The conversation inside the sheet: sends a message (with the circled picture), follows the reply while it streams. */
private class CtsChat(private val ctx: Context, private val scope: CoroutineScope) {
    var convId by mutableStateOf<String?>(null)
    val turns = mutableStateListOf<AiMsg>()
    var reply by mutableStateOf("")
    var sending by mutableStateOf(false)
    var error by mutableStateOf("")
    private var jobId: String? = null
    private var drawn: List<AttRef> = emptyList()

    private suspend fun follow(job: String) {
        jobId = job
        var since = 0
        var fails = 0
        while (true) {
            val p = try {
                Api.poll(job, since)
            } catch (e: IOException) {
                fails++
                if (fails > 5) throw e
                delay(1500)
                continue
            }
            fails = 0
            val piece = if (p.full) p.text else p.delta
            if (piece.isNotEmpty()) reply = if (p.full) piece else reply + piece
            since = p.next
            if (p.status != "running") {
                val fin = p.final
                if (!fin.isNullOrBlank()) reply = fin
                if (p.atts.isNotEmpty()) drawn = p.atts
                if (p.status == "error") error = ctx.getString(R.string.err_reply)
                break
            }
        }
    }

    fun ask(text: String, files: List<Pending> = emptyList()) {
        if (sending || (text.isBlank() && files.isEmpty())) return
        sending = true
        error = ""
        drawn = emptyList()
        turns.add(
            AiMsg(
                -System.nanoTime(), "user", text, System.currentTimeMillis(),
                atts = files.mapIndexed { i, p -> AttRef(-(i + 1).toLong(), p.kind, p.name, p.thumb) },
            ),
        )
        reply = ""
        scope.launch {
            var sent = false
            try {
                val cid = convId ?: Api.createConversation().also {
                    convId = it
                    // it shows up in the normal list of conversations, named after where it came from
                    try { Api.renameConversation(it, ctx.getString(R.string.cts_conv_title)) } catch (_: Exception) { }
                }
                val job = Api.sendMessage(cid, text, files)
                sent = true
                follow(job)
                if (reply.isNotEmpty() || drawn.isNotEmpty()) {
                    turns.add(AiMsg(-System.nanoTime(), "assistant", reply, System.currentTimeMillis(), atts = drawn))
                }
                reply = ""
            } catch (e: Exception) {
                error = errorMessage(ctx, e)
                if (!sent && turns.isNotEmpty()) turns.removeAt(turns.lastIndex)
            } finally {
                sending = false
                jobId = null
            }
        }
    }

    fun stop() {
        val j = jobId ?: return
        scope.launch { try { Api.cancel(j) } catch (_: Exception) { } }
    }
}

@Composable
fun CtsScreen(shot: Bitmap, onClose: () -> Unit, onOpenFull: (String?) -> Unit) {
    val ctx = LocalContext.current
    val c = LocalCandy.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val chat = remember { CtsChat(ctx, scope) }
    val img = remember(shot) { shot.asImageBitmap() }

    val stroke = remember { ArrayList<Offset>() }                   // what the finger is drawing (not observable: redraws are driven by [tick])
    val livePath = remember { Path() }
    var tick by remember { mutableIntStateOf(0) }
    var drawingNow by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<Path?>(null) }       // the closed shape once the finger is up
    var box by remember { mutableStateOf(IntSize.Zero) }
    var region by remember { mutableStateOf<Pending?>(null) }       // the circled part, packed to send
    var attached by remember { mutableStateOf(false) }              // is the current picture already in the conversation?
    var input by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    val recorder = remember { VoiceRecorder(ctx) }
    val scroll = rememberScrollState()

    val hasChat = chat.turns.isNotEmpty() || chat.sending
    val sheetH = remember { Animatable(0f) }

    // ───── asking ─────
    fun fail() { chat.error = ctx.getString(R.string.attach_fail) }

    /** Sends text. The picture (the circle, or the whole screen if nothing was circled) goes along only the first time. */
    fun askWith(text: String) {
        if (chat.sending) return
        scope.launch {
            val files = if (attached) emptyList() else listOfNotNull(region ?: AttachKit.fromBitmap(shot, "screen.jpg"))
            if (!attached && files.isEmpty()) { fail(); return@launch }
            attached = true
            input = ""
            chat.ask(text, files)
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
            chat.ask(ctx.getString(R.string.cts_default_prompt), listOf(packed))
        }
    }

    // ───── voice, same as the Edge panel ─────
    fun finishRecording() {
        val done = recorder.finish()
        recording = false
        if (done == null || done.second < 800L) {
            done?.first?.delete()
            chat.error = ctx.getString(R.string.voice_too_short)
            return
        }
        scope.launch {
            listening = true
            chat.error = ""
            try {
                val bytes = withContext(Dispatchers.IO) { done.first.readBytes() }
                done.first.delete()
                val t = Api.transcribe(Base64.encodeToString(bytes, Base64.NO_WRAP)).trim()
                listening = false
                if (t.isEmpty()) chat.error = ctx.getString(R.string.ai_stt_empty) else askWith(t)
            } catch (e: Exception) {
                chat.error = errorMessage(ctx, e)
            } finally {
                listening = false
            }
        }
    }

    fun startRecording() {
        if (recorder.start()) { recording = true; chat.error = "" } else chat.error = ctx.getString(R.string.voice_record_failed)
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else chat.error = ctx.getString(R.string.voice_no_permission)
    }

    fun onMic() {
        if (recording) finishRecording()
        else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    DisposableEffect(Unit) { onDispose { recorder.cancel() } }
    LaunchedEffect(chat.reply.length, chat.turns.size) { scroll.scrollTo(scroll.maxValue) }
    // the sheet opens to a comfortable height the moment there is something to read
    LaunchedEffect(hasChat) {
        if (hasChat && box.height > 0 && sheetH.value == 0f) {
            sheetH.snapTo(box.height * 0.26f)
            sheetH.animateTo(box.height * 0.42f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow))
        }
    }
    BackHandler {
        if (sheetH.value > box.height * 0.5f) scope.launch { sheetH.animateTo(box.height * 0.42f) } else onClose()
    }

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
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp).size(40.dp).clip(CircleShape).clickable { onClose() },
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, stringResource(R.string.cts_close), tint = Color.White) }
        }

        // hint, until the first circle
        if (selection == null && !drawingNow && !hasChat) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 18.dp).padding(horizontal = 64.dp),
            ) {
                Text(stringResource(R.string.cts_hint), color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }

        // the sheet: drag the handle to make it taller or shorter
        val dragState = rememberDraggableState { d ->
            scope.launch { sheetH.snapTo((sheetH.value - d).coerceIn(box.height * 0.22f, box.height * 0.92f)) }
        }
        val interactionNone = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .layout { measurable, constraints ->
                    // dragging changes the height every frame: read it here, in layout, so nothing recomposes
                    if (!hasChat) {
                        val p = measurable.measure(constraints)
                        layout(p.width, p.height) { p.place(0, 0) }
                    } else {
                        val want = if (sheetH.value > 0f) sheetH.value else constraints.maxHeight * 0.26f
                        val h = want.toInt().coerceIn(0, constraints.maxHeight)
                        val p = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                        layout(p.width, p.height) { p.place(0, 0) }
                    }
                }
                .clickable(interactionSource = interactionNone, indication = null) { }   // touches on the sheet never reach the drawing surface
                .background(cs.background, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .navigationBarsPadding().imePadding()
                .padding(horizontal = 14.dp).padding(bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // handle
            Box(
                Modifier.fillMaxWidth().height(28.dp).draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { v ->
                        val h = box.height.toFloat()
                        val stops = listOf(0.28f * h, 0.5f * h, 0.92f * h)
                        val projected = sheetH.value - v * 0.15f
                        val target = stops.minByOrNull { abs(it - projected) } ?: stops[1]
                        sheetH.animateTo(target, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow))
                    },
                ),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(cs.onSurfaceVariant.copy(alpha = 0.4f)))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                AiAvatar(30.dp)
                Text(
                    stringResource(R.string.ai_name),
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onBackground,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                )
                if (chat.convId != null) {
                    TextButton(onClick = { onOpenFull(chat.convId) }) { Text(stringResource(R.string.cts_open_chat), color = cs.primary) }
                }
            }

            if (hasChat) {
                Column(Modifier.weight(1f).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (t in chat.turns) AiBubble(t)
                    if (chat.reply.isNotEmpty()) {
                        AiBubble(AiMsg(-2L, "assistant", chat.reply, System.currentTimeMillis()))
                    } else if (chat.sending || listening) {
                        AiTypingBubble(stringResource(if (listening) R.string.ai_listening else R.string.ai_thinking))
                    }
                }
            }
            if (recording) Muted(stringResource(R.string.edge_recording))
            if (chat.error.isNotBlank()) ErrorText(chat.error)

            // quick actions on what is selected (or the whole screen)
            if (!chat.sending) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CtsChip(stringResource(R.string.cts_explain)) { askWith(ctx.getString(R.string.cts_p_explain)) }
                    CtsChip(stringResource(R.string.cts_translate)) { askWith(ctx.getString(R.string.cts_p_translate)) }
                    CtsChip(stringResource(R.string.cts_summarize)) { askWith(ctx.getString(R.string.cts_p_summarize)) }
                }
            }

            val buttonFirst = (Store.sendSide == "left") != (LocalLayoutDirection.current == LayoutDirection.Rtl)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val inputBox: @Composable RowScope.() -> Unit = {
                    Surface(shape = MaterialTheme.shapes.extraLarge, color = cs.surfaceContainerHigh, modifier = Modifier.weight(1f)) {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            if (input.isEmpty()) Text(stringResource(R.string.cts_input_hint), color = cs.onSurfaceVariant)
                            BasicTextField(
                                value = input,
                                onValueChange = { input = it.take(1500) },
                                textStyle = LocalTextStyle.current.copy(color = cs.onSurface),
                                cursorBrush = SolidColor(cs.primary),
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                val actionButton: @Composable () -> Unit = {
                    val showMic = input.isBlank() && !chat.sending
                    val active = chat.sending || recording || (!listening && (showMic || input.isNotBlank()))
                    Surface(
                        shape = CircleShape,
                        color = if (active) cs.primary else cs.surfaceContainerHigh,
                        modifier = Modifier.size(48.dp).clip(CircleShape).clickable(enabled = active) {
                            if (chat.sending) chat.stop() else if (recording || showMic) onMic() else askWith(input.trim())
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val tint = if (active) cs.onPrimary else cs.onSurfaceVariant
                            when {
                                chat.sending || recording -> Icon(Icons.Rounded.Stop, stringResource(R.string.stop), tint = tint)
                                showMic -> Icon(painterResource(R.drawable.ic_wave), null, tint = tint, modifier = Modifier.size(24.dp))
                                else -> Icon(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.send), tint = tint)
                            }
                        }
                    }
                }
                if (buttonFirst) { actionButton(); inputBox() } else { inputBox(); actionButton() }
            }
        }
    }
}

@Composable
private fun CtsChip(label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(50),
        color = cs.surfaceContainerHigh,
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick),
    ) {
        Text(label, color = cs.onSurface, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}
