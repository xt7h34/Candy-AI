package com.candyai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.app.Activity
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.graphics.graphicsLayer
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Holds a screenshot that arrives from the assistant session after the voice panel is already open (the optional "attach the screen" button). */
object SeeHolder {
    @Volatile var shot: android.graphics.Bitmap? = null
    @Volatile var hasScreen: Boolean = false
}

/**
 * Candy See: the conversation behind the capsule. One class for Circle to Search, the voice panel and floating Candy.
 * Sends, follows the streaming reply, records the voice and, when [speakReplies] is on, reads the answer out loud.
 */
class SeeChat(private val ctx: Context, private val scope: CoroutineScope, var speakReplies: Boolean = false, private val convTitle: String? = null) {
    var convId by mutableStateOf<String?>(null)
    val turns = mutableStateListOf<AiMsg>()
    var reply by mutableStateOf("")
    var sending by mutableStateOf(false)
    var listening by mutableStateOf(false)
    var recording by mutableStateOf(false)
    var error by mutableStateOf("")
    var lastAnswer by mutableStateOf("")
    /** Pictures and files waiting to go with the next message (the "+" menu, a screenshot). */
    val pending = mutableStateListOf<Pending>()
    /** True while a screenshot is being taken: the panel hides itself so it is not in the picture. */
    var capturing by mutableStateOf(false)
    /** Phone actions Candy prepared in the last reply; the panel asks before doing them. */
    var actions by mutableStateOf<List<AgentAction>>(emptyList())
    /** Steps of a research that is running (shown as a card, like in the main chat). */
    var research by mutableStateOf<List<String>>(emptyList())
    private var jobId: String? = null
    private var drawn: List<AttRef> = emptyList()
    private var messageId = 0L
    private val recorder = VoiceRecorder(ctx)

    /** How loud it is right now while recording (0 to 32767). */
    val level: Int get() = recorder.amplitude

    val hasChat: Boolean get() = turns.isNotEmpty() || sending || reply.isNotEmpty()

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
            if (p.progress.isNotEmpty()) research = p.progress
            if (p.status != "running") {
                research = emptyList()
                val fin = p.final
                if (!fin.isNullOrBlank()) reply = fin
                if (p.atts.isNotEmpty()) drawn = p.atts
                if (p.actions.isNotEmpty()) actions = p.actions
                if (p.messageId > 0) messageId = p.messageId
                if (p.status == "error") error = ctx.getString(R.string.err_reply)
                break
            }
        }
    }

    /** Sends [text] (and [files], the circle or the screen). Returns at once; the reply streams into [reply]. */
    fun ask(text: String, files: List<Pending> = emptyList(), onReply: (Int) -> Unit = {}) {
        if (sending || (text.isBlank() && files.isEmpty())) return
        VoicePlayer.stop()
        sending = true
        error = ""
        research = emptyList()
        drawn = emptyList()
        messageId = 0L
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
                    if (convTitle != null) try { Api.renameConversation(it, convTitle) } catch (_: Exception) { }
                }
                val job = Api.sendMessage(cid, text, files)
                sent = true
                follow(job)
                if (reply.isNotEmpty() || drawn.isNotEmpty()) {
                    val done = reply
                    turns.add(AiMsg(-System.nanoTime(), "assistant", done, System.currentTimeMillis(), atts = drawn))
                    lastAnswer = done
                    reply = ""
                    onReply(done.length)
                    if (speakReplies && done.isNotBlank() && messageId > 0) speak(messageId)
                }
            } catch (e: Exception) {
                error = errorMessage(ctx, e)
                if (!sent && turns.isNotEmpty()) turns.removeAt(turns.lastIndex)
            } finally {
                sending = false
                jobId = null
            }
        }
    }

    private fun speak(n: Long) {
        scope.launch {
            try {
                val bytes = Api.voiceBytes(n)
                val f = File(ctx.cacheDir, "see_voice_$n.mp3")
                withContext(Dispatchers.IO) { f.writeBytes(bytes) }
                VoicePlayer.toggle("see#$n", f)
            } catch (_: Exception) {
                // a reply that cannot be read aloud (limit reached, voice off) is still on the screen
            }
        }
    }

    fun stop() {
        VoicePlayer.stop()
        val j = jobId ?: return
        scope.launch { try { Api.cancel(j) } catch (_: Exception) { } }
    }

    fun startRecording() {
        VoicePlayer.stop()
        if (recorder.start()) { recording = true; error = "" } else error = ctx.getString(R.string.voice_record_failed)
    }

    fun cancelRecording() {
        if (recording) { recorder.cancel(); recording = false }
    }

    /** Stops recording, turns it into text and hands the text to [onText]. */
    fun finishRecording(onText: (String) -> Unit) {
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
                if (t.isEmpty()) error = ctx.getString(R.string.ai_stt_empty) else onText(t)
            } catch (e: Exception) {
                error = errorMessage(ctx, e)
            } finally {
                listening = false
            }
        }
    }

    fun release() {
        recorder.cancel()
        VoicePlayer.stop()
    }
}

/**
 * The Candy See capsule: a short pill at the bottom of the screen ("+", Ask anything, translate, mic). It grows when you tap it,
 * and the answer grows out of the capsule itself (upward, up to a limit, then it scrolls). Used by Circle to Search, the voice panel,
 * floating Candy, the shortcut and the tile.
 *
 * [onSubmit] gets the text and whatever is attached (the host adds the screen or the circle when it has one).
 * [frozen] = the host already holds the screen (Circle to Search): there is no "screenshot" item and the translate button calls [onTranslateScreen].
 */
@Composable
fun SeeCapsule(
    chat: SeeChat,
    hint: String,
    onSubmit: (String, List<Pending>) -> Unit,
    onOpenFull: (String?) -> Unit,
    modifier: Modifier = Modifier,
    startListening: Boolean = false,
    frozen: Boolean = false,
    onTranslateScreen: () -> Unit = {},
    onClose: () -> Unit = {},
    onCircle: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var tall by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var camFile by remember { mutableStateOf<File?>(null) }
    var lastTouch by remember { mutableStateOf(System.currentTimeMillis()) }
    val focus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val screenH = LocalConfiguration.current.screenHeightDp
    val maxAnswer = (screenH * if (tall) 0.7f else 0.36f).dp
    val grown = focused || chat.hasChat || chat.listening || chat.pending.isNotEmpty() || chat.recording || input.isNotEmpty()

    fun send(text: String) {
        val files = chat.pending.toList()
        chat.pending.clear()
        input = ""
        onSubmit(text, files)
    }
    fun submit() {
        val t = input.trim()
        if (t.isEmpty() && chat.pending.isEmpty()) return
        send(t)
    }

    // ───── "+" : camera, gallery, text file, screenshot ─────
    fun full(): Boolean { if (chat.pending.size >= AttachKit.MAX_FILES) { chat.error = ctx.getString(R.string.attach_max); return true }; return false }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(AttachKit.MAX_FILES)) { uris ->
        scope.launch {
            for (u in uris) {
                if (chat.pending.size >= AttachKit.MAX_FILES) break
                val p = AttachKit.fromImage(ctx, u)
                if (p != null) chat.pending.add(p) else chat.error = ctx.getString(R.string.attach_fail)
            }
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = camFile
        camFile = null
        if (f != null) {
            if (ok) scope.launch {
                val p = if (chat.pending.size < AttachKit.MAX_FILES) AttachKit.fromImage(ctx, Uri.fromFile(f)) else null
                if (p != null) chat.pending.add(p) else chat.error = ctx.getString(R.string.attach_fail)
                f.delete()
            } else f.delete()
        }
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !full()) scope.launch {
            val p = AttachKit.fromDocument(ctx, uri)
            if (p != null) chat.pending.add(p) else chat.error = ctx.getString(R.string.attach_fail)
        }
    }
    fun openCamera() {
        if (full()) return
        try {
            val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
            val f = File.createTempFile("cam_", ".jpg", dir)
            camFile = f
            camera.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", f))
        } catch (e: Exception) {
            chat.error = ctx.getString(R.string.attach_fail)
        }
    }

    // ───── a screenshot of what is behind the panel ─────
    var afterShot by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun gotShot(bmp: Bitmap?) {
        chat.capturing = false
        scope.launch {
            val p = bmp?.let { AttachKit.fromBitmap(it, "screen.jpg") }
            if (p == null) { chat.error = ctx.getString(R.string.see_no_screen); afterShot = null; return@launch }
            chat.error = ""
            chat.pending.add(p)
            val next = afterShot
            afterShot = null
            next?.invoke()
        }
    }
    val projection = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data
        if (r.resultCode == Activity.RESULT_OK && data != null) {
            chat.capturing = true
            CaptureService.start(ctx.applicationContext, r.resultCode, data) { gotShot(it) }
        } else {
            afterShot = null
        }
    }
    /** The older ways: the assistant session hands over a screenshot, or the system asks once ("share your screen"). */
    fun legacyShot(translate: Boolean) {
        // Candy's own assistant session hands over a real screenshot, no permission prompt
        if (EdgeInteractionService.show("shot", if (translate) "translate" else null)) {
            afterShot = null
            onClose()
            return
        }
        try {
            projection.launch(ctx.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
        } catch (e: Exception) {
            afterShot = null
            chat.error = ctx.getString(R.string.see_no_screen)
        }
    }

    /** Screenshot into the attachments. The one the assistant already handed over is used when there is one. */
    fun takeScreen(then: (() -> Unit)? = null, translate: Boolean = false) {
        if (full()) return
        val held = SeeHolder.shot
        afterShot = then
        if (held != null) { gotShot(held); return }
        // best choice: Candy's accessibility service takes it, no prompt at all (Android 11+)
        val svc = CandyAgentService.inst
        if (svc != null && android.os.Build.VERSION.SDK_INT >= 30) {
            chat.capturing = true
            scope.launch {
                delay(350)   // the panel hides itself first
                val b = svc.screenshotBitmap()
                if (b != null) gotShot(b) else { chat.capturing = false; legacyShot(translate) }
            }
            return
        }
        legacyShot(translate)
    }

    // ───── translate: text if there is some, else the screen ─────
    fun translate() {
        val t = input.trim()
        when {
            t.isNotEmpty() -> send(ctx.getString(R.string.see_translate_text) + "\n" + t)
            frozen -> onTranslateScreen()
            chat.pending.any { it.kind == "image" } -> send(ctx.getString(R.string.cts_p_translate))
            else -> takeScreen({ send(ctx.getString(R.string.cts_p_translate)) }, translate = true)
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) chat.startRecording() else chat.error = ctx.getString(R.string.voice_no_permission)
    }
    fun onMic() {
        if (chat.recording) chat.finishRecording { send(it) }
        else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) chat.startRecording()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(Unit) {
        if (startListening) {
            delay(400)
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) chat.startRecording()
            else micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    LaunchedEffect(chat.reply.length, chat.turns.size) { scroll.scrollTo(scroll.maxValue) }
    DisposableEffect(Unit) { onDispose { chat.release() } }

    LaunchedEffect(input, focused, menu, chat.sending, chat.listening, chat.recording, chat.reply.length, chat.turns.size, chat.pending.size) {
        lastTouch = System.currentTimeMillis()
    }
    val none = remember { MutableInteractionSource() }
    Column(
        modifier
            .graphicsLayer { alpha = if (chat.capturing) 0f else 1f }   // not in the screenshot
            .fillMaxWidth()
            .navigationBarsPadding().imePadding()
            .padding(horizontal = if (grown) 12.dp else 28.dp).padding(bottom = 12.dp)
            .clickable(interactionSource = none, indication = null) { },   // touches on the capsule never fall through
    ) {
        // Candy peeks over the top edge of the capsule, as if it were a table (hidden when the answer is expanded or the screen is short)
        if (!tall && screenH >= 640) {
            CandyBarPeek(
                busy = chat.sending,
                listening = chat.listening || chat.recording,
                activeStamp = lastTouch,
                onPoke = { lastTouch = System.currentTimeMillis() },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        Surface(
            shape = RoundedCornerShape(if (grown) 26.dp else 30.dp),
            color = cs.surfaceContainerHigh,
            shadowElevation = 10.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (chat.hasChat || chat.listening) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                        AiAvatar(24.dp)
                        Text(
                            stringResource(R.string.ai_name),
                            style = MaterialTheme.typography.titleSmall,
                            color = cs.onSurface,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                        if (chat.convId != null) {
                            TextButton(onClick = { onOpenFull(chat.convId) }) { Text(stringResource(R.string.cts_open_chat), color = cs.primary, fontSize = 13.sp) }
                        }
                        Icon(
                            if (tall) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
                            contentDescription = null,
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(32.dp).clip(CircleShape).clickable { tall = !tall }.padding(4.dp),
                        )
                    }
                    Column(Modifier.heightIn(max = maxAnswer).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (t in chat.turns) AiBubble(t)
                        if (chat.reply.isNotEmpty()) {
                            AiBubble(AiMsg(-2L, "assistant", chat.reply, System.currentTimeMillis()))
                        } else if (chat.sending || chat.listening) {
                            AiTypingBubble(stringResource(if (chat.listening) R.string.ai_listening else R.string.ai_thinking))
                        }
                        if (chat.research.isNotEmpty() && chat.sending) ResearchCard(chat.research)
                    }
                }
                if (chat.pending.isNotEmpty()) PendingStrip(chat.pending) { chat.pending.remove(it) }
                if (chat.recording) Muted(stringResource(R.string.edge_recording))
                if (chat.error.isNotBlank()) ErrorText(chat.error)

                val buttonFirst = (Store.sendSide == "left") != (LocalLayoutDirection.current == LayoutDirection.Rtl)
                val actionButton: @Composable () -> Unit = {
                    val showMic = input.isBlank() && !chat.sending && chat.pending.isEmpty()
                    val active = chat.sending || chat.recording || (!chat.listening && (showMic || input.isNotBlank() || chat.pending.isNotEmpty()))
                    Surface(
                        shape = CircleShape,
                        color = if (active) cs.primary else cs.surfaceContainerHighest,
                        modifier = Modifier.size(42.dp).clip(CircleShape).clickable(enabled = active) {
                            if (chat.sending) chat.stop() else if (chat.recording || showMic) onMic() else submit()
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val tint = if (active) cs.onPrimary else cs.onSurfaceVariant
                            when {
                                chat.sending || chat.recording -> Icon(Icons.Rounded.Stop, stringResource(R.string.stop), tint = tint)
                                showMic -> Icon(painterResource(R.drawable.ic_wave), null, tint = tint, modifier = Modifier.size(22.dp))
                                else -> Icon(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.send), tint = tint)
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (buttonFirst) actionButton()
                    Box {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.attach),
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(40.dp).clip(CircleShape).clickable(enabled = !chat.sending) { menu = true }.padding(8.dp),
                        )
                        AttachMenu(
                            expanded = menu,
                            onDismiss = { menu = false },
                            onCamera = { openCamera() },
                            onGallery = { gallery.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            onFile = { files.launch(arrayOf("*/*")) },
                            onScreen = if (frozen) null else ({ takeScreen() }),
                            onCircle = if (frozen) null else onCircle,
                        )
                    }
                    Box(Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                        if (input.isEmpty()) Text(hint, color = cs.onSurfaceVariant, fontSize = 15.sp, maxLines = 1)
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it.take(1500) },
                            textStyle = LocalTextStyle.current.copy(color = cs.onSurface, fontSize = 15.sp),
                            cursorBrush = SolidColor(cs.primary),
                            maxLines = if (focused) 4 else 1,
                            modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged {
                                focused = it.isFocused
                                if (it.isFocused) chat.cancelRecording()
                            },
                        )
                    }
                    Icon(
                        Icons.Rounded.Translate,
                        contentDescription = stringResource(R.string.cts_translate),
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(40.dp).clip(CircleShape).clickable(enabled = !chat.sending) { translate() }.padding(8.dp),
                    )
                    if (!buttonFirst) actionButton()
                }
            }
        }
    }
}
