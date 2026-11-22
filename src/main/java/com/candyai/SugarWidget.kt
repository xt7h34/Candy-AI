package com.candyai

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import kotlin.concurrent.thread

/**
 * The home-screen widget: up to four notes (pinned first), tilted like the cards in the app.
 * The notes come from a small cache the app writes whenever it loads them; every refresh the widget also asks the server once.
 */
class SugarWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids)   // what we have right now, immediately
        val pending = goAsync()
        thread {
            try {
                Store.init(context.applicationContext)
                if (Store.loginDone) {
                    val list = runBlocking { Api.notes() }
                    save(context, list)   // save() re-renders every widget
                }
            } catch (_: Exception) {
                // offline or not signed in: the cached notes stay
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val PREFS = "sugar_widget"
        private const val KEY = "notes"

        /** Writes the cache and redraws every widget on the home screen. */
        fun save(ctx: Context, notes: List<Note>) {
            val arr = JSONArray()
            for (n in notes.take(8)) arr.put(JSONObject().put("id", n.id).put("text", n.text).put("color", n.color))
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
            val m = AppWidgetManager.getInstance(ctx)
            val ids = m.getAppWidgetIds(ComponentName(ctx, SugarWidget::class.java))
            if (ids.isNotEmpty()) render(ctx, m, ids)
        }

        private fun open(ctx: Context, what: String, code: Int): PendingIntent = PendingIntent.getActivity(
            ctx, code,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("open_sugar", what),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun render(ctx: Context, m: AppWidgetManager, ids: IntArray) {
            val cached = try {
                JSONArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
            } catch (_: Exception) {
                JSONArray()
            }
            val slots = intArrayOf(R.id.sw_n1, R.id.sw_n2, R.id.sw_n3, R.id.sw_n4)
            val bgs = intArrayOf(
                R.drawable.note_bg_0, R.drawable.note_bg_1, R.drawable.note_bg_2, R.drawable.note_bg_3,
                R.drawable.note_bg_4, R.drawable.note_bg_5, R.drawable.note_bg_6, R.drawable.note_bg_7,
            )
            for (id in ids) {
                val v = RemoteViews(ctx.packageName, R.layout.sugar_widget)
                for ((i, slot) in slots.withIndex()) {
                    val o = cached.optJSONObject(i)
                    if (o == null) {
                        v.setViewVisibility(slot, android.view.View.INVISIBLE)
                    } else {
                        v.setViewVisibility(slot, android.view.View.VISIBLE)
                        v.setTextViewText(slot, o.optString("text"))
                        v.setInt(slot, "setBackgroundResource", bgs[((o.optInt("color") % 8) + 8) % 8])
                        v.setFloat(slot, "setRotation", noteTilt(o.optString("id")))
                        v.setOnClickPendingIntent(slot, open(ctx, "list", 10 + i))
                    }
                }
                v.setOnClickPendingIntent(R.id.sw_add, open(ctx, "new", 20))
                v.setOnClickPendingIntent(R.id.sw_root, open(ctx, "list", 21))
                m.updateAppWidget(id, v)
            }
        }
    }
}
