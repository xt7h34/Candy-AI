package com.candyai

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Simple colors used by the list screens. Light: Eggshell / Cornsilk / Bright pink. Dark: Dark purple / Wisteria / Sunglow. */
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
    accent = Color(0xFFF7567C),      // Bright pink
    onAccent = Color.White,
    userBubble = Color(0xFFC93A5E),  // darker pink: readable text at small sizes
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

private val LightScheme = lightColorScheme(
    primary = Color(0xFFF7567C), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E1), onPrimaryContainer = Color(0xFF5C0F26),
    secondary = Color(0xFFC93A5E), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFEFB8), onSecondaryContainer = Color(0xFF3D2F00),
    tertiary = Color(0xFFC93A5E), onTertiary = Color.White,
    background = Color(0xFFFCFCFC), onBackground = Color(0xFF2B1B20),
    surface = Color(0xFFFCFCFC), onSurface = Color(0xFF2B1B20),
    surfaceVariant = Color(0xFFFFF3C7), onSurfaceVariant = Color(0xFF7A6A6E),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFFFCEF),
    surfaceContainer = Color(0xFFFFF8DA), surfaceContainerHigh = Color(0xFFFFF3C7), surfaceContainerHighest = Color(0xFFFFEDB0),
    outline = Color(0xFF9C8A8E), outlineVariant = Color(0xFFEFE6C8),
    error = Color(0xFFB3261E),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFBC96E6), onPrimary = Color(0xFF210B2C),
    primaryContainer = Color(0xFF4A2A6B), onPrimaryContainer = Color(0xFFEBDCFF),
    secondary = Color(0xFFFFD166), onSecondary = Color(0xFF210B2C),
    secondaryContainer = Color(0xFF45265A), onSecondaryContainer = Color(0xFFF3EAFB),
    tertiary = Color(0xFFFFD166), onTertiary = Color(0xFF210B2C),
    background = Color(0xFF210B2C), onBackground = Color(0xFFF3EAFB),
    surface = Color(0xFF210B2C), onSurface = Color(0xFFF3EAFB),
    surfaceVariant = Color(0xFF301640), onSurfaceVariant = Color(0xFFB9A3CC),
    surfaceContainerLowest = Color(0xFF1A0824), surfaceContainerLow = Color(0xFF260F33),
    surfaceContainer = Color(0xFF2A1237), surfaceContainerHigh = Color(0xFF301640), surfaceContainerHighest = Color(0xFF3A1F4D),
    outline = Color(0xFF7E6A94), outlineVariant = Color(0xFF45265A),
    error = Color(0xFFFFB4AB),
)

private val CandyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Amman, the font of the whole app: Light for text you read, Bold for headings, names and buttons. */
private val AmmanFamily = FontFamily(
    Font(R.font.amman_light, FontWeight.Light),
    Font(R.font.amman_bold, FontWeight.Bold),
)

// No letterSpacing anywhere: it breaks the joined letters of Arabic.
private val CandyTypography = Typography(
    displaySmall = TextStyle(fontFamily = AmmanFamily, fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontFamily = AmmanFamily, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontFamily = AmmanFamily, fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontFamily = AmmanFamily, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = AmmanFamily, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = AmmanFamily, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontFamily = AmmanFamily, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontFamily = AmmanFamily, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Light),
    bodyMedium = TextStyle(fontFamily = AmmanFamily, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Light),
    bodySmall = TextStyle(fontFamily = AmmanFamily, fontSize = 12.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.Light),
    labelLarge = TextStyle(fontFamily = AmmanFamily, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontFamily = AmmanFamily, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
    labelSmall = TextStyle(fontFamily = AmmanFamily, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
)

@Composable
fun CandyTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCandy provides if (dark) DarkCandy else LightCandy) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            shapes = CandyShapes,
            typography = CandyTypography,
            content = content,
        )
    }
}
