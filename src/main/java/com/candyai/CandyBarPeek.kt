package com.candyai

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Candy on top of the Candy See capsule, as if the capsule were a table.
 *
 * Two families of pictures. Inside each family only the EYES change (open / side / half / closed), so those swaps never jump:
 *  - PEEK: she peeks over the edge, hands on it (alert: waiting, listening, thinking).
 *  - LEAN: she rests on her folded arms (relaxed: sleepy, asleep).
 * Between the two she goes through two bridge pictures (RISE_1, RISE_2: hands on the edge -> chin on her forearms),
 * one hard cut every ~100-150 ms, like limited animation. Never a cross-fade between two different poses (that shows two faces).
 *
 * [busy] = a reply is on its way, [listening] = the mic is open, [activeStamp] changes on every touch or typing (wakes her).
 * Idle: 25 s sleepy (half-closed eyes) -> 40 s she settles onto her arms -> 65 s she sleeps (eyes closed, the Z floats).
 *
 * Layout: this composable takes NO height (it grows upward over the area above the capsule), so it never moves anything,
 * and the flat "table edge" of every picture sits exactly on the capsule's top edge.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun CandyBarPeek(
    busy: Boolean,
    listening: Boolean,
    activeStamp: Long,
    onPoke: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val animated = remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f }

    // all pictures are small, so they are decoded once (about 5 MB together) and never hitch on a swap
    val peek = listOf(PEEK_OPEN, PEEK_SIDE, PEEK_HALF, PEEK_CLOSED)
    val lean = listOf(LEAN_OPEN, LEAN_SIDE, LEAN_HALF, LEAN_CLOSED)
    val peekBmp = peek.map { ImageBitmap.imageResource(it.res) }
    val leanBmp = lean.map { ImageBitmap.imageResource(it.res) }
    val rise1 = ImageBitmap.imageResource(RISE_1.res)
    val rise2 = ImageBitmap.imageResource(RISE_2.res)

    var stage by remember { mutableStateOf(0) }       // 0 alert, 1 sleepy, 2 settled on her arms, 3 asleep
    var blink by remember { mutableStateOf(false) }
    var glance by remember { mutableStateOf(false) }
    var level by remember { mutableStateOf(0) }       // 0 peek, 1 rise 1, 2 rise 2, 3 lean

    LaunchedEffect(activeStamp, busy, listening) {
        stage = 0
        if (busy || listening) return@LaunchedEffect
        delay(25_000)
        stage = 1
        delay(15_000)
        stage = 2
        delay(25_000)
        stage = 3
    }
    // walk the ladder one picture at a time (quicker when she wakes up)
    val targetLevel = if (stage >= 2) 3 else 0
    LaunchedEffect(targetLevel) {
        while (level != targetLevel) {
            val goingUp = targetLevel < level
            level += if (targetLevel > level) 1 else -1
            if (animated) delay(if (goingUp) 90L else 140L)
        }
    }
    // blinking (slow when sleepy, none when asleep)
    LaunchedEffect(animated, stage) {
        blink = false
        if (!animated || stage == 3) return@LaunchedEffect
        while (true) {
            delay(if (stage >= 1) Random.nextLong(5000, 8000) else Random.nextLong(2500, 5500))
            blink = true
            delay(if (stage >= 1) 260L else 130L)
            blink = false
        }
    }
    // while she is thinking she looks around
    LaunchedEffect(animated, busy) {
        glance = false
        if (!animated || !busy) return@LaunchedEffect
        while (true) {
            delay(1300)
            glance = true
            delay(1100)
            glance = false
        }
    }

    // eyes: 0 open, 1 side, 2 half, 3 closed
    val eyes = when {
        stage == 3 || blink -> 3
        stage >= 1 -> 2
        glance -> 1
        else -> 0
    }
    val pose: Pose
    val bmp: ImageBitmap
    when (level) {
        0 -> { pose = peek[eyes]; bmp = peekBmp[eyes] }
        1 -> { pose = RISE_1; bmp = rise1 }
        2 -> { pose = RISE_2; bmp = rise2 }
        else -> { pose = lean[eyes]; bmp = leanBmp[eyes] }
    }

    val transition = rememberInfiniteTransition(label = "candy-bar")
    val breathe: State<Float> = if (animated) {
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.010f,
            animationSpec = infiniteRepeatable(tween(if (stage == 3) 5200 else 3400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "candy-bar-breathe",
        )
    } else {
        remember { mutableStateOf(1f) }
    }
    val zAlpha: State<Float> = if (animated) {
        transition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
            label = "candy-bar-z",
        )
    } else {
        remember { mutableStateOf(0.8f) }
    }

    Box(
        modifier
            .zIndex(1f)   // above the capsule, so her fingertips and arms lie on its edge
            .layout { measurable, _ ->
                val wPx = AREA_W.roundToPx()
                val hPx = AREA_H.roundToPx()
                val p = measurable.measure(Constraints.fixed(wPx, hPx))
                // takes no height: grows upward from the capsule's top edge (1 dp of overlap so no hairline shows)
                layout(wPx, 0) { p.place(0, -hPx + 1.dp.roundToPx()) }
            },
    ) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            filterQuality = FilterQuality.Medium,
            modifier = Modifier
                .offset(x = ANCHOR_X.dp - pose.ax.dp, y = AREA_H - pose.ay.dp)
                .size(pose.w.dp, pose.h.dp)
                .graphicsLayer {
                    scaleX = breathe.value
                    scaleY = breathe.value
                    transformOrigin = TransformOrigin(0.5f, 1f)
                },
        )
        if (stage == 3 && level == 3) {
            Image(
                painter = painterResource(R.drawable.z_sleep),
                contentDescription = null,
                modifier = Modifier
                    .offset(x = (ANCHOR_X + 40f).dp, y = 8.dp)
                    .requiredSize(16.dp, 27.dp)
                    .graphicsLayer { alpha = zAlpha.value },
            )
        }
    }
}

/** A picture: resource, size in dp, and where her glasses (ax) and the flat table edge (ay) are inside it, in dp. */
private class Pose(val res: Int, val w: Float, val h: Float, val ax: Float, val ay: Float)

private val AREA_W = 200.dp
private val AREA_H = 150.dp
/** x of her glasses inside the 200 dp area; every picture is placed so its glasses land here */
private const val ANCHOR_X = 109.6f

private val PEEK_OPEN = Pose(R.drawable.candy_bar_open, 94.3f, 57.0f, 55.6f, 54.3f)
private val PEEK_SIDE = Pose(R.drawable.candy_bar_side, 94.3f, 57.0f, 55.5f, 54.3f)
private val PEEK_HALF = Pose(R.drawable.candy_bar_half, 94.3f, 56.7f, 55.5f, 54.3f)
private val PEEK_CLOSED = Pose(R.drawable.candy_bar_closed, 94.3f, 56.7f, 55.5f, 54.2f)
private val RISE_1 = Pose(R.drawable.candy_bar_p1, 109.7f, 67.3f, 61.4f, 67.1f)
private val RISE_2 = Pose(R.drawable.candy_bar_p2, 108.0f, 70.0f, 59.7f, 69.5f)
private val LEAN_OPEN = Pose(R.drawable.candy_bar_lean_awake, 162.7f, 114.0f, 91.2f, 113.4f)
private val LEAN_SIDE = Pose(R.drawable.candy_bar_lean_side, 162.7f, 114.7f, 91.1f, 113.7f)
private val LEAN_HALF = Pose(R.drawable.candy_bar_lean_half, 163.0f, 114.0f, 91.2f, 113.6f)
private val LEAN_CLOSED = Pose(R.drawable.candy_bar_lean_closed, 163.0f, 114.3f, 91.2f, 113.6f)
