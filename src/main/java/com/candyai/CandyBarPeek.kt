package com.candyai

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlin.random.Random

// where the flat "table edge" of the picture is, as a fraction of its height (the fingertips hang a little below it)
private const val BAR_EDGE = 0.953f

/**
 * Candy peeking over the top edge of the Candy See capsule, as if the capsule were a table.
 * Four pictures with the same frame, only the eyes differ, so swapping them never makes her jump:
 * open (waiting), side (glancing around while she thinks), half (getting sleepy), closed (blink / asleep).
 *
 * [busy] = a reply is on its way, [listening] = the mic is open, [activeStamp] changes on every touch or typing,
 * which wakes her up. With nothing happening she gets sleepy after ~25 s and sleeps after ~45 s (with the Z).
 */
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
    val open = ImageBitmap.imageResource(R.drawable.candy_bar_open)
    val side = ImageBitmap.imageResource(R.drawable.candy_bar_side)
    val half = ImageBitmap.imageResource(R.drawable.candy_bar_half)
    val closed = ImageBitmap.imageResource(R.drawable.candy_bar_closed)

    var sleepy by remember { mutableStateOf(0) }      // 0 awake, 1 sleepy, 2 asleep
    var blink by remember { mutableStateOf(false) }
    var glance by remember { mutableStateOf(false) }

    // any touch, typing, mic or reply wakes her; then the sleepy timer starts again
    LaunchedEffect(activeStamp, busy, listening) {
        sleepy = 0
        if (busy || listening) return@LaunchedEffect
        delay(25_000)
        sleepy = 1
        delay(20_000)
        sleepy = 2
    }
    // blinking (slow when sleepy, none when asleep)
    LaunchedEffect(animated, sleepy) {
        blink = false
        if (!animated || sleepy == 2) return@LaunchedEffect
        while (true) {
            delay(if (sleepy == 1) Random.nextLong(5000, 8000) else Random.nextLong(2500, 5500))
            blink = true
            delay(if (sleepy == 1) 260L else 130L)
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

    val img = when {
        sleepy == 2 || blink -> closed
        sleepy == 1 -> half
        glance -> side
        else -> open
    }

    val transition = rememberInfiniteTransition(label = "candy-bar")
    val breathe: State<Float> = if (animated) {
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.012f,
            animationSpec = infiniteRepeatable(tween(if (sleepy == 2) 5200 else 3400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
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
    // she sits up a little while you talk to her
    val lift by animateDpAsState(if (listening) (-4).dp else 0.dp, tween(180), label = "candy-bar-lift")

    val w = 168.dp
    val h = w * (433f / 720f)
    Box(
        modifier
            .zIndex(1f)   // above the capsule, so her fingertips lie on its edge
            .size(w, h)
            .offset(y = h * (1f - BAR_EDGE) + lift)   // the flat edge of the picture sits exactly on the capsule's top edge
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPoke() },
    ) {
        Image(
            bitmap = img,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            filterQuality = FilterQuality.Medium,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = breathe.value
                scaleY = breathe.value
                transformOrigin = TransformOrigin(0.5f, BAR_EDGE)
            },
        )
        if (sleepy == 2) {
            Image(
                painter = painterResource(R.drawable.z_sleep),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 12.dp)
                    .requiredSize(16.dp, 27.dp)
                    .graphicsLayer { alpha = zAlpha.value },
            )
        }
    }
}
