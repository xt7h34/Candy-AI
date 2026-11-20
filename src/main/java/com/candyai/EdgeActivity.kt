package com.candyai

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject


/** Candy Edge: a small ask-Candy panel that opens over whatever app you are in (shortcut, quick-settings tile, or the assistant gesture). */
class EdgeActivity : ComponentActivity() {
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
        setContent {
            CandyTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    EdgePanel(
                        onClose = { finish() },
                        onOpenFull = { id ->
                            val open = Intent(this@EdgeActivity, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            if (id != null) open.putExtra("open_conv", id)
                            startActivity(open)
                            finish()
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun EdgePanel(onClose: () -> Unit, onOpenFull: (String?) -> Unit, onReply: (Int) -> Unit = {}) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    var convId by remember { mutableStateOf<String?>(null) }
    val turns = remember { mutableStateListOf<AiMsg>() }   // what was asked and answered in this panel, oldest first
    var reply by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var jobId by remember { mutableStateOf<String?>(null) }
    val sttOn = true   // Candy's voice is always on, like Elite
    val recorder = remember { VoiceRecorder(ctx) }
    val focus = remember { FocusRequester() }
    val scroll = rememberScrollState()

    // writes the reply into the panel while the server produces it
    suspend fun follow(job: String) {
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
                if (p.status == "error") error = ctx.getString(R.string.err_reply)
                break
            }
        }
    }

    fun ask(text: String) {
        if (text.isEmpty() || sending) return
        sending = true
        error = ""
        input = ""
        turns.add(AiMsg(-System.nanoTime(), "user", text, System.currentTimeMillis()))
        reply = ""
        scope.launch {
            var sent = false
            try {
                val cid = convId ?: Api.createConversation().also { convId = it }
                val job = Api.sendMessage(cid, text)
                sent = true
                follow(job)
                if (reply.isNotEmpty()) {
                    val done = reply
                    turns.add(AiMsg(-System.nanoTime(), "assistant", done, System.currentTimeMillis()))
                    reply = ""
                    onReply(done.length)
                }
            } catch (e: Exception) {
                error = errorMessage(ctx, e)
                if (!sent) { input = text; if (turns.isNotEmpty()) turns.removeAt(turns.lastIndex) }
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

    fun finishRecording() {
        val done = recorder.finish()
        recording = false
        if (done == null || done.second < 800L) {
            done?.first?.delete()
            error = ctx.getString(R.string.voice_too_short)
            return
        }
        scope.launch {
            listening = true
            error = ""
            try {
                val bytes = withContext(Dispatchers.IO) { done.first.readBytes() }
                done.first.delete()
                val t = Api.transcribe(Base64.encodeToString(bytes, Base64.NO_WRAP)).trim()
                listening = false
                if (t.isEmpty()) error = ctx.getString(R.string.ai_stt_empty) else ask(t)
            } catch (e: Exception) {
                error = errorMessage(ctx, e)
            } finally {
                listening = false
            }
        }
    }

    fun startRecording() {
        if (recorder.start()) {
            recording = true
            error = ""
        } else {
            error = ctx.getString(R.string.voice_record_failed)
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else error = ctx.getString(R.string.voice_no_permission)
    }

    fun onMic() {
        if (recording) {
            finishRecording()
        } else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) {
        delay(200)
        try { focus.requestFocus() } catch (_: Exception) { }
    }
    LaunchedEffect(reply.length, turns.size) { scroll.scrollTo(scroll.maxValue) }
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }

    val noRipple = remember { MutableInteractionSource() }
    val noRipple2 = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
            .clickable(interactionSource = noRipple, indication = null) { onClose() },
    ) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clickable(interactionSource = noRipple2, indication = null) { }   // taps on the panel do not close it
                .background(cs.background, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .navigationBarsPadding().imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AiAvatar(36.dp)
                Text(
                    stringResource(R.string.ai_name),
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onBackground,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                )
                TextButton(onClick = { onOpenFull(convId) }) {
                    Text(stringResource(R.string.edge_open_full), color = cs.primary)
                }
            }

            if (turns.isNotEmpty() || reply.isNotEmpty() || sending || listening) {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (t in turns) AiBubble(t)
                    if (reply.isNotEmpty()) {
                        AiBubble(AiMsg(-2L, "assistant", reply, System.currentTimeMillis()))
                    } else if (sending || listening) {
                        AiTypingBubble(stringResource(if (listening) R.string.ai_listening else R.string.ai_thinking))
                    }
                }
            }
            if (recording) Muted(stringResource(R.string.edge_recording))
            if (error.isNotBlank()) ErrorText(error)

            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = cs.surfaceContainerHigh, modifier = Modifier.weight(1f)) {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        if (input.isEmpty()) Text(stringResource(R.string.ai_hint), color = cs.onSurfaceVariant)
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it.take(1500) },
                            textStyle = LocalTextStyle.current.copy(color = cs.onSurface),
                            cursorBrush = SolidColor(cs.primary),
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                }
                val showMic = sttOn && input.isBlank() && !sending
                val active = sending || recording || (!listening && (showMic || input.isNotBlank()))
                Surface(
                    shape = CircleShape,
                    color = if (active) cs.primary else cs.surfaceContainerHigh,
                    modifier = Modifier.size(48.dp).clip(CircleShape).clickable(enabled = active) {
                        if (sending) stop() else if (recording || showMic) onMic() else ask(input.trim())
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val tint = if (active) cs.onPrimary else cs.onSurfaceVariant
                        when {
                            sending || recording -> Icon(Icons.Rounded.Stop, contentDescription = stringResource(R.string.stop), tint = tint)
                            showMic -> Icon(painterResource(R.drawable.ic_wave), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                            else -> Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = stringResource(R.string.send), tint = tint)
                        }
                    }
                }
            }
        }
    }
}
