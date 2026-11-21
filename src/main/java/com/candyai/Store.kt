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

    var serverUrl: String
        get() = sp.getString("server", null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.SERVER_URL.ifBlank { "http://127.0.0.1:8788" }
        set(v) { sp.edit().putString("server", v).apply() }

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

    /** Circle to Search (the assistant gesture opens a frozen screenshot to draw on) is switched on. */
    var ctsOn: Boolean
        get() = sp.getBoolean("cts_on", true)
        set(v) { sp.edit().putBoolean("cts_on", v).apply() }

    /** Keep the screenshot in the gallery (Pictures/Candy) after Circle to Search closes. Default: it is forgotten. */
    var ctsSave: Boolean
        get() = sp.getBoolean("cts_save", false)
        set(v) { sp.edit().putBoolean("cts_save", v).apply() }

    /** "system" (follow the phone, default), "light" or "dark". */
    var themeMode: String
        get() = sp.getString("theme", "system").let { if (it == "light" || it == "dark") it else "system" }
        set(v) { sp.edit().putString("theme", if (v == "light" || v == "dark") v else "system").apply() }
}
