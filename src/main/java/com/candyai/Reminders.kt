package com.candyai

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/**
 * SugarNote reminders: a note can carry a time; at that time Candy posts a notification that opens the notes.
 * Alarms live on the phone (no server needed when they ring) and are rebuilt from a small saved list after a reboot.
 */
object Reminders {
    private const val CHANNEL = "sugar_reminders"
    private const val PREFS = "sugar_alarms"
    private const val KEY = "list"

    fun title(text: String): String = text.substringBefore('\n').trim().take(80)

    private fun pending(ctx: Context, id: String, title: String): PendingIntent = PendingIntent.getBroadcast(
        ctx, id.hashCode(),
        Intent(ctx, ReminderReceiver::class.java).putExtra("id", id).putExtra("title", title),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun schedule(ctx: Context, id: String, at: Long, title: String) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        try {
            // not "exact": needs no special permission and may ring a few minutes late while the phone sleeps, which is fine for a reminder
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx, id, title))
        } catch (_: Exception) {
        }
    }

    /** Makes the phone's alarms match the notes: sets the future ones, cancels the ones that are gone or changed. */
    fun sync(ctx: Context, notes: List<Note>) {
        val app = ctx.applicationContext
        val now = System.currentTimeMillis()
        val future = notes.filter { it.remindAt > now }
        val am = app.getSystemService(AlarmManager::class.java)
        for ((id, _) in load(app)) {
            if (future.none { it.id == id }) am?.cancel(pending(app, id, ""))
        }
        for (n in future) schedule(app, n.id, n.remindAt, title(n.text))
        val arr = JSONArray()
        for (n in future) arr.put(JSONObject().put("id", n.id).put("at", n.remindAt).put("title", title(n.text)))
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    private fun load(ctx: Context): List<Pair<String, Long>> = try {
        val arr = JSONArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
        (0 until arr.length()).map { arr.getJSONObject(it).let { o -> o.getString("id") to o.getLong("at") } }
    } catch (_: Exception) {
        emptyList()
    }

    /** After a reboot all alarms are gone: set them again from the saved list. */
    fun restore(ctx: Context) {
        val now = System.currentTimeMillis()
        try {
            val arr = JSONArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.getLong("at") > now) schedule(ctx, o.getString("id"), o.getLong("at"), o.optString("title"))
            }
        } catch (_: Exception) {
        }
    }

    fun channel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            ctx.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.sugar_channel), NotificationManager.IMPORTANCE_HIGH))
        }
    }

    fun show(ctx: Context, id: String, title: String) {
        channel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 77,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("open_sugar", "list"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(ctx)
        val n = b.setSmallIcon(R.drawable.ic_edge_tile)
            .setContentTitle(ctx.getString(R.string.sugar_remind_title))
            .setContentText(ctx.getString(R.string.sugar_remind_body, title))
            .setStyle(Notification.BigTextStyle().bigText(ctx.getString(R.string.sugar_remind_body, title)))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java)?.notify(id.hashCode(), n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.show(context, intent.getStringExtra("id") ?: "", intent.getStringExtra("title") ?: "")
    }
}

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Reminders.restore(context)
    }
}
