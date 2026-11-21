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
    Rect("rect"), Donut("donut"), Crown("crown"), Bear("bear");

    companion object {
        fun of(key: String?): TabSkin = values().firstOrNull { it.key == key } ?: Rect
    }
}

/** The drawings are all made in a 100 x 100 square, then scaled to the size they are shown at. */
@Composable
fun SkinArt(skin: TabSkin, size: Dp, modifier: Modifier = Modifier, mirror: Boolean = false) {
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
        else -> Canvas(
            modifier.size(size).graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen   // the donut hole is cut out, so it needs its own layer
                scaleX = if (mirror) -1f else 1f
            },
        ) {
            when (skin) {
                TabSkin.Donut -> drawDonut()
                TabSkin.Crown -> drawCrown()
                else -> drawBear()
            }
        }
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

private fun DrawScope.drawCrown() {
    val s = size.minDimension / 100f
    val path = Path().apply {
        moveTo(16f * s, 80f * s)
        lineTo(8f * s, 34f * s)
        lineTo(31f * s, 54f * s)
        lineTo(50f * s, 20f * s)
        lineTo(69f * s, 54f * s)
        lineTo(92f * s, 34f * s)
        lineTo(84f * s, 80f * s)
        close()
    }
    drawPath(path, Color(0xFF1B1B1B), style = Stroke(10f * s, join = StrokeJoin.Round))
    drawPath(path, Color(0xFFFFD21F))
    // shading
    val shade = Path().apply {
        moveTo(22f * s, 72f * s); lineTo(18f * s, 50f * s); lineTo(34f * s, 64f * s); close()
        moveTo(78f * s, 72f * s); lineTo(82f * s, 50f * s); lineTo(66f * s, 64f * s); close()
    }
    drawPath(shade, Color(0xFFE5B800))
    drawLine(Color(0xFF1B1B1B), Offset(18f * s, 80f * s), Offset(82f * s, 80f * s), strokeWidth = 5f * s)
}

private fun DrawScope.drawBear() {
    val s = size.minDimension / 100f
    val fill = Color(0xFFF9ABCF)
    val line = Color(0xFFEE6BA6)
    val w = 5f * s
    fun body(stroke: Boolean) {
        fun circ(x: Float, y: Float, r: Float) {
            if (stroke) drawCircle(line, r * s, Offset(x * s, y * s), style = Stroke(w)) else drawCircle(fill, r * s, Offset(x * s, y * s))
        }
        // ears, arms, legs, body, head
        circ(31f, 17f, 9f); circ(69f, 17f, 9f)
        circ(24f, 60f, 10f); circ(76f, 58f, 10f)
        circ(35f, 86f, 11f); circ(65f, 86f, 11f)
        val tl = Offset(28f * s, 42f * s); val sz = Size(44f * s, 46f * s); val cr = CornerRadius(20f * s)
        if (stroke) drawRoundRect(line, tl, sz, cr, style = Stroke(w)) else drawRoundRect(fill, tl, sz, cr)
        circ(50f, 32f, 22f)
    }
    body(true)
    body(false)
    // face
    drawCircle(Color(0xFF5A2346), 2.6f * s, Offset(41f * s, 31f * s))
    drawCircle(Color(0xFF5A2346), 2.6f * s, Offset(59f * s, 31f * s))
    drawOval(line, Offset(44f * s, 36f * s), Size(12f * s, 8f * s))
    // shine
    drawOval(Color.White, Offset(38f * s, 14f * s), Size(14f * s, 6f * s))
    drawOval(Color.White, Offset(31f * s, 56f * s), Size(5f * s, 14f * s))
    drawOval(Color.White, Offset(28f * s, 84f * s), Size(10f * s, 5f * s))
}
