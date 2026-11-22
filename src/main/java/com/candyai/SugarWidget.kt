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

        private val SLOT = intArrayOf(R.id.sw_n1, R.id.sw_n2, R.id.sw_n3, R.id.sw_n4, R.id.sw_n5, R.id.sw_n6)
        private val TEXT = intArrayOf(R.id.sw_t1, R.id.sw_t2, R.id.sw_t3, R.id.sw_t4, R.id.sw_t5, R.id.sw_t6)
        private val PIN = intArrayOf(R.id.sw_p1, R.id.sw_p2, R.id.sw_p3, R.id.sw_p4, R.id.sw_p5, R.id.sw_p6)
        private val ROW = intArrayOf(R.id.sw_row1, R.id.sw_row2, R.id.sw_row3)
        private val BG = intArrayOf(
            R.drawable.note_bg_0, R.drawable.note_bg_1, R.drawable.note_bg_2, R.drawable.note_bg_3,
            R.drawable.note_bg_4, R.drawable.note_bg_5, R.drawable.note_bg_6, R.drawable.note_bg_7,
        )
        private val PINS = intArrayOf(
            R.drawable.pin_0, R.drawable.pin_1, R.drawable.pin_2, R.drawable.pin_3,
            R.drawable.pin_4, R.drawable.pin_5, R.drawable.pin_6, R.drawable.pin_7,
        )

        private fun render(ctx: Context, m: AppWidgetManager, ids: IntArray) {
            val cached = try {
                JSONArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
            } catch (_: Exception) {
                JSONArray()
            }
            for (id in ids) {
                // how many cards fit: one or two columns and one to three rows, by the size the person gave the widget
                val o = m.getAppWidgetOptions(id)
                val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
                val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
                val cols = if (w >= 150) 2 else 1
                val rows = ((h - 30) / 85).coerceIn(1, 3)
                val v = RemoteViews(ctx.packageName, R.layout.sugar_widget)
                var next = 0
                for (r in 0 until 3) {
                    if (r >= rows) { v.setViewVisibility(ROW[r], android.view.View.GONE); continue }
                    v.setViewVisibility(ROW[r], android.view.View.VISIBLE)
                    for (c in 0 until 2) {
                        val slot = r * 2 + c
                        if (c >= cols) { v.setViewVisibility(SLOT[slot], android.view.View.GONE); continue }
                        val n = cached.optJSONObject(next++)
                        if (n == null) {
                            v.setViewVisibility(SLOT[slot], android.view.View.INVISIBLE)
                            continue
                        }
                        val text = n.optString("text")
                        val title = text.substringBefore('\n').length
                        val styled = android.text.SpannableString(text).apply {
                            setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, title, 0)
                        }
                        val color = ((n.optInt("color") % 8) + 8) % 8
                        v.setViewVisibility(SLOT[slot], android.view.View.VISIBLE)
                        v.setTextViewText(TEXT[slot], styled)
                        v.setInt(TEXT[slot], "setBackgroundResource", BG[color])
                        v.setImageViewResource(PIN[slot], PINS[color])
                        v.setFloat(SLOT[slot], "setRotation", noteTilt(n.optString("id")))
                        v.setOnClickPendingIntent(SLOT[slot], open(ctx, "list", 10 + slot))
                    }
                }
                v.setOnClickPendingIntent(R.id.sw_add, open(ctx, "new", 20))
                v.setOnClickPendingIntent(R.id.sw_root, open(ctx, "list", 21))
                m.updateAppWidget(id, v)
            }
        }
    }

    // resized by the person: show as many cards as now fit
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle?) {
        render(context, manager, intArrayOf(appWidgetId))
    }
}
