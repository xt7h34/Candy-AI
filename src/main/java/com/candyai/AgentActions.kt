package com.candyai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A phone action Candy prepared (alarm, timer, calendar event, call, SMS, open an app / link / map / search). Always confirmed first. */
class AgentAction(val type: String, val args: List<String>) {
    fun arg(i: Int): String = args.getOrNull(i)?.trim() ?: ""
}

object AgentActions {
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    private fun time(s: String): Pair<Int, Int>? {
        val m = Regex("^(\\d{1,2}):(\\d{2})$").find(s.trim()) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        return if (h in 0..23 && min in 0..59) h to min else null
    }

    private fun begin(s: String): Long? = try {
        LocalDateTime.parse(s.trim(), dateFmt).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (_: Exception) { null }

    private fun isHttps(s: String) = Regex("^https://\\S+$", RegexOption.IGNORE_CASE).matches(s.trim())

    /** Only actions with usable details are shown. */
    fun valid(a: AgentAction): Boolean = when (a.type) {
        "alarm" -> time(a.arg(0)) != null
        "timer" -> (a.arg(0).toLongOrNull() ?: 0) in 1..86400
        "calendar" -> a.arg(0).isNotBlank() && begin(a.arg(1)) != null
        "call" -> a.arg(0).replace(Regex("[^\\d+*#]"), "").isNotEmpty()
        "sms" -> a.arg(0).replace(Regex("[^\\d+*#]"), "").isNotEmpty() && a.arg(1).isNotBlank()
        "open", "map", "search" -> a.arg(0).isNotBlank()
        "url" -> isHttps(a.arg(0))
        "agent" -> a.arg(0).isNotBlank()
        "settings" -> a.arg(0).isNotBlank()
        else -> false
    }

    private fun duration(ctx: Context, sec: Long): String = when {
        sec % 3600 == 0L -> ctx.getString(R.string.agent_dur_h, sec / 3600)
        sec % 60 == 0L -> ctx.getString(R.string.agent_dur_m, sec / 60)
        else -> ctx.getString(R.string.agent_dur_s, sec)
    }

    fun describe(ctx: Context, a: AgentAction): String = when (a.type) {
        "alarm" -> ctx.getString(R.string.agent_alarm, a.arg(0)) + a.arg(1).let { if (it.isBlank()) "" else " ($it)" }
        "timer" -> ctx.getString(R.string.agent_timer, duration(ctx, a.arg(0).toLongOrNull() ?: 0)) + a.arg(1).let { if (it.isBlank()) "" else " ($it)" }
        "calendar" -> ctx.getString(R.string.agent_calendar, a.arg(0), a.arg(1))
        "call" -> ctx.getString(R.string.agent_call, a.arg(0))
        "sms" -> ctx.getString(R.string.agent_sms, a.arg(0), a.arg(1))
        "open" -> ctx.getString(R.string.agent_open, a.arg(0))
        "url" -> ctx.getString(R.string.agent_url, a.arg(0))
        "map" -> ctx.getString(R.string.agent_map, a.arg(0))
        "search" -> ctx.getString(R.string.agent_search, a.arg(0))
        "agent" -> ctx.getString(R.string.agent_goal, a.arg(0))
        "settings" -> ctx.getString(R.string.agent_settings_open, a.arg(0))
        else -> a.type
    }

    private fun go(ctx: Context, i: Intent): Boolean = try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) { false }

    /** Does it. Returns false when the phone had nothing to handle it. */
    fun run(ctx: Context, a: AgentAction): Boolean = when (a.type) {
        "alarm" -> {
            val (h, m) = time(a.arg(0))!!
            val base = Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, h).putExtra(AlarmClock.EXTRA_MINUTES, m).putExtra(AlarmClock.EXTRA_MESSAGE, a.arg(1))
            // already confirmed in Candy's card, so the clock app just sets it; if the phone refuses that, it opens the clock instead
            go(ctx, Intent(base).putExtra(AlarmClock.EXTRA_SKIP_UI, true)) || go(ctx, base)
        }
        "timer" -> {
            val base = Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, (a.arg(0).toIntOrNull() ?: 60)).putExtra(AlarmClock.EXTRA_MESSAGE, a.arg(1))
            go(ctx, Intent(base).putExtra(AlarmClock.EXTRA_SKIP_UI, true)) || go(ctx, base)
        }
        "calendar" -> {
            val start = begin(a.arg(1)) ?: 0L
            val mins = (a.arg(2).toLongOrNull() ?: 60L).coerceIn(5, 1440)
            go(
                ctx,
                Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.Events.TITLE, a.arg(0))
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + mins * 60_000),
            )
        }
        // the dialer opens with the number; the person presses call / send themselves
        "call" -> go(ctx, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", a.arg(0).replace(Regex("[^\\d+*#]"), ""), null)))
        "sms" -> go(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + a.arg(0).replace(Regex("[^\\d+*#]"), ""))).putExtra("sms_body", a.arg(1)))
        "open" -> openApp(ctx, a.arg(0)) || (settingsAction(a.arg(0)) != null && openSettings(ctx, a.arg(0)))
        "agent" -> startAgent(ctx, a.arg(0))
        "settings" -> openSettings(ctx, a.arg(0))
        "url" -> go(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(a.arg(0))))
        "map" -> go(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(a.arg(0)))))
        "search" -> go(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(a.arg(0)))))
        else -> false
    }

    /** Candy takes the screen: needs the accessibility service. Without it, the person is sent to switch it on. */
    private fun startAgent(ctx: Context, goal: String): Boolean {
        val svc = CandyAgentService.inst
        if (svc == null || !CandyAgentService.enabled(ctx)) {
            Toast.makeText(ctx, R.string.agent_need_service, Toast.LENGTH_LONG).show()
            return go(ctx, Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        svc.start(goal)
        return true
    }

    /** The system settings page for a word like "wifi", "واي فاي" or "الإشعارات"; null when it is not about settings. */
    private fun settingsAction(key: String): String? {
        val k = key.lowercase()
        fun has(vararg w: String) = w.any { k.contains(it) }
        return when {
            has("wifi", "wi-fi", "واي", "وايفاي", "وای") -> android.provider.Settings.ACTION_WIFI_SETTINGS
            has("bluetooth", "بلوتوث", "بلوتث") -> android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
            has("airplane", "طيران") -> android.provider.Settings.ACTION_AIRPLANE_MODE_SETTINGS
            has("hotspot", "هوت سبوت", "هوتسبوت", "نقطة اتصال", "نقطة الاتصال") -> android.provider.Settings.ACTION_WIRELESS_SETTINGS
            has("mobiledata", "mobile data", "بيانات", "شبكة") -> android.provider.Settings.ACTION_NETWORK_OPERATOR_SETTINGS
            has("battery", "بطاري") -> android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS
            has("location", "gps", "موقع") -> android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS
            has("sound", "volume", "صوت", "رنين") -> android.provider.Settings.ACTION_SOUND_SETTINGS
            has("display", "brightness", "شاشة", "سطوع", "إضاءة", "اضاءة") -> android.provider.Settings.ACTION_DISPLAY_SETTINGS
            has("notification", "اشعار", "إشعار") -> if (android.os.Build.VERSION.SDK_INT >= 26) android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS else android.provider.Settings.ACTION_SETTINGS
            has("apps", "تطبيقات") -> android.provider.Settings.ACTION_APPLICATION_SETTINGS
            has("security", "أمان", "امان") -> android.provider.Settings.ACTION_SECURITY_SETTINGS
            has("accessib", "وصول", "امكانية", "إمكانية") -> android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS
            has("storage", "تخزين") -> android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS
            has("date", "time", "تاريخ", "وقت") -> android.provider.Settings.ACTION_DATE_SETTINGS
            has("language", "لغة", "لغات") -> android.provider.Settings.ACTION_LOCALE_SETTINGS
            has("nfc") -> android.provider.Settings.ACTION_NFC_SETTINGS
            has("main", "settings", "إعدادات", "اعدادات") -> android.provider.Settings.ACTION_SETTINGS
            else -> null
        }
    }

    private fun openSettings(ctx: Context, key: String): Boolean {
        val action = settingsAction(key) ?: android.provider.Settings.ACTION_SETTINGS
        val i = Intent(action)
        if (action == android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS) i.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        return go(ctx, i) || go(ctx, Intent(android.provider.Settings.ACTION_SETTINGS))
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("\\s+"), "")

    private fun openApp(ctx: Context, name: String): Boolean {
        val pm = ctx.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val want = norm(name)
        val hit = apps.firstOrNull { norm(it.loadLabel(pm).toString()) == want }
            ?: apps.firstOrNull { norm(it.loadLabel(pm).toString()).contains(want) }
            ?: apps.firstOrNull { want.contains(norm(it.loadLabel(pm).toString())) && norm(it.loadLabel(pm).toString()).length >= 3 }
        val launch = hit?.activityInfo?.packageName?.let { pm.getLaunchIntentForPackage(it) } ?: return false
        return go(ctx, launch)
    }
}

/** The confirmation card: one action at a time, always asked (no "always allow" yet). */
@Composable
fun ActionConfirm(actions: List<AgentAction>, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalCandy.current
    val list = remember(actions) { actions.filter { AgentActions.valid(it) } }
    var i by remember(actions) { mutableIntStateOf(0) }
    if (list.isEmpty()) {
        LaunchedEffect(actions) { onDone() }
        return
    }
    val a = list[i]
    fun next() { if (i + 1 < list.size) i++ else onDone() }
    AlertDialog(
        onDismissRequest = { onDone() },
        containerColor = c.surface,
        title = { Text(stringResource(R.string.agent_ask_title), color = c.text) },
        text = {
            Text(
                AgentActions.describe(ctx, a), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                style = TextStyle(textDirection = TextDirection.Content),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (!AgentActions.run(ctx, a)) Toast.makeText(ctx, R.string.agent_failed, Toast.LENGTH_SHORT).show()
                next()
            }) { Text(stringResource(R.string.agent_do), color = c.userBubble, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = { next() }) { Text(stringResource(R.string.agent_skip), color = c.subtext) } },
    )
}
