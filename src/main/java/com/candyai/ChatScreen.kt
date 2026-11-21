package com.candyai

import androidx.compose.foundation.layout.statusBarsPadding
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.provider.Settings
import android.text.format.DateUtils
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Menu
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
import androidx.compose.ui.res.stringArrayResource
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
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject


/** The conversation with Candy: she is the wallpaper, replies are written word by word, and every reply has its action row. */
@Composable
fun ChatScreen(
    startId: String?,
    menuOnLeft: Boolean,
    sendOnLeft: Boolean,
    onMenu: () -> Unit,
    onConversation: (String) -> Unit,
    onOpenMemory: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    // A new chat only becomes a real conversation on the first message, so empty chats never pile up.
    var convId by remember { mutableStateOf(startId) }
    // a different friendly line every time a new chat opens
    val greetings = stringArrayResource(R.array.ai_greetings)
    val greeting = remember { greetings.random() }
    val msgs = remember { mutableStateListOf<AiMsg>() }
    var loading by remember { mutableStateOf(startId != null) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var shareFor by remember { mutableStateOf<AiMsg?>(null) }
    var jobId by remember { mutableStateOf<String?>(null) }
    var talkTick by remember { mutableStateOf(0) }
    var talkMs by remember { mutableStateOf(0L) }
    var lastActive by remember { mutableStateOf(System.currentTimeMillis()) }
    var voice by remember { mutableStateOf(VoiceStatus(false, 0, 0, false)) }
    var voiceBusy by remember { mutableStateOf(-1L) }
    var listening by remember { mutableStateOf(false) }
    val recorder = remember { VoiceRecorder(ctx) }
    var recording by remember { mutableStateOf(false) }
    var recPaused by remember { mutableStateOf(false) }
    var limitReached by remember { mutableStateOf(false) }
    var recElapsed by remember { mutableStateOf(0L) }
    // what is waiting to be sent with the next message (pictures and text files), and the camera's target file
    val pending = remember { mutableStateListOf<Pending>() }
    var attachMenu by remember { mutableStateOf(false) }
    var camFile by remember { mutableStateOf<File?>(null) }
    val amps = remember { mutableStateListOf<Float>() }
    val listState = rememberLazyListState()
    val reduceMotion = remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val animated = !reduceMotion

    fun refreshVoice() {
        scope.launch { try { voice = Api.voiceStatus() } catch (_: Exception) { } }
    }

    fun reload() {
        val id = convId
        if (id == null) { loading = false; return }
        scope.launch {
            try {
                val list = Api.messages(id)
                msgs.clear()
                msgs.addAll(list)
                error = ""
            } catch (e: Exception) {
                error = errorMessage(ctx, e)
            }
            loading = false
        }
    }

    // Follows one reply while the server writes it: the bubble grows word by word, and at the end it is replaced by the final text.
    suspend fun follow(job: String) {
        jobId = job
        var idx = -1
        var since = 0
        var fails = 0
        var needReload = false
        while (true) {
            val p = try {
                Api.poll(job, since)
            } catch (e: IOException) {
                // a short network hiccup: try again before giving up
                fails++
                if (fails > 5) throw e
                delay(1500)
                continue
            }
            fails = 0
            val piece = if (p.full) p.text else p.delta
            if (piece.isNotEmpty()) {
                if (idx < 0) {
                    msgs.add(AiMsg(-System.nanoTime(), "assistant", piece, System.currentTimeMillis()))
                    idx = msgs.lastIndex
                    talkMs = 90_000L   // her mouth moves while the words come
                    talkTick++
                } else if (p.full) {
                    msgs[idx] = msgs[idx].withText(piece)
                } else {
                    msgs[idx] = msgs[idx].withText(msgs[idx].text + piece)
                }
            }
            since = p.next
            if (p.status != "running") {
                val fin = p.final
                if (idx >= 0) {
                    val old = msgs[idx]
                    val text = fin ?: old.text
                    if (text.isBlank() && p.atts.isEmpty()) {
                        msgs.removeAt(idx)
                    } else {
                        msgs[idx] = AiMsg(
                            if (p.messageId > 0) p.messageId else old.n, "assistant", text,
                            System.currentTimeMillis(), p.memAdded, p.memRemoved, 0, p.atts,
                        )
                    }
                    if (p.full) needReload = true
                } else if (fin.isNullOrBlank()) {
                    needReload = true   // nothing arrived: show what the server kept
                }
                if (p.status == "error") error = ctx.getString(R.string.err_reply)
                break
            }
        }
        talkMs = 0L
        talkTick++
        jobId = null
        if (needReload) reload()
    }

    // plays Candy's reply n out loud. The server keeps what it already voiced, so playing a reply again costs nothing.
    fun speak(n: Long) {
        if (n <= 0 || voiceBusy == n) return
        if (VoicePlayer.playingKey == "ai#$n") {
            VoicePlayer.stop()
            return
        }
        scope.launch {
            voiceBusy = n
            try {
                val bytes = Api.voiceBytes(n)
                val f = File(ctx.cacheDir, "ai_voice_$n.mp3")
                withContext(Dispatchers.IO) { f.writeBytes(bytes) }
                VoicePlayer.toggle("ai#$n", f)
                if (VoicePlayer.playingKey == "ai#$n") {
                    talkMs = VoicePlayer.durationMs.toLong() + 300L
                    talkTick++
                }
                refreshVoice()
            } catch (e: Exception) {
                error = errorMessage(ctx, e)
            } finally {
                voiceBusy = -1L
            }
        }
    }

    // sends one message; with speakReply Candy's answer is read out loud as soon as it is complete (messages that came from the mic)
    fun sendText(text: String, speakReply: Boolean) {
        if ((text.isEmpty() && pending.isEmpty()) || sending) return
        sending = true
        error = ""
        input = ""
        val files = pending.toList()
        pending.clear()
        scope.launch {
            var sent = false
            try {
                val cid = convId ?: Api.createConversation().also { convId = it; onConversation(it) }
                val job = Api.sendMessage(cid, text, files)
                sent = true
                msgs.add(
                    AiMsg(
                        -System.nanoTime(), "user", text, System.currentTimeMillis(),
                        atts = files.mapIndexed { i, p -> AttRef(-(i + 1).toLong(), p.kind, p.name, p.thumb) },
                    ),
                )
                follow(job)
                if (speakReply) {
                    val last = msgs.lastOrNull()
                    if (last != null && !last.mine && last.n > 0) speak(last.n)
                }
            } catch (e: Exception) {
                error = errorMessage(ctx, e)
                // the message never reached the server: give the text back so nothing is lost
                if (!sent) { input = text; pending.addAll(files) }
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

    // like / dislike: shown at once, then saved (put back if that fails)
    fun react(n: Long, v: Int) {
        val idx = msgs.indexOfFirst { it.n == n }
        if (idx < 0) return
        val old = msgs[idx]
        msgs[idx] = old.withReact(v)
        scope.launch {
            try {
                Api.react(n, v)
            } catch (e: Exception) {
                val at = msgs.indexOfFirst { it.n == n }
                if (at >= 0) msgs[at] = msgs[at].withReact(old.react)
                error = errorMessage(ctx, e)
            }
        }
    }

    // "send again": Candy answers the last message once more and the new answer takes the place of the old one
    fun regenerate() {
        val cid = convId ?: return
        val last = msgs.lastOrNull() ?: return
        if (sending || last.mine || last.n <= 0) return
        sending = true
        error = ""
        msgs.removeAt(msgs.lastIndex)
        scope.launch {
            var started = false
            try {
                val job = Api.regenerate(cid)
                started = true
                follow(job)
            } catch (e: Exception) {
                error = errorMessage(ctx, e)
                if (!started) msgs.add(last)
            } finally {
                sending = false
                jobId = null
            }
        }
    }

    // share: Candy's reply as a picture, in the colours of the chat
    val snapColors = AiSnapColors(
        bg = cs.background.toArgb(),
        bubble = cs.surfaceContainerHigh.toArgb(),
        text = cs.onSurface.toArgb(),
        accent = cs.primary.toArgb(),
        mineText = cs.onPrimary.toArgb(),
    )
    fun shareReply(text: String) {
        scope.launch {
            if (!shareAiReply(ctx, text, snapColors)) error = ctx.getString(R.string.ai_share_fail)
        }
    }

    // the whole conversation on the screen (only messages the server already saved)
    fun shareChat() {
        // your own messages count too (a message you just sent has no server id yet); only a reply still being written is left out
        val list = msgs.filter { it.mine || it.n > 0 }.map { AiSnapMsg(it.text, it.mine) }
        if (list.isEmpty()) return
        scope.launch {
            if (!shareAiConversation(ctx, list, snapColors)) error = ctx.getString(R.string.ai_share_fail)
        }
    }

    fun send() = sendText(input.trim(), false)

    // ───── the + button: camera, gallery, text file ─────
    fun addImages(uris: List<Uri>) {
        val room = AttachKit.MAX_FILES - pending.size
        if (room <= 0) { error = ctx.getString(R.string.attach_max); return }
        scope.launch {
            for (u in uris.take(room)) {
                val p = AttachKit.fromImage(ctx, u)
                if (p != null) pending.add(p) else error = ctx.getString(R.string.attach_fail)
            }
        }
    }
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(AttachKit.MAX_FILES)) { uris ->
        if (uris.isNotEmpty()) addImages(uris)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = camFile
        camFile = null
        if (f != null) {
            if (ok) {
                scope.launch {
                    val p = if (pending.size < AttachKit.MAX_FILES) AttachKit.fromImage(ctx, Uri.fromFile(f)) else null
                    if (p != null) pending.add(p) else error = ctx.getString(R.string.attach_fail)
                    f.delete()
                }
            } else {
                f.delete()
            }
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            if (pending.size >= AttachKit.MAX_FILES) { error = ctx.getString(R.string.attach_max); return@rememberLauncherForActivityResult }
            scope.launch {
                val p = AttachKit.fromText(ctx, uri)
                if (p != null) pending.add(p) else error = ctx.getString(R.string.attach_fail)
            }
        }
    }
    fun openCamera() {
        if (pending.size >= AttachKit.MAX_FILES) { error = ctx.getString(R.string.attach_max); return }
        try {
            val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
            val f = File.createTempFile("cam_", ".jpg", dir)
            camFile = f
            cameraLauncher.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", f))
        } catch (e: Exception) {
            error = ctx.getString(R.string.attach_fail)
        }
    }

    // ───── talking to her: record, turn it into text on the server, send it and hear the answer ─────
    fun startRecording() {
        if (recorder.start()) {
            recording = true
            recPaused = false
            limitReached = false
            recElapsed = 0L
            amps.clear()
            error = ""
        } else {
            error = ctx.getString(R.string.voice_record_failed)
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else error = ctx.getString(R.string.voice_no_permission)
    }

    fun onMicClick() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun discardVoice() {
        recorder.cancel()
        recording = false
        recPaused = false
        limitReached = false
        amps.clear()
    }

    fun sendVoice() {
        val done = recorder.finish()
        recording = false
        recPaused = false
        limitReached = false
        amps.clear()
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
                if (t.isEmpty()) error = ctx.getString(R.string.ai_stt_empty) else sendText(t, true)
            } catch (e: Exception) {
                error = errorMessage(ctx, e)
            } finally {
                listening = false
            }
        }
    }

    LaunchedEffect(Unit) {
        reload()
        refreshVoice()
    }

    LaunchedEffect(recording, recPaused) {
        while (recording) {
            recElapsed = recorder.elapsedMs
            if (!recPaused) {
                amps.add((recorder.amplitude / 32767f).coerceIn(0f, 1f))
                if (amps.size > 40) amps.removeAt(0)
                if (recElapsed >= 60_000L) {
                    recorder.pause()
                    recPaused = true
                    limitReached = true
                }
            }
            delay(100)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recorder.cancel()
            VoicePlayer.stop()
        }
    }

    // when her voice stops (it ended, or you stopped it) her mouth stops too
    val voicePlaying = VoicePlayer.playingKey?.startsWith("ai#") == true
    LaunchedEffect(voicePlaying) {
        lastActive = System.currentTimeMillis()
        if (!voicePlaying && talkTick > 0 && !sending) {
            talkMs = 0L
            talkTick++
        }
    }

    // typing, sending and new messages count as someone being there, like a touch
    LaunchedEffect(input, msgs.size, sending) { lastActive = System.currentTimeMillis() }

    // keep the newest message in view, and keep following it while the reply grows
    LaunchedEffect(msgs.size, sending, listening) {
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0) listState.animateScrollToItem(total - 1)
    }
    val lastLen = msgs.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(lastLen) {
        if (sending && listState.layoutInfo.totalItemsCount > 0) listState.scrollBy(1_000_000f)
    }

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            // watches every touch without consuming it; once a second is enough to keep her awake
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                    val now = System.currentTimeMillis()
                    if (now - lastActive > 1000L) lastActive = now
                }
            }
        },
    ) {
        CandyStage(animated, talkTick, talkMs, lastActive)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            // only the menu button, so Candy stays fully visible; it sits on the same side as the drawer
            Box(
                Modifier.align(if (menuOnLeft == (LocalLayoutDirection.current == LayoutDirection.Rtl)) Alignment.End else Alignment.Start)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(cs.background.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                AppIconButton(Icons.Rounded.Menu, stringResource(R.string.menu), onMenu, tint = cs.onBackground)
            }

            if (voice.ttsLimit > 0 && voice.ttsLeft <= 5) {
                Muted(stringResource(R.string.ai_voice_left, voice.ttsLeft, voice.ttsLimit), Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
            }

            // messages
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
            ) {
                if (!loading && msgs.isEmpty() && error.isBlank() && !sending) {
                    item(key = "empty") {
                        Column(
                            // no box around it: only a soft fade so the text stays readable over the picture
                            Modifier.fillMaxWidth().padding(top = 36.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colorStops = arrayOf(0f to Color.Transparent, 0.22f to cs.background.copy(alpha = 0.9f), 1f to cs.background.copy(alpha = 0.9f)),
                                    ),
                                )
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Image(
                                painter = painterResource(R.drawable.candy_logo),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.height(112.dp),
                            )
                            Text(
                                greeting,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = cs.onBackground,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(6.dp))
                            for (id in listOf(R.string.ai_sugg_1, R.string.ai_sugg_2, R.string.ai_sugg_3)) {
                                val s = stringResource(id)
                                Surface(
                                    shape = CircleShape,
                                    color = cs.surfaceContainerHigh,
                                    modifier = Modifier.clip(CircleShape).clickable { input = s },
                                ) {
                                    Text(s, color = cs.onSurface, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Muted(stringResource(R.string.ai_disclaimer))
                        }
                    }
                }
                itemsIndexed(msgs, key = { _, m -> m.n }) { i, m ->
                    val newDay = i == 0 || !sameDay(msgs[i - 1].at, m.at)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (newDay) AiDayChip(aiDayLabel(ctx, m.at))
                        AiBubble(m)
                        if (m.memAdded.isNotEmpty() || m.memRemoved.isNotEmpty()) AiMemChips(m, onOpenMemory)
                        if (!m.mine && m.n > 0 && !(sending && i == msgs.lastIndex)) {
                            AiActionRow(
                                voice = true,
                                playing = VoicePlayer.playingKey == "ai#${m.n}",
                                loading = voiceBusy == m.n,
                                onVoice = { speak(m.n) },
                                react = m.react,
                                onLike = { react(m.n, if (m.react == 1) 0 else 1) },
                                onDislike = { react(m.n, if (m.react == -1) 0 else -1) },
                                onCopy = { copyAiText(ctx, m.text) },
                                onShare = { shareFor = m },
                                // answering again only makes sense for the last reply
                                onResend = if (!sending && i == msgs.lastIndex) ({ regenerate() }) else null,
                            )
                        }
                    }
                }
                if ((sending || listening) && (msgs.lastOrNull()?.mine != false)) item(key = "typing") {
                    AiTypingBubble(stringResource(if (listening) R.string.ai_listening else R.string.ai_thinking))
                }
                if (error.isNotBlank()) {
                    item(key = "error") { ErrorText(error) { error = ""; reload() } }
                }
            }

            // the message box, or the bar while you record
            if (recording) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    RecordingBar(
                        elapsedMs = recElapsed,
                        amps = amps,
                        paused = recPaused,
                        limitReached = limitReached,
                        onDelete = { discardVoice() },
                        onTogglePause = {
                            if (recPaused) {
                                recorder.resume()
                                recPaused = false
                            } else {
                                recorder.pause()
                                recPaused = true
                            }
                        },
                        onSend = { sendVoice() },
                    )
                }
            } else {
                val showMic = input.isBlank() && pending.isEmpty()
                val canSend = !listening && (showMic || input.isNotBlank() || pending.isNotEmpty())
                // which side the send / voice button sits on (picked in the settings)
                val buttonFirst = sendOnLeft != (LocalLayoutDirection.current == LayoutDirection.Rtl)
                if (pending.isNotEmpty()) PendingStrip(pending) { pending.remove(it) }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val inputBox: @Composable RowScope.() -> Unit = {
                    Surface(shape = MaterialTheme.shapes.extraLarge, color = cs.surfaceContainerHigh, modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Box {
                                Box(
                                    Modifier.padding(start = 4.dp, bottom = 4.dp).size(40.dp).clip(CircleShape)
                                        .clickable(enabled = !sending) { attachMenu = true },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.attach), tint = cs.onSurfaceVariant)
                                }
                                AttachMenu(
                                    expanded = attachMenu,
                                    onDismiss = { attachMenu = false },
                                    onCamera = { openCamera() },
                                    onGallery = {
                                        galleryPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                    onFile = { filePicker.launch(arrayOf("text/*", "application/json", "application/xml", "application/csv")) },
                                )
                            }
                            Box(Modifier.weight(1f).padding(start = 4.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
                                if (input.isEmpty()) {
                                    Text(stringResource(R.string.ai_hint), color = cs.onSurfaceVariant)
                                }
                                BasicTextField(
                                    value = input,
                                    onValueChange = { input = it.take(1500) },
                                    textStyle = LocalTextStyle.current.copy(color = cs.onSurface),
                                    cursorBrush = SolidColor(cs.primary),
                                    maxLines = 5,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    }
                    // while she writes this button stops her; otherwise it sends, or records when the box is empty
                    val actionButton: @Composable () -> Unit = {
                    val active = sending || canSend
                    Surface(
                        shape = CircleShape,
                        color = if (active) cs.primary else cs.surfaceContainerHigh,
                        modifier = Modifier.size(48.dp).clip(CircleShape).clickable(enabled = active) {
                            if (sending) stop() else if (showMic) onMicClick() else send()
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val tint = if (active) cs.onPrimary else cs.onSurfaceVariant
                            when {
                                sending -> Icon(Icons.Rounded.Stop, contentDescription = stringResource(R.string.stop), tint = tint)
                                showMic -> Icon(painterResource(R.drawable.ic_wave), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                                else -> Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = stringResource(R.string.send), tint = tint)
                            }
                        }
                    }
                    }
                    if (buttonFirst) {
                        actionButton()
                        inputBox()
                    } else {
                        inputBox()
                        actionButton()
                    }
                }
            }
        }
    }

    shareFor?.let { target ->
        AlertDialog(
            onDismissRequest = { shareFor = null },
            containerColor = cs.surfaceContainerHigh,
            titleContentColor = cs.onSurface,
            title = { Text(stringResource(R.string.ai_share_title)) },
            text = {
                Column {
                    AiShareChoice(stringResource(R.string.ai_share_one)) {
                        shareFor = null
                        shareReply(target.text)
                    }
                    AiShareChoice(stringResource(R.string.ai_share_all)) {
                        shareFor = null
                        shareChat()
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { shareFor = null }) { Text(stringResource(R.string.cancel), color = cs.onSurfaceVariant) }
            },
        )
    }
}

@Composable
private fun AiShareChoice(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 14.dp),
    )
}
