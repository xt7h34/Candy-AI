package com.candyai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource

/**
 * Candy See over any app: the capsule at the bottom of the live screen.
 * Opened by floating Candy's tap, the shortcut and the quick-settings tile (typing), and by the assistant gesture in voice mode
 * (extra "voice": it opens listening, reads the answer out loud, and can attach one screenshot on request).
 */
class EdgeActivity : ComponentActivity() {
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
        EdgeSession.current?.hide()   // let go of the assistant session so it never keeps eating touches
        SeeHolder.shot = null   // the screenshot is only ever kept in memory, and only while the panel is open
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val was = this.intent.getBooleanExtra("voice", false)
        setIntent(intent)
        if (was != intent.getBooleanExtra("voice", false)) recreate()   // only when it switches between typing and voice
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(applicationContext)
        ThemeMode.load()
        val voice = intent.getBooleanExtra("voice", false)
        val autoShot = intent.getBooleanExtra("shot", false)
        val thenTranslate = intent.getStringExtra("then") == "translate"
        setContent {
            CandyTheme {
                CompositionLocalProvider(LocalLayoutDirection provides Lang.direction(this@EdgeActivity)) {
                    EdgePanel(
                        voice = voice,
                        autoShot = autoShot,
                        thenTranslate = thenTranslate,
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
fun EdgePanel(onClose: () -> Unit, onOpenFull: (String?) -> Unit, onReply: (Int) -> Unit = {}, voice: Boolean = false, autoShot: Boolean = false, thenTranslate: Boolean = false) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val chat = remember { SeeChat(ctx, scope, speakReplies = voice) }

    // came back from "screenshot": the shot is attached (and translated right away if that is what was asked)
    LaunchedEffect(Unit) {
        if (autoShot) {
            val b = SeeHolder.shot
            val p = if (b != null) AttachKit.fromBitmap(b, "screen.jpg") else null
            if (p == null) chat.error = ctx.getString(R.string.see_no_screen)
            else if (thenTranslate) chat.ask(ctx.getString(R.string.cts_p_translate), listOf(p), onReply)
            else chat.pending.add(p)
        }
    }

    // Circle to Search from the panel: the screenshot we already hold, or a fresh one from the assistant session
    val circle: () -> Unit = {
        val held = SeeHolder.shot
        if (held != null) {
            CtsHolder.shot = held
            ctx.startActivity(Intent(ctx, CtsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            onClose()
        } else if (EdgeInteractionService.show("cts")) {
            onClose()
        } else {
            chat.error = ctx.getString(R.string.see_need_assistant)
        }
    }

    val noRipple = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = if (chat.capturing) 0f else if (voice) 0.12f else 0.4f))
            .clickable(interactionSource = noRipple, indication = null) { onClose() },
    ) {
        SeeCapsule(
            chat = chat,
            hint = stringResource(if (voice) R.string.see_voice_hint else R.string.see_ask_anything),
            onSubmit = { t, files ->
                val text = if (t.isBlank() && files.isNotEmpty()) ctx.getString(R.string.see_screen_prompt) else t
                chat.ask(text, files, onReply)
            },
            onOpenFull = onOpenFull,
            modifier = Modifier.align(Alignment.BottomCenter),
            startListening = voice,
            onClose = onClose,
            onCircle = circle,
        )
    }
}
