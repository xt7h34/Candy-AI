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

class EdgeInteractionService : VoiceInteractionService()

class EdgeSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = EdgeSession(this)
}

/**
 * The assistant gesture (long-press home / power, whichever the phone is set to). With "use screenshot" on in the phone's assistant settings,
 * Android hands over a screenshot: Candy shows it frozen so you can circle something (Circle to Search). Without a screenshot, or with
 * Circle to Search switched off in Candy's settings, the normal ask panel opens.
 */
class EdgeSession(context: Context) : VoiceInteractionSession(context) {
    private val handler = Handler(Looper.getMainLooper())
    private var opened = false
    private val noScreenshot = Runnable { openPanel() }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        opened = false
        Store.init(context.applicationContext)
        if (!Store.ctsOn || showFlags and SHOW_WITH_SCREENSHOT == 0) {
            openPanel()
        } else {
            // the screenshot arrives a moment after onShow; if it never does, fall back to the panel
            handler.postDelayed(noScreenshot, 1500)
        }
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        handler.removeCallbacks(noScreenshot)
        if (opened) return
        if (screenshot == null || !Store.ctsOn) {
            openPanel()
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

    private fun openPanel() {
        if (opened) return
        opened = true
        startAssistantActivity(Intent(context, EdgeActivity::class.java))
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
