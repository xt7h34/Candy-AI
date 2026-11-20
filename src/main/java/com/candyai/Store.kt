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
}
