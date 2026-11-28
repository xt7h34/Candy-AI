package com.candyai

import android.content.Context
import android.content.SharedPreferences

/** Small local storage: server address, guest secret and the login token. */
object Store {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("candy", Context.MODE_PRIVATE)
    }

    var token: String?
        get() = sp.getString("token", null)
        set(v) { sp.edit().putString("token", v).apply() }

    var guestSecret: String?
        get() = sp.getString("guest_secret", null)
        set(v) { sp.edit().putString("guest_secret", v).apply() }

    /** Candy on the phone's screen (over every app) is switched on. */
    var overlayOn: Boolean
        get() = sp.getBoolean("overlay_on", false)
        set(v) { sp.edit().putBoolean("overlay_on", v).apply() }

    /** The server address always comes from the build (api-url.txt), so every user is on the same one. */
    val serverUrl: String
        get() = BuildConfig.SERVER_URL.ifBlank { "http://127.0.0.1:8788" }

    /** The app language: "ar" (default) or "en". Lang reads the same key before Store exists. */
    var language: String
        get() = sp.getString("lang", "ar").let { if (it == "en") "en" else "ar" }
        set(v) { sp.edit().putString("lang", if (v == "en") "en" else "ar").apply() }

    /** Which edge the conversations drawer opens from. Until the person picks one it follows the language (right for Arabic, left for English). */
    var drawerSide: String
        get() = (sp.getString("drawer_side", null) ?: if (language == "en") "left" else "right").let { if (it == "left") "left" else "right" }
        set(v) { sp.edit().putString("drawer_side", if (v == "left") "left" else "right").apply() }

    /** Which edge the send / voice button sits on in the message box. Default: left for Arabic (as before), right for English. */
    var sendSide: String
        get() = (sp.getString("send_side", null) ?: if (language == "en") "right" else "left").let { if (it == "left") "left" else "right" }
        set(v) { sp.edit().putString("send_side", if (v == "left") "left" else "right").apply() }

    /** The login screen has been passed once (as a guest for now), so it is not shown again. */
    var loginDone: Boolean
        get() = sp.getBoolean("login_done", false)
        set(v) { sp.edit().putBoolean("login_done", v).apply() }

    /** Kinds of phone action Candy may do without asking first (see AgentActions.GROUPS). Sensitive steps inside screen control still ask. */
    var autoActions: Set<String>
        get() = sp.getStringSet("auto_actions", emptySet())?.toSet() ?: emptySet()
        set(v) { sp.edit().putStringSet("auto_actions", v.toSet()).apply() }

    /** The wake word ("Hey Candy") listening is on. */
    var wakeOn: Boolean
        get() = sp.getBoolean("wake_on", false)
        set(v) { sp.edit().putBoolean("wake_on", v).apply() }

    /** What the wake word does: "edge_voice" (default), "edge_type" or "voice_only" (answers out loud with no screen). */
    var wakeAction: String
        get() = sp.getString("wake_action", "edge_voice").let { if (it == "edge_type" || it == "voice_only") it!! else "edge_voice" }
        set(v) { sp.edit().putString("wake_action", v).apply() }

    /** 0 low, 1 normal, 2 high: how sure the phone must be that it heard the phrase. */
    var wakeSens: Int
        get() = sp.getInt("wake_sens", 1).coerceIn(0, 2)
        set(v) { sp.edit().putInt("wake_sens", v.coerceIn(0, 2)).apply() }

    /** Circle to Search (the assistant gesture opens a frozen screenshot to draw on) is switched on. */
    var ctsOn: Boolean
        get() = sp.getBoolean("cts_on", true)
        set(v) { sp.edit().putBoolean("cts_on", v).apply() }

    /** Keep the screenshot in the gallery (Pictures/Candy) after Circle to Search closes. Default: it is forgotten. */
    var ctsSave: Boolean
        get() = sp.getBoolean("cts_save", false)
        set(v) { sp.edit().putBoolean("cts_save", v).apply() }

    /** What an assistant gesture that Candy cannot tell apart (home vs power) opens: "cts" (default) or "voice". */
    var seeUnknown: String
        get() = sp.getString("see_unknown", "cts").let { if (it == "voice") "voice" else "cts" }
        set(v) { sp.edit().putString("see_unknown", if (v == "voice") "voice" else "cts").apply() }

    /** Diagnostic: how the assistant was invoked the last time (shown in Settings). */
    var lastAssist: String
        get() = sp.getString("last_assist", "") ?: ""
        set(v) { sp.edit().putString("last_assist", v).apply() }

    /** When the person sent messages (millis, newest last, the last 40 only): the mood of the face skin is worked out from these. */
    private var sends: List<Long>
        get() = (sp.getString("sends", "") ?: "").split(',').mapNotNull { it.toLongOrNull() }
        set(v) { sp.edit().putString("sends", v.takeLast(40).joinToString(",")).apply() }

    fun recordSend() { sends = sends + System.currentTimeMillis() }
    val lastSend: Long get() = sends.lastOrNull() ?: 0L
    fun sendsSince(since: Long): Int = sends.count { it >= since }

    /** What floating Candy looks like while she is tucked away: rect (default), donut, crown or bear. */
    var tabSkin: String
        get() = sp.getString("tab_skin", "rect") ?: "rect"
        set(v) { sp.edit().putString("tab_skin", v).apply() }

    /** "system" (follow the phone, default), "light" or "dark". */
    var themeMode: String
        get() = sp.getString("theme", "system").let { if (it == "light" || it == "dark") it else "system" }
        set(v) { sp.edit().putString("theme", if (v == "light" || v == "dark") v else "system").apply() }

    /** The app font: "amman" (default, bundled) or "cairo" (needs cairo_light.ttf and cairo_bold.ttf in res/font; falls back to Amman without them). */
    var fontChoice: String
        get() = sp.getString("font", "amman").let { if (it == "cairo") "cairo" else "amman" }
        set(v) { sp.edit().putString("font", if (v == "cairo") "cairo" else "amman").apply() }

    /** Which outfit Candy wears: "default", "rain", "green", "stars" or "academy" (see CandyOutfits). */
    var outfit: String
        get() = sp.getString("outfit", "default") ?: "default"
        set(v) { sp.edit().putString("outfit", v).apply() }
}
