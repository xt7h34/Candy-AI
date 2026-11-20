package com.candyai

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

/** The app language ("ar" or "en"). It is read straight from the prefs because it is needed before Store is initialised. */
object Lang {
    fun code(ctx: Context): String =
        ctx.getSharedPreferences("candy", Context.MODE_PRIVATE).getString("lang", "ar").let { if (it == "en") "en" else "ar" }

    fun direction(ctx: Context): LayoutDirection = if (code(ctx) == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr

    /** Call from attachBaseContext of every activity and service, so their strings come out in the chosen language. */
    fun wrap(base: Context): Context {
        val locale = Locale.forLanguageTag(code(base))
        Locale.setDefault(locale)
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(locale)
        cfg.setLayoutDirection(locale)
        return base.createConfigurationContext(cfg)
    }

    /** Saves the language and restarts the current screen so everything is redrawn with it. */
    fun change(ctx: Context, code: String) {
        Store.language = code
        var c: Context = ctx
        while (c is ContextWrapper) {
            if (c is Activity) {
                c.recreate()
                return
            }
            c = c.baseContext
        }
    }
}
