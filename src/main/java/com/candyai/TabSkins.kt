package com.candyai

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** What Candy looks like while she is tucked away (the shape the person picks in Settings). */
enum class TabSkin(val key: String) {
    Rect("rect"), Donut("donut"), Crown("crown"), Bear("bear"), Faces("faces");

    companion object {
        fun of(key: String?): TabSkin = values().firstOrNull { it.key == key } ?: Rect
    }
}

/**
 * How Candy feels, from how much the person talked to her: 0 sad (nothing for 3 days or more), 1 neutral (a day or more of quiet),
 * 2 happy (talked today), 3 very happy (6 or more messages in the last 24 hours).
 */
object Mood {
    private const val DAY = 24L * 60 * 60 * 1000
    fun level(now: Long = System.currentTimeMillis()): Int {
        val last = Store.lastSend
        val today = Store.sendsSince(now - DAY)
        return when {
            today >= 6 -> 3
            today >= 1 -> 2
            last == 0L || now - last < 3 * DAY -> 1
            else -> 0
        }
    }
}

private val FACES = intArrayOf(R.drawable.skin_face_0, R.drawable.skin_face_1, R.drawable.skin_face_2, R.drawable.skin_face_3)

/** The drawings are all made in a 100 x 100 square, then scaled to the size they are shown at. */
@Composable
fun SkinArt(skin: TabSkin, size: Dp, modifier: Modifier = Modifier, mirror: Boolean = false, mood: Int = 2) {
    when (skin) {
        TabSkin.Rect -> {
            // the flat tab with her picture, as a small preview
            Box(
                modifier.size(size * 0.6f, size).clip(RoundedCornerShape(topStart = size * 0.3f, bottomStart = size * 0.3f))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.candy_ai_avatar), null, contentScale = ContentScale.Crop, modifier = Modifier.size(size * 0.4f).clip(CircleShape))
            }
        }
        TabSkin.Donut -> Canvas(
            modifier.size(size).graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen   // the donut hole is cut out, so it needs its own layer
                scaleX = if (mirror) -1f else 1f
            },
        ) { drawDonut() }
        TabSkin.Faces -> androidx.compose.animation.Crossfade(mood.coerceIn(0, 3), modifier.size(size), label = "mood") { m ->
            Image(painterResource(FACES[m]), null, contentScale = ContentScale.Fit, modifier = Modifier.size(size))
        }
        else -> Image(
            painterResource(if (skin == TabSkin.Crown) R.drawable.skin_crown else R.drawable.skin_bear),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.size(size).graphicsLayer { scaleX = if (mirror) -1f else 1f },
        )
    }
}

private fun DrawScope.drawDonut() {
    val s = size.minDimension / 100f
    val ink = Color(0xFF5A2346)
    // dough
    drawCircle(Color(0xFFEBB874), radius = 44f * s, center = Offset(50f * s, 52f * s))
    // glaze
    drawCircle(Color(0xFFFF73A6), radius = 40f * s, center = Offset(50f * s, 47f * s))
    // the hole
    drawOval(Color.Transparent, topLeft = Offset(36f * s, 37f * s), size = Size(28f * s, 22f * s), blendMode = BlendMode.Clear)
    drawOval(ink, topLeft = Offset(36f * s, 37f * s), size = Size(28f * s, 22f * s), style = Stroke(3f * s))
    drawCircle(ink, radius = 44f * s, center = Offset(50f * s, 52f * s), style = Stroke(3.5f * s))
    // sprinkles
    val sprinkles = listOf(Triple(24f, 36f, 30f), Triple(40f, 20f, -20f), Triple(62f, 20f, 40f), Triple(76f, 36f, -35f), Triple(30f, 62f, 70f), Triple(72f, 62f, 15f), Triple(52f, 74f, -50f))
    for ((x, y, deg) in sprinkles) {
        rotate(deg, pivot = Offset(x * s, y * s)) {
            drawRoundRect(Color.White, topLeft = Offset((x - 5f) * s, (y - 1.8f) * s), size = Size(10f * s, 3.6f * s), cornerRadius = CornerRadius(1.8f * s))
        }
    }
}
