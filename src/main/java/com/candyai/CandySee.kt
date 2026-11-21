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
    private var jobId: String? = null
    private var drawn: List<AttRef> = emptyList()
    private var messageId = 0L
    private val recorder = VoiceRecorder(ctx)

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
            if (p.status != "running") {
                val fin = p.final
                if (!fin.isNullOrBlank()) reply = fin
                if (p.atts.isNotEmpty()) drawn = p.atts
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
 * The Candy See capsule: a small rounded bar at the bottom of the screen. The answer grows out of the capsule itself
 * (upward, up to a limit, then it scrolls). Used by Circle to Search, the voice panel and floating Candy.
 *
 * [onSubmit] gets the text to send (the host decides what travels with it); [onTranslate] is the translate button.
 * [onAttachScreen] shows the optional "attach the screen" button (voice panel), [screenAttached] says it was pressed.
 */
@Composable
fun SeeCapsule(
    chat: SeeChat,
    hint: String,
    onSubmit: (String) -> Unit,
    onTranslate: (input: String) -> Unit,
    onOpenFull: (String?) -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    startListening: Boolean = false,
    onAttachScreen: (() -> Unit)? = null,
    screenAttached: Boolean = false,
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var input by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val screenH = LocalConfiguration.current.screenHeightDp
    val maxAnswer = (screenH * if (expanded) 0.7f else 0.36f).dp

    fun submit() {
        val t = input.trim()
        if (t.isEmpty() && !screenAttached) return
        input = ""
        onSubmit(t)
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) chat.startRecording() else chat.error = ctx.getString(R.string.voice_no_permission)
    }
    fun onMic() {
        if (chat.recording) chat.finishRecording { onSubmit(it) }
        else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) chat.startRecording()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(Unit) {
        if (startListening) {
            delay(250)
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) chat.startRecording()
            else micPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else if (autoFocus) {
            delay(200)
            try { focus.requestFocus() } catch (_: Exception) { }
        }
    }
    LaunchedEffect(chat.reply.length, chat.turns.size) { scroll.scrollTo(scroll.maxValue) }
    DisposableEffect(Unit) { onDispose { chat.release() } }

    val none = remember { MutableInteractionSource() }
    Column(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding().imePadding()
            .padding(horizontal = 12.dp).padding(bottom = 12.dp)
            .clickable(interactionSource = none, indication = null) { },   // touches on the capsule never fall through
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = cs.surfaceContainerHigh,
            shadowElevation = 10.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (chat.hasChat || chat.listening) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                            if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
                            contentDescription = null,
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(32.dp).clip(CircleShape).clickable { expanded = !expanded }.padding(4.dp),
                        )
                    }
                    Column(Modifier.heightIn(max = maxAnswer).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (t in chat.turns) AiBubble(t)
                        if (chat.reply.isNotEmpty()) {
                            AiBubble(AiMsg(-2L, "assistant", chat.reply, System.currentTimeMillis()))
                        } else if (chat.sending || chat.listening) {
                            AiTypingBubble(stringResource(if (chat.listening) R.string.ai_listening else R.string.ai_thinking))
                        }
                    }
                }
                if (chat.recording) Muted(stringResource(R.string.edge_recording))
                if (chat.error.isNotBlank()) ErrorText(chat.error)

                val buttonFirst = (Store.sendSide == "left") != (LocalLayoutDirection.current == LayoutDirection.Rtl)
                val actionButton: @Composable () -> Unit = {
                    val showMic = input.isBlank() && !chat.sending && !screenAttached
                    val active = chat.sending || chat.recording || (!chat.listening && (showMic || input.isNotBlank() || screenAttached))
                    Surface(
                        shape = CircleShape,
                        color = if (active) cs.primary else cs.surfaceContainerHighest,
                        modifier = Modifier.size(44.dp).clip(CircleShape).clickable(enabled = active) {
                            if (chat.sending) chat.stop() else if (chat.recording || showMic) onMic() else submit()
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val tint = if (active) cs.onPrimary else cs.onSurfaceVariant
                            when {
                                chat.sending || chat.recording -> Icon(Icons.Rounded.Stop, stringResource(R.string.stop), tint = tint)
                                showMic -> Icon(painterResource(R.drawable.ic_wave), null, tint = tint, modifier = Modifier.size(24.dp))
                                else -> Icon(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.send), tint = tint)
                            }
                        }
                    }
                }
                val field: @Composable (Modifier) -> Unit = { m ->
                    Box(m.padding(horizontal = 10.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                        if (input.isEmpty()) Text(hint, color = cs.onSurfaceVariant, fontSize = 15.sp)
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it.take(1500) },
                            textStyle = LocalTextStyle.current.copy(color = cs.onSurface, fontSize = 15.sp),
                            cursorBrush = SolidColor(cs.primary),
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { if (it.isFocused) chat.cancelRecording() },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (buttonFirst) actionButton()
                    if (onAttachScreen != null) {
                        Icon(
                            Icons.Rounded.Fullscreen,
                            contentDescription = stringResource(R.string.see_attach_screen),
                            tint = if (screenAttached) cs.primary else cs.onSurfaceVariant,
                            modifier = Modifier.size(40.dp).clip(CircleShape).clickable { onAttachScreen() }.padding(8.dp),
                        )
                    }
                    field(Modifier.weight(1f))
                    Icon(
                        Icons.Rounded.Translate,
                        contentDescription = stringResource(R.string.cts_translate),
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(40.dp).clip(CircleShape).clickable { val t = input.trim(); input = ""; onTranslate(t) }.padding(8.dp),
                    )
                    if (!buttonFirst) actionButton()
                }
            }
        }
    }
}
