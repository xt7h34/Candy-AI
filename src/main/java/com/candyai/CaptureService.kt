package com.candyai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * Takes ONE screenshot after the person approved the system's screen-share prompt, hands it to the waiting capsule and stops.
 * Nothing keeps recording: the projection is released as soon as a frame is grabbed.
 */
class CaptureService : Service() {
    companion object {
        @Volatile private var callback: ((Bitmap?) -> Unit)? = null
        private const val CHANNEL = "candy_see_capture"

        fun start(ctx: Context, resultCode: Int, data: Intent, done: (Bitmap?) -> Unit) {
            callback = done
            val i = Intent(ctx, CaptureService::class.java).putExtra("code", resultCode).putExtra("data", data)
            ContextCompat.startForegroundService(ctx, i)
        }
    }

    private val main = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    private fun finish(bmp: Bitmap?) {
        val cb = callback
        callback = null
        main.post { cb?.invoke(bmp) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Candy See", NotificationManager.IMPORTANCE_LOW))
        val note = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(getString(R.string.see_capture_note))
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(7117, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(7117, note)

        val code = intent?.getIntExtra("code", 0) ?: 0
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>("data")
        if (data == null) { finish(null); return START_NOT_STICKY }

        Thread {
            var proj: MediaProjection? = null
            var reader: ImageReader? = null
            var bmp: Bitmap? = null
            try {
                val dm = resources.displayMetrics
                val w = dm.widthPixels
                val h = dm.heightPixels
                val bg = Handler(Looper.getMainLooper())
                proj = getSystemService(MediaProjectionManager::class.java).getMediaProjection(code, data)
                proj.registerCallback(object : MediaProjection.Callback() {}, bg)   // required before the display is created on Android 14+
                reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
                val vd = proj.createVirtualDisplay("candy-see", w, h, dm.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, null)
                Thread.sleep(450)   // let the permission dialog finish closing so it is not in the picture
                var tries = 0
                while (bmp == null && tries < 15) {
                    val img = reader.acquireLatestImage()
                    if (img != null) {
                        val pl = img.planes[0]
                        val rowPad = pl.rowStride - pl.pixelStride * w
                        val full = Bitmap.createBitmap(w + rowPad / pl.pixelStride, h, Bitmap.Config.ARGB_8888)
                        full.copyPixelsFromBuffer(pl.buffer)
                        img.close()
                        bmp = if (full.width != w) Bitmap.createBitmap(full, 0, 0, w, h) else full
                    } else {
                        Thread.sleep(120)
                        tries++
                    }
                }
                vd?.release()
            } catch (_: Exception) {
                bmp = null
            } finally {
                try { reader?.close() } catch (_: Exception) { }
                try { proj?.stop() } catch (_: Exception) { }
            }
            main.post { finish(bmp) }
        }.start()
        return START_NOT_STICKY
    }
}
