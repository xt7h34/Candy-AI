package com.candyai

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin
import kotlin.random.Random

// Everything below is measured on the original 973 x 1920 picture; the sprites are cut from it at these spots.
private const val IMG_W = 973f
private const val EYE_Y = 1385f                      // the height of her eyes in the picture
private const val CLOSED_X = 352f                    // the sprite with both eyes closed (and the glasses)
private const val CLOSED_Y = 1322f
private const val CLOSED_W = 400f
private const val IRIS_L_X = 430f; private const val IRIS_L_Y = 1377f   // the whole iris of the eye on the left
private const val IRIS_R_X = 605f; private const val IRIS_R_Y = 1345f
// How far the irises may travel, in picture pixels. The eye on the right sits close to its corner, so it goes less far to the left.
private const val L_LEFT = 9f
private const val L_RIGHT = 9f
private const val R_LEFT = 6f
private const val R_RIGHT = 9f
private const val UP = 2.5f
private const val DOWN = 3.5f
// Her gaze reaches the edge of that range before your finger reaches the edge of the screen
private const val GAIN_X = 1.5f
private const val GAIN_Y = 1.4f

// The eyelid is a slanted line through both eyes (her head is tilted). It sweeps down from above the eyes (k = -36)
// to below the closed lashes (k = +34); halfway (k = -1) the eyes are half closed.
private const val LINE_X0 = 466f
private const val LINE_Y0 = 1405f
private const val LINE_M = -0.1897f
private const val K_OPEN = -36f
private const val K_SHUT = 34f
private val LASH = Color(0xFF1E0E26)

/**
 * Candy peeking over her pillow. She blinks now and then (open, half closed, closed, half closed, open),
 * and her irises follow [gaze] (-1..1 on both axes: x to the right, y downwards).
 * With no gaze (null) she looks around on her own. [eyeLine] is where her eyes should sit on the screen,
 * as a fraction of its height.
 */
@Composable
fun PeekCandy(gaze: Offset?, modifier: Modifier = Modifier, eyeLine: Float = 0.38f) {
    val base = ImageBitmap.imageResource(R.drawable.candy_peek_base)       // the picture, with the irises taken out
    val closed = ImageBitmap.imageResource(R.drawable.candy_peek_closed)
    val irisL = ImageBitmap.imageResource(R.drawable.candy_peek_pupil_l)
    val irisR = ImageBitmap.imageResource(R.drawable.candy_peek_pupil_r)

    // 0 = open, 0.5 = half closed, 1 = closed. It stops at each of the three, so every stage is seen.
    val lid = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2600, 6200))
            repeat(if (Random.nextFloat() < 0.2f) 2 else 1) { i ->
                if (i > 0) delay(120)
                lid.animateTo(0.5f, tween(50, easing = FastOutLinearInEasing))
                delay(15)
                lid.animateTo(1f, tween(45, easing = FastOutLinearInEasing))
                delay(35)
                lid.animateTo(0.5f, tween(60, easing = LinearOutSlowInEasing))
                delay(15)
                lid.animateTo(0f, tween(70, easing = LinearOutSlowInEasing))
            }
        }
    }

    // nobody to look at: wander
    var wander by remember { mutableStateOf(Offset(0f, 0.2f)) }
    LaunchedEffect(gaze == null) {
        while (gaze == null) {
            wander = Offset(Random.nextFloat() * 1.8f - 0.9f, Random.nextFloat() * 1.2f - 0.5f)
            delay(Random.nextLong(1800, 4200))
        }
    }
    val target = gaze ?: wander
    val gx by animateFloatAsState((target.x * GAIN_X).coerceIn(-1f, 1f), spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow), label = "gx")
    val gy by animateFloatAsState((target.y * GAIN_Y).coerceIn(-1f, 1f), spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow), label = "gy")
    // -1..1 -> picture pixels, for each eye
    val dxL = if (gx < 0f) gx * L_LEFT else gx * L_RIGHT
    val dxR = if (gx < 0f) gx * R_LEFT else gx * R_RIGHT
    val dy = if (gy < 0f) gy * UP else gy * DOWN
    val p = lid.value

    Canvas(modifier) {
        val s = size.width / IMG_W
        // the picture is as wide as the screen; it slides up until her eyes are at [eyeLine]
        val oy = minOf(0f, eyeLine * size.height - EYE_Y * s)
        fun dst(img: ImageBitmap) = IntSize(ceil(img.width * s).toInt(), ceil(img.height * s).toInt())
        fun lineY(x: Float, k: Float) = LINE_Y0 + LINE_M * (x - LINE_X0) + k
        translate(0f, oy) {
            drawImage(base, dstSize = dst(base), filterQuality = FilterQuality.High)
            translate((IRIS_L_X + dxL) * s, (IRIS_L_Y + dy) * s) {
                drawImage(irisL, dstSize = dst(irisL), filterQuality = FilterQuality.High)
            }
            translate((IRIS_R_X + dxR) * s, (IRIS_R_Y + dy) * s) {
                drawImage(irisR, dstSize = dst(irisR), filterQuality = FilterQuality.High)
            }

            if (p > 0.001f) {
                if (p >= 0.98f) {
                    translate(CLOSED_X * s, CLOSED_Y * s) {
                        drawImage(closed, dstSize = dst(closed), filterQuality = FilterQuality.High)
                    }
                } else {
                    // the lid: the closed eyes show above the slanted line, the open ones below it
                    val k = K_OPEN + (K_SHUT - K_OPEN) * p
                    val xa = CLOSED_X
                    val xb = CLOSED_X + CLOSED_W
                    val lid = Path().apply {
                        moveTo(xa * s, (CLOSED_Y - 6f) * s)
                        lineTo(xb * s, (CLOSED_Y - 6f) * s)
                        lineTo(xb * s, lineY(xb, k) * s)
                        lineTo(xa * s, lineY(xa, k) * s)
                        close()
                    }
                    clipPath(lid) {
                        translate(CLOSED_X * s, CLOSED_Y * s) {
                            drawImage(closed, dstSize = dst(closed), filterQuality = FilterQuality.High)
                        }
                    }
                    // the lashes ride on the edge of the lid, thicker in the middle of each eye
                    val fade = ((0.98f - p) / 0.12f).coerceIn(0f, 1f)
                    for ((xs, xe) in listOf(412f to 500f, 602f to 708f)) {
                        val n = 8
                        for (i in 0 until n) {
                            val xi = xs + (xe - xs) * i / n
                            val xj = xs + (xe - xs) * (i + 1) / n
                            val t = (i + 0.5f) / n
                            drawLine(
                                LASH.copy(alpha = fade),
                                Offset(xi * s, lineY(xi, k) * s),
                                Offset(xj * s, lineY(xj, k) * s),
                                strokeWidth = (1f + 4f * sin(PI.toFloat() * t)) * s,
                                cap = StrokeCap.Round,
                            )
                        }
                    }
                }
            }
        }
    }
}
