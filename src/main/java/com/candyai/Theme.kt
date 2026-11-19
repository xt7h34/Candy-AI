package com.candyai

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** App colors. Light: Eggshell / Cornsilk / Bright pink. Dark: Dark purple / Wisteria / Sunglow. */
class CandyColors(
    val bg: Color,
    val surface: Color,
    val text: Color,
    val subtext: Color,
    val accent: Color,
    val onAccent: Color,
    val userBubble: Color,
    val onUserBubble: Color,
    val border: Color,
)

val LightCandy = CandyColors(
    bg = Color(0xFFFCFCFC),          // Eggshell
    surface = Color(0xFFFFFAE3),     // Cornsilk
    text = Color(0xFF2B1B20),
    subtext = Color(0xFF7A6A6E),
    accent = Color(0xFFF7567C),      // Bright pink (buttons, big elements)
    onAccent = Color.White,
    userBubble = Color(0xFFC93A5E),  // darker pink: readable white text at small sizes
    onUserBubble = Color.White,
    border = Color(0xFFEFE6C8),
)

val DarkCandy = CandyColors(
    bg = Color(0xFF210B2C),          // Dark purple
    surface = Color(0xFF301640),
    text = Color(0xFFF3EAFB),
    subtext = Color(0xFFB9A3CC),
    accent = Color(0xFFFFD166),      // Sunglow
    onAccent = Color(0xFF210B2C),
    userBubble = Color(0xFFBC96E6),  // Wisteria
    onUserBubble = Color(0xFF210B2C),
    border = Color(0xFF45265A),
)

val LocalCandy = staticCompositionLocalOf { LightCandy }

@Composable
fun CandyTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkCandy else LightCandy
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent,
            background = c.bg, onBackground = c.text,
            surface = c.surface, onSurface = c.text,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent,
            background = c.bg, onBackground = c.text,
            surface = c.surface, onSurface = c.text,
        )
    }
    CompositionLocalProvider(LocalCandy provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
