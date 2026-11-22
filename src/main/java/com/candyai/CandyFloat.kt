package com.candyai

import android.content.Context
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** The time of the last touch anywhere in the app (MainActivity reports it). Candy gets bored when it stays quiet. */
object CandyIdle {
    var last by mutableStateOf(System.currentTimeMillis())
        private set

    fun touched() {
        val now = System.currentTimeMillis()
        if (now - last > 1000L) last = now
    }
}

/** Where Candy sits on the screen edge, and whether she is tucked away. Kept on the phone. */
private object CandyFloatPrefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("candy_float", Context.MODE_PRIVATE)
    fun edge(ctx: Context): Int = p(ctx).getInt("edge", 1)
    fun y(ctx: Context): Float = p(ctx).getFloat("y", 0.55f)
    fun hidden(ctx: Context): Boolean = p(ctx).getBoolean("hidden", false)
    fun save(ctx: Context, edge: Int, y: Float, hidden: Boolean) {
        p(ctx).edit().putInt("edge", edge).putFloat("y", y).putBoolean("hidden", hidden).apply()
    }
}

// The peeking art (725x1000): the right border of the picture is the screen edge she hides behind.
// The whole picture is shown (hair, face, shoulder and the hand on the edge); the top and bottom fade out.
private const val ART_W = 725f
private const val ART_H = 1000f
// eyes and mouth pieces, placed on the art (art pixels)
private const val P_EYES_X = 390f
private const val P_EYES_Y = 285f
private const val P_EYES_W = 337f
private const val P_EYES_H = 170f
private const val P_MOUTH_X = 580f
private const val P_MOUTH_Y = 462f
private const val P_MOUTH_W = 105f
private const val P_MOUTH_H = 83f

private fun peekEyesRes(e: Eyes): Int? = when (e) {
    Eyes.Open -> null
    Eyes.Half -> R.drawable.peek_eyes_half
    Eyes.Closed -> R.drawable.peek_eyes_closed
}

private fun peekMouthRes(m: Mouth): Int? = when (m) {
    Mouth.Closed -> null
    Mouth.Slight -> R.drawable.peek_mouth_slight
    Mouth.Oo -> R.drawable.peek_mouth_oo
    Mouth.Ah -> R.drawable.peek_mouth_ah
    Mouth.Bored -> R.drawable.peek_mouth_bored
}

/**
 * Candy lives on the edge of the screen on the home screen.
 * Tap: the ask panel opens over the screen. Long press: a new full chat. Drag her to move her, or push her into the edge to tuck her away.
 */
@Composable
fun CandyFloatingHost(
    visible: Boolean,
    bottomInset: Dp,
    onOpenFull: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var edge by remember { mutableStateOf(CandyFloatPrefs.edge(ctx)) }
    var yFrac by remember { mutableStateOf(CandyFloatPrefs.y(ctx)) }
    var hidden by remember { mutableStateOf(CandyFloatPrefs.hidden(ctx)) }
    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }
    var panel by remember { mutableStateOf(false) }
    var talkTick by remember { mutableStateOf(0) }
    var talkMs by remember { mutableStateOf(0L) }
    val reduceMotion = remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }

    LaunchedEffect(visible) { if (!visible) panel = false }

    if (!visible) return

    BoxWithConstraints(modifier) {
        val w = 104.dp
        val h = 144.dp
        val wPx = with(density) { w.toPx() }
        val hPx = with(density) { h.toPx() }
        val maxWPx = with(density) { maxWidth.toPx() }
        val maxHPx = with(density) { maxHeight.toPx() }
        val bottomPx = with(density) { bottomInset.toPx() }
        val yMax = (maxHPx - hPx - bottomPx).coerceAtLeast(1f)
        val baseX = if (edge == 1) maxWPx - wPx else 0f
        val baseY = (yFrac * yMax).coerceIn(0f, yMax)

        // the figure is positioned in screen coordinates, so right-to-left must not mirror it
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        if (hidden) {
            // only a small tab stays on the edge; tap it to bring her back
            val tabW = 28.dp
            val tabH = 56.dp
            val tabPx = with(density) { tabW.toPx() }
            val tabHPx = with(density) { tabH.toPx() }
            Box(
                Modifier
                    .offset { IntOffset(if (edge == 1) (maxWPx - tabPx).roundToInt() else 0, (baseY + hPx / 2f - tabHPx / 2f).roundToInt()) }
                    .size(tabW, tabH)
                    .clip(if (edge == 1) RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp) else RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f))
                    .clickable {
                        hidden = false
                        CandyFloatPrefs.save(ctx, edge, yFrac, false)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.candy_ai_avatar),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(22.dp).clip(CircleShape),
                )
            }
        } else {
            Box(
                Modifier
                    .offset { IntOffset((baseX + dragX).roundToInt(), (baseY + dragY).coerceIn(0f, yMax).roundToInt()) }
                    .size(w, h)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { panel = true },
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onOpenFull(null)
                            },
                        )
                    }
                    .pointerInput(edge, yFrac, maxWPx, yMax) {
                        fun finish() {
                            val left = baseX + dragX
                            val pushedIn = if (edge == 1) dragX > wPx * 0.4f else dragX < -wPx * 0.4f
                            val newEdge = if (left + wPx / 2f < maxWPx / 2f) 0 else 1
                            val newY = ((baseY + dragY).coerceIn(0f, yMax) / yMax).coerceIn(0f, 1f)
                            if (pushedIn) {
                                // dragged into the edge she is on: she tucks away
                                hidden = true
                                dragX = 0f
                                dragY = 0f
                                CandyFloatPrefs.save(ctx, edge, newY, true)
                                yFrac = newY
                                return
                            }
                            val newBaseX = if (newEdge == 1) maxWPx - wPx else 0f
                            val from = left - newBaseX
                            edge = newEdge
                            yFrac = newY
                            dragY = 0f
                            dragX = from
                            CandyFloatPrefs.save(ctx, newEdge, newY, false)
                            scope.launch { animate(from, 0f, animationSpec = tween(220)) { v, _ -> dragX = v } }
                        }
                        detectDragGestures(
                            onDragEnd = { finish() },
                            onDragCancel = { finish() },
                            onDrag = { change, amount ->
                                change.consume()
                                dragX += amount.x
                                dragY += amount.y
                            },
                        )
                    },
            ) {
                CandyPeek(w, h, !reduceMotion, talkTick, talkMs, mirror = edge == 0)
            }
        }
        }

        if (panel) {
            EdgePanel(
                onClose = { panel = false },
                onOpenFull = { id ->
                    panel = false
                    onOpenFull(id)
                },
                onReply = { len ->
                    talkMs = (len * 45L).coerceIn(1200L, 5000L)
                    talkTick++
                },
            )
        }
    }
}

/** Candy peeking from behind the screen edge (mirrored on the left edge): she blinks and breathes, her mouth moves when she answers, and she gets bored and sleeps. */
@Composable
internal fun CandyPeek(width: Dp, height: Dp, animated: Boolean, talkTick: Int, talkMs: Long, mirror: Boolean) {
    var mood by remember { mutableStateOf(Mood.Awake) }
    var blinkEyes by remember { mutableStateOf<Eyes?>(null) }
    var actEyes by remember { mutableStateOf<Eyes?>(null) }
    var talkMouth by remember { mutableStateOf<Mouth?>(null) }
    var actMouth by remember { mutableStateOf<Mouth?>(null) }
    val lastActive = CandyIdle.last
    val cs = MaterialTheme.colorScheme

    val eyes = actEyes ?: blinkEyes ?: when (mood) {
        Mood.Awake -> Eyes.Open
        Mood.Bored -> Eyes.Half
        Mood.Asleep -> Eyes.Closed
    }
    val mouth = talkMouth ?: actMouth ?: if (mood == Mood.Bored) Mouth.Bored else Mouth.Closed

    val transition = rememberInfiniteTransition(label = "candy-peek")
    val breathe = if (animated) {
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.02f,
            animationSpec = infiniteRepeatable(
                tween(if (mood == Mood.Awake) 3200 else 5200, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
            ),
            label = "candy-peek-breathe",
        ).value
    } else {
        1f
    }

    if (animated) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(Random.nextLong(2500L, 6500L))
                when (mood) {
                    Mood.Awake -> {
                        blinkEyes = Eyes.Half
                        delay(60)
                        blinkEyes = Eyes.Closed
                        delay(90)
                        blinkEyes = Eyes.Half
                        delay(60)
                        blinkEyes = null
                    }
                    Mood.Bored -> {
                        blinkEyes = Eyes.Closed
                        delay(260)
                        blinkEyes = null
                    }
                    Mood.Asleep -> Unit
                }
            }
        }
        LaunchedEffect(talkTick) {
            if (talkTick > 0) {
                val shapes = listOf(Mouth.Slight, Mouth.Oo, Mouth.Ah, Mouth.Slight, Mouth.Closed, Mouth.Ah, Mouth.Oo)
                val end = System.currentTimeMillis() + talkMs
                var i = 0
                while (System.currentTimeMillis() < end) {
                    talkMouth = shapes[i % shapes.size]
                    i++
                    delay(130)
                }
                talkMouth = null
            }
        }
        // awake -> bored -> yawn -> asleep, starting over on every touch anywhere in the app
        LaunchedEffect(lastActive) {
            val wasAsleep = mood == Mood.Asleep
            mood = Mood.Awake
            actEyes = null
            actMouth = null
            if (wasAsleep) {
                actEyes = Eyes.Half
                delay(220)
                actEyes = null
            }
            delay(25_000L)
            mood = Mood.Bored
            delay(15_000L)
            actEyes = Eyes.Closed
            actMouth = Mouth.Slight
            delay(300)
            actMouth = Mouth.Ah
            delay(1200)
            actMouth = Mouth.Slight
            delay(250)
            actEyes = null
            actMouth = null
            delay(20_000L)
            mood = Mood.Asleep
        }
    }

    val sc = width / ART_W
    Box(
        Modifier
            .size(width, height)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // her hair and body fade out at the top and bottom instead of ending in a hard line
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.05f to Color.Black,
                        0.93f to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            .clipToBounds(),
    ) {
        // the art is positioned in screen coordinates: right-to-left must not mirror it, the left edge mirrors it on purpose
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                Modifier
                    .requiredSize(width, sc * ART_H)
                    .align(Alignment.TopStart)
                    .graphicsLayer { scaleX = if (mirror) -1f else 1f },
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = breathe
                            scaleY = breathe
                            // the wall side stays put, she breathes away from it
                            transformOrigin = TransformOrigin(1f, 1f)
                        },
                ) {
                    Image(
                        painter = painterResource(R.drawable.peek_body),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize(),
                    )
                    peekEyesRes(eyes)?.let { r ->
                        Image(
                            painter = painterResource(r),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.offset(x = sc * P_EYES_X, y = sc * P_EYES_Y).requiredSize(sc * P_EYES_W, sc * P_EYES_H),
                        )
                    }
                    peekMouthRes(mouth)?.let { r ->
                        Image(
                            painter = painterResource(r),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.offset(x = sc * P_MOUTH_X, y = sc * P_MOUTH_Y).requiredSize(sc * P_MOUTH_W, sc * P_MOUTH_H),
                        )
                    }
                }
            }
        }
        if (mood == Mood.Asleep) {
            Image(
                painter = painterResource(R.drawable.z_sleep),
                contentDescription = null,
                modifier = Modifier
                    .align(if (mirror) Alignment.TopEnd else Alignment.TopStart)
                    .padding(top = 6.dp, start = 26.dp, end = 26.dp)
                    .requiredSize(16.dp, 27.dp),
            )
        }
    }
}

