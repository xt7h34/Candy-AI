package com.candyai

import androidx.compose.material3.MaterialTheme
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

// ───────────── Wake word: "Hey Candy" while the app is closed ─────────────
// A foreground service listens with Vosk (offline, on the phone: the sound never leaves it) for four phrases only.
// When it hears one it does what the person chose: open the voice panel, open the typing panel, or answer out loud with no screen.

/** The offline speech model (English, small). The Arabic spellings of the four phrases are the same sounds, so one model covers both. */
object WakeModel {
    private const val URL_ZIP = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    private const val NAME = "vosk-model-small-en-us-0.15"
    private fun root(ctx: Context) = File(ctx.filesDir, "wake")
    fun dir(ctx: Context) = File(root(ctx), NAME)
    fun ready(ctx: Context) = File(dir(ctx), "am").isDirectory

    fun delete(ctx: Context) { root(ctx).deleteRecursively() }

    /** Downloads (about 40 MB) and unpacks. [onProgress] gets 0..1. */
    suspend fun download(ctx: Context, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val tmp = File(ctx.cacheDir, "wake_model.zip")
        val out = root(ctx).apply { deleteRecursively(); mkdirs() }
        try {
            val c = URL(URL_ZIP).openConnection() as HttpURLConnection
            c.connectTimeout = 20_000
            c.readTimeout = 30_000
            val total = c.contentLengthLong.takeIf { it > 0 } ?: 40_000_000L
            c.inputStream.use { ins ->
                tmp.outputStream().use { os ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n)
                        done += n
                        onProgress((done.toFloat() / total).coerceAtMost(0.95f))
                    }
                }
            }
            val base = out.canonicalPath + File.separator
            ZipInputStream(tmp.inputStream().buffered()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    val f = File(out, e.name)
                    if (!f.canonicalPath.startsWith(base)) continue          // nothing may leave the folder
                    if (e.isDirectory) f.mkdirs() else { f.parentFile?.mkdirs(); f.outputStream().use { z.copyTo(it) } }
                }
            }
            onProgress(1f)
            if (!ready(ctx)) throw java.io.IOException("model_incomplete")
        } catch (e: Exception) {
            out.deleteRecursively()
            throw e
        } finally {
            tmp.delete()
        }
    }
}

/** The service that listens. It is started from the app (the system allows a microphone service only then). */
class WakeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var model: Model? = null
    private var speech: SpeechService? = null
    private var busy = false                    // a wake word is being handled: the microphone belongs to that
    private var chat: SeeChat? = null           // follow-up questions within five minutes stay in one conversation
    private var chatAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Store.init(applicationContext)
        alive = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Store.wakeOn = false
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val n = notification()
            if (Build.VERSION.SDK_INT >= 30) startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) else startForeground(NOTE_ID, n)
        } catch (e: Exception) {
            stopSelf()                           // not allowed to start from the background (Android 14), or no microphone permission
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_IDLE) busy = false
        if (speech == null && !busy) scope.launch { listen() }
        return START_STICKY
    }

    override fun onDestroy() {
        alive = false
        scope.cancel()
        stopListening()
        try { model?.close() } catch (_: Exception) { }
        chat?.release()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.wake_channel), NotificationManager.IMPORTANCE_MIN))
        val stop = PendingIntent.getService(this, 7, Intent(this, WakeService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_edge_tile)
            .setContentTitle(getString(R.string.wake_note_title))
            .setContentText(getString(R.string.wake_note_text))
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.wake_note_stop), stop).build())
            .build()
    }

    // ───── listening ─────

    private suspend fun listen() {
        if (!Store.wakeOn || !WakeModel.ready(this) || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }
        try {
            val m = model ?: withContext(Dispatchers.IO) { Model(WakeModel.dir(this@WakeService).absolutePath) }.also { model = it }
            val rec = Recognizer(m, 16000f, GRAMMAR)
            rec.setWords(true)
            val sp = SpeechService(rec, 16000f)
            speech = sp
            sp.startListening(listener)
        } catch (e: Exception) {
            speech = null
            Toast.makeText(this, R.string.wake_failed, Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun stopListening() {
        val s = speech ?: return
        speech = null
        try { s.stop() } catch (_: Exception) { }
        try { s.shutdown() } catch (_: Exception) { }
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) {}
        override fun onFinalResult(hypothesis: String?) {}
        override fun onTimeout() {}
        override fun onError(exception: Exception?) { speech = null; if (!busy) scope.launch { delay(2000); if (speech == null && !busy) listen() } }
        override fun onResult(hypothesis: String?) {
            if (busy || hypothesis == null) return
            try {
                val o = JSONObject(hypothesis)
                val text = o.optString("text").trim()
                if (text !in PHRASES) return
                val words = o.optJSONArray("result") ?: return
                var low = 1.0
                for (i in 0 until words.length()) low = minOf(low, words.getJSONObject(i).optDouble("conf", 0.0))
                if (low >= threshold()) heard()
            } catch (_: Exception) { }
        }
    }

    private fun threshold() = when (Store.wakeSens) { 0 -> 0.85; 2 -> 0.5; else -> 0.7 }   // low / normal / high sensitivity

    private fun heard() {
        busy = true
        stopListening()
        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70).startTone(ToneGenerator.TONE_PROP_ACK, 150)
        } catch (_: Exception) { }
        when (Store.wakeAction) {
            "edge_voice" -> openEdge(true)
            "edge_type" -> openEdge(false)
            else -> scope.launch { delay(450); session() }
        }
    }

    private fun openEdge(voice: Boolean) {
        try {
            startActivity(Intent(this, EdgeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("voice", voice))
            scope.launch { delay(180_000); if (busy) { busy = false; resume() } }       // safety: never stay deaf
        } catch (e: Exception) {
            Toast.makeText(this, R.string.wake_cant_open, Toast.LENGTH_LONG).show()
            busy = false
            resume()
        }
    }

    private fun resume() {
        if (!Store.wakeOn) { stopSelf(); return }
        scope.launch { delay(600); if (speech == null && !busy) listen() }
    }

    // ───── answer out loud, with no screen ─────

    private suspend fun session() {
        val c = chat?.takeIf { System.currentTimeMillis() - chatAt < 5 * 60_000L } ?: SeeChat(applicationContext, scope, speakReplies = true).also { chat = it }
        c.error = ""
        c.actions = emptyList()
        c.startRecording()
        if (!c.recording) { done(); return }
        // wait for the person to speak, then for a short silence
        val t0 = System.currentTimeMillis()
        var heardVoice = false
        var quietSince = 0L
        while (true) {
            delay(120)
            val now = System.currentTimeMillis()
            val amp = c.level
            if (amp > SPEECH_LEVEL) { heardVoice = true; quietSince = 0L }
            else if (heardVoice) {
                if (quietSince == 0L) quietSince = now
                if (now - quietSince > 1300) break
            }
            if (!heardVoice && now - t0 > 7000) break
            if (now - t0 > 25_000) break
        }
        if (!heardVoice) { c.cancelRecording(); done(); return }
        c.finishRecording { text -> c.ask(text) }
        delay(500)
        while (c.listening || c.sending || c.recording) delay(250)
        if (c.error.isNotBlank()) Toast.makeText(this, c.error, Toast.LENGTH_LONG).show()
        // the reply is read aloud: give the audio a moment to arrive, then wait until it ends
        var waited = 0
        while (VoicePlayer.playingKey == null && waited < 6000) { delay(250); waited += 250 }
        var playing = 0
        while (VoicePlayer.playingKey != null && playing < 180_000) { delay(300); playing += 300 }
        if (c.actions.isNotEmpty()) {
            // an action needs the person's approval, which needs a screen
            WakeActionActivity.pending = c.actions
            c.actions = emptyList()
            try {
                startActivity(Intent(this, WakeActionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return                           // the activity wakes the service up again when it closes
            } catch (_: Exception) { Toast.makeText(this, R.string.wake_cant_open, Toast.LENGTH_LONG).show() }
        }
        done()
    }

    private fun done() {
        chatAt = System.currentTimeMillis()
        busy = false
        resume()
    }

    companion object {
        const val ACTION_STOP = "com.candyai.wake.STOP"
        const val ACTION_IDLE = "com.candyai.wake.IDLE"
        private const val CHANNEL = "candy_wake"
        private const val NOTE_ID = 4207
        private const val SPEECH_LEVEL = 2200
        val PHRASES = setOf("hey candy", "oh candy", "with candy", "candy see")
        private val GRAMMAR = JSONArray(PHRASES.toList() + "[unk]").toString()
        @Volatile var alive = false

        private fun micOk(ctx: Context) = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        fun start(ctx: Context, action: String? = null) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, WakeService::class.java).also { if (action != null) it.action = action })
            } catch (_: Exception) { }
        }

        /** Called when the app comes to the screen: brings the listening back if it is on and something stopped it. */
        fun ensure(ctx: Context) {
            if (Store.wakeOn && WakeModel.ready(ctx) && micOk(ctx)) start(ctx)
        }

        /** The panel that was opened by the wake word has closed: listen again. */
        fun idle(ctx: Context) {
            if (Store.wakeOn && alive) start(ctx, ACTION_IDLE)
        }

        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, WakeService::class.java)) }
    }
}

/** Shows the confirmation of a phone action that Candy prepared while she was answering without a screen. */
class WakeActionActivity : ComponentActivity() {
    companion object { @Volatile var pending: List<AgentAction> = emptyList() }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        ThemeMode.load()
        val list = pending
        pending = emptyList()
        if (list.isEmpty()) { finish(); return }
        setContent { CandyTheme { ActionConfirm(list) { finish() } } }
    }

    override fun onDestroy() {
        WakeService.idle(this)
        super.onDestroy()
    }
}

// ───────────── Settings ─────────────

@Composable
fun WakeSettingsCard() {
    val c = LocalCandy.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var on by remember { mutableStateOf(Store.wakeOn) }
    var action by remember { mutableStateOf(Store.wakeAction) }
    var sens by remember { mutableStateOf(Store.wakeSens) }
    var ready by remember { mutableStateOf(WakeModel.ready(ctx)) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf("") }
    var overlay by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    LaunchedEffect(Unit) { while (true) { overlay = Settings.canDrawOverlays(ctx); delay(1500) } }

    fun turnOn() {
        scope.launch {
            error = ""
            if (!WakeModel.ready(ctx)) {
                progress = 0f
                try { WakeModel.download(ctx) { progress = it } } catch (e: Exception) { error = ctx.getString(R.string.wake_download_failed); progress = null; return@launch }
                progress = null
                ready = true
            }
            Store.wakeOn = true
            on = true
            WakeService.start(ctx)
        }
    }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { turnOn() }          // the answer does not matter: the listening works either way
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) error = ctx.getString(R.string.wake_need_mic)
        else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
        else turnOn()
    }
    fun toggle(v: Boolean) {
        if (!v) { Store.wakeOn = false; on = false; WakeService.stop(ctx); return }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) mic.launch(Manifest.permission.RECORD_AUDIO) else turnOn()
    }

    SettingsRows {
        SettingRow(stringResource(R.string.wake_title), stringResource(R.string.wake_hint))
        SettingDivider()
        // the whole row is the switch
        SettingRow(
            stringResource(if (on) R.string.wake_on else R.string.wake_off),
            onClick = { if (progress == null) toggle(!on) },
            trailing = { Switch(checked = on, onCheckedChange = null) },
        )
        val p = progress
        if (p != null) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.wake_downloading), color = c.subtext, style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { p }, color = c.cta, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        }
        if (error.isNotBlank()) SettingNote(error, error = true)
        SettingNote(stringResource(R.string.wake_phrases))

        SettingDivider()
        SettingRow(stringResource(R.string.wake_action_title))
        for ((id, label) in listOf("edge_voice" to R.string.wake_a_voice, "edge_type" to R.string.wake_a_type, "voice_only" to R.string.wake_a_silent)) {
            SettingRadio(stringResource(label), action == id) { action = id; Store.wakeAction = id }
        }
        if (action != "voice_only" && !overlay) {
            SettingNote(stringResource(R.string.wake_overlay_hint))
            SettingButton(stringResource(R.string.wake_overlay_btn), {
                try { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { }
            })
        }

        SettingDivider()
        SettingRow(stringResource(R.string.wake_sens_title))
        for ((id, label) in listOf(0 to R.string.wake_s_low, 1 to R.string.wake_s_mid, 2 to R.string.wake_s_high)) {
            SettingRadio(stringResource(label), sens == id) { sens = id; Store.wakeSens = id }
        }
        SettingNote(stringResource(R.string.wake_limits))
        if (ready && !on) SettingButton(stringResource(R.string.wake_delete_model), { WakeModel.delete(ctx); ready = false })
    }
}
