package com.candyai

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/** The quick-settings tile: pull down the notification shade, tap "Candy", and the ask panel opens over the current app. */
class EdgeTileService : TileService() {
    override fun onStartListening() {
        qsTile?.let {
            it.state = Tile.STATE_INACTIVE
            it.updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        val open = Intent(this, EdgeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(open)
        }
    }
}

// ───── Candy as the phone's digital assistant: choose it in Settings > Default apps > Digital assistant, then long-press the power button ─────

class EdgeInteractionService : VoiceInteractionService() {
    companion object {
        @Volatile private var inst: EdgeInteractionService? = null

        /**
         * Candy asks Android for the assistant session herself, with a screenshot and no permission prompt (works while Candy is the chosen assistant).
         * [mode]: "cts" = Circle to Search, "shot" = back to the panel with the screenshot attached ([then] = "translate" also translates it).
         * Returns false when Candy is not the active assistant.
         */
        fun show(mode: String, then: String? = null): Boolean {
            val s = inst ?: return false
            return try {
                val args = Bundle().apply { putString("candy_mode", mode); if (then != null) putString("candy_then", then) }
                s.showSession(args, VoiceInteractionSession.SHOW_WITH_ASSIST or VoiceInteractionSession.SHOW_WITH_SCREENSHOT)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    override fun onReady() {
        super.onReady()
        inst = this
    }

    override fun onShutdown() {
        if (inst === this) inst = null
        super.onShutdown()
    }
}

class EdgeSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = EdgeSession(this)
}

/**
 * The assistant gesture. Candy See has two faces:
 *  - Circle to Search (home long-press): the screenshot Android hands over is shown frozen, you circle or ask.
 *  - the voice panel (power long-press): opens listening over the live screen; the screenshot is kept only for the optional "attach the screen" button.
 * Which one opens depends on how the assistant was invoked. Android passes that in [onShow]'s args (the key is internal, so it is also
 * written to Settings > Candy See as a diagnostic line); when it is not recognised, Settings decides.
 */
class EdgeSession(context: Context) : VoiceInteractionSession(context) {
    private val handler = Handler(Looper.getMainLooper())
    private var opened = false
    private var voiceMode = false
    private var mode = ""      // "cts" / "shot" when Candy asked for the session herself
    private var then: String? = null
    private val noScreenshot = Runnable { open(voice = false) }

    companion object {
        @Volatile var current: EdgeSession? = null
    }

    override fun onCreate() {
        super.onCreate()
        current = this
        // the session has no screen of its own: only the Candy activity is shown, so the system window must never cover it or take its touches
        try { setUiEnabled(false) } catch (_: Exception) { }
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        opened = false
        Store.init(context.applicationContext)
        val withShot = showFlags and SHOW_WITH_SCREENSHOT != 0
        mode = args?.getString("candy_mode") ?: ""
        then = args?.getString("candy_then")
        val inv = args?.getInt("invocation_type", -1) ?: -1
        val keys = args?.keySet()?.joinToString(",") ?: "-"
        // 6 = power long-press, 5 = home long-press, 1 = corner swipe on stock Android; other phones may differ
        voiceMode = when {
            mode == "cts" || mode == "shot" -> false
            !Store.ctsOn || !withShot -> true
            inv == 6 -> true
            inv == 5 || inv == 1 -> false
            else -> Store.seeUnknown == "voice"
        }
        Store.lastAssist = "invocation_type=$inv, flags=$showFlags, screenshot=${if (withShot) "yes" else "no"}, keys=[$keys] → ${if (voiceMode) "voice" else "cts"}"
        SeeHolder.shot = null
        SeeHolder.hasScreen = withShot
        if (voiceMode) {
            open(voice = true)
        } else {
            // the screenshot arrives a moment after onShow; if it never does, fall back to the typing panel
            handler.postDelayed(noScreenshot, 1500)
        }
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        handler.removeCallbacks(noScreenshot)
        if (voiceMode) {
            SeeHolder.shot = screenshot   // kept in memory for the "+ > screenshot" item
            return
        }
        if (mode == "shot") {
            if (opened) return
            SeeHolder.shot = screenshot
            opened = true
            startAssistantActivity(
                Intent(context, EdgeActivity::class.java).putExtra("shot", screenshot != null).putExtra("then", then),
            )
            return
        }
        if (opened) return
        if (screenshot == null) {
            open(voice = false)
        } else {
            opened = true
            CtsHolder.shot = screenshot
            startAssistantActivity(Intent(context, CtsActivity::class.java))
        }
    }

    override fun onHide() {
        handler.removeCallbacks(noScreenshot)
        super.onHide()
    }

    private fun open(voice: Boolean) {
        if (opened) return
        opened = true
        startAssistantActivity(Intent(context, EdgeActivity::class.java).putExtra("voice", voice))
    }
}

/** Android only lists an assistant that also has a recognition service. Candy listens through her own mic button, so this one does nothing. */
class EdgeRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        listener?.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onCancel(listener: Callback?) {}

    override fun onStopListening(listener: Callback?) {}
}
