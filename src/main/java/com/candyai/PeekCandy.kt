package com.candyai

import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.random.Random

// Everything below is measured on the original 973 x 1920 picture; the sprites are cut from it at these spots.
private const val IMG_W = 973f
private const val EYE_Y = 1385f                      // the height of her eyes in the picture
private const val CLOSED_X = 352f; private const val CLOSED_Y = 1322f   // both eyes, closed, with the glasses
private const val PUPIL_L_X = 443f; private const val PUPIL_L_Y = 1388f // pupil + glow of the eye on the left
private const val PUPIL_R_X = 620f; private const val PUPIL_R_Y = 1358f
private const val MAX_DX = 6f                        // how far the pupils may travel, in picture pixels
private const val MAX_DY = 2.5f

/**
 * Candy peeking over her pillow. She blinks now and then, and her pupils follow [gaze]
 * (-1..1 on both axes: x to the right, y downwards). With no gaze (null) she looks around on her own.
 * [eyeLine] is where her eyes should sit on the screen, as a fraction of its height.
 */
@Composable
fun PeekCandy(gaze: Offset?, modifier: Modifier = Modifier, eyeLine: Float = 0.38f) {
    val base = ImageBitmap.imageResource(R.drawable.candy_peek_base)
    val closed = ImageBitmap.imageResource(R.drawable.candy_peek_closed)
    val pupilL = ImageBitmap.imageResource(R.drawable.candy_peek_pupil_l)
    val pupilR = ImageBitmap.imageResource(R.drawable.candy_peek_pupil_r)

    // blink: quick close, a breath closed, slower open; sometimes twice in a row
    val lid = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2600, 6200))
            repeat(if (Random.nextFloat() < 0.2f) 2 else 1) { i ->
                if (i > 0) delay(120)
                lid.animateTo(1f, tween(70))
                delay(45)
                lid.animateTo(0f, tween(110))
            }
        }
    }

    // nobody to look at: wander
    var wander by remember { mutableStateOf(Offset(0f, 0.2f)) }
    LaunchedEffect(gaze == null) {
        while (gaze == null) {
            wander = Offset(Random.nextFloat() * 1.4f - 0.7f, Random.nextFloat() * 0.8f - 0.2f)
            delay(Random.nextLong(1800, 4200))
        }
    }
    val target = gaze ?: wander
    val gx by animateFloatAsState(target.x.coerceIn(-1f, 1f) * MAX_DX, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow), label = "gx")
    val gy by animateFloatAsState(target.y.coerceIn(-1f, 1f) * MAX_DY, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow), label = "gy")
    val closedA = lid.value

    Canvas(modifier) {
        val s = size.width / IMG_W
        // the picture is as wide as the screen; it slides up until her eyes are at [eyeLine]
        val oy = minOf(0f, eyeLine * size.height - EYE_Y * s)
        fun dst(img: ImageBitmap) = IntSize(ceil(img.width * s).toInt(), ceil(img.height * s).toInt())
        translate(0f, oy) {
            drawImage(base, dstSize = dst(base), filterQuality = FilterQuality.High)
            val pupilAlpha = 1f - closedA
            if (pupilAlpha > 0f) {
                translate((PUPIL_L_X + gx) * s, (PUPIL_L_Y + gy) * s) {
                    drawImage(pupilL, dstSize = dst(pupilL), alpha = pupilAlpha, filterQuality = FilterQuality.High)
                }
                translate((PUPIL_R_X + gx) * s, (PUPIL_R_Y + gy) * s) {
                    drawImage(pupilR, dstSize = dst(pupilR), alpha = pupilAlpha, filterQuality = FilterQuality.High)
                }
            }
            if (closedA > 0.001f) {
                translate(CLOSED_X * s, CLOSED_Y * s) {
                    drawImage(closed, dstSize = dst(closed), alpha = closedA, filterQuality = FilterQuality.High)
                }
            }
        }
    }
}
