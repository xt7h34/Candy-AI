package com.candyai

import androidx.compose.foundation.layout.statusBarsPadding
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// Where the eye and mouth layers sit on the 1254x1254 art, as fractions of its side.
internal const val ART = 1254f
internal const val EYES_X = 560f / ART
internal const val EYES_Y = 270f / ART
internal const val EYES_W = 370f / ART
internal const val EYES_H = 180f / ART
internal const val MOUTH_X = 680f / ART
internal const val MOUTH_Y = 448f / ART
internal const val MOUTH_W = 120f / ART
internal const val MOUTH_H = 82f / ART

internal enum class Eyes(val res: Int?) {
    Open(null), Half(R.drawable.candy_eyes_half), Closed(R.drawable.candy_eyes_closed)
}

internal enum class Mouth(val res: Int?) {
    Closed(null), Oo(R.drawable.candy_mouth_oo), Slight(R.drawable.candy_mouth_slight), Ah(R.drawable.candy_mouth_ah), Bored(R.drawable.candy_mouth_bored)
}

internal enum class Mood { Awake, Bored, Asleep }

/**
 * Candy and her café as the chat's wallpaper, pinned to the top and fading into the chat.
 * Plus: a still picture. Pro and Elite: she breathes and blinks, her mouth moves while she answers
 * ([talkTick] goes up on every reply, [talkMs] is how long she keeps talking), and if nobody touches the screen
 * ([lastActive] is the time of the last touch) she gets bored: half-closed eyes, a sigh, a yawn, and finally she falls asleep.
 * Any touch wakes her up.
 */
@Composable
internal fun CandyStage(animated: Boolean, talkTick: Int, talkMs: Long, lastActive: Long) {
    var mood by remember { mutableStateOf(Mood.Awake) }
    var blinkEyes by remember { mutableStateOf<Eyes?>(null) }
    var actEyes by remember { mutableStateOf<Eyes?>(null) }
    var talkMouth by remember { mutableStateOf<Mouth?>(null) }
    var actMouth by remember { mutableStateOf<Mouth?>(null) }
    val bump = remember { Animatable(1f) }
    val cs = MaterialTheme.colorScheme
    val bg = cs.background

    val eyes = actEyes ?: blinkEyes ?: when (mood) {
        Mood.Awake -> Eyes.Open
        Mood.Bored -> Eyes.Half
        Mood.Asleep -> Eyes.Closed
    }
    val mouth = talkMouth ?: actMouth ?: if (mood == Mood.Bored) Mouth.Bored else Mouth.Closed

    val transition = rememberInfiniteTransition(label = "candy-idle")
    val breathe: State<Float> = if (animated) {
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.014f,
            animationSpec = infiniteRepeatable(
                tween(if (mood == Mood.Awake) 3800 else if (mood == Mood.Bored) 4800 else 6200, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
            ),
            label = "candy-breathe-scale",
        )
    } else {
        remember { mutableStateOf(1f) }
    }
    val zAlpha: State<Float> = if (animated) {
        transition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
            label = "candy-z",
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    if (animated) {
        // blinking (slow and sleepy when she is bored, none when she sleeps)
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
        // her mouth moves while she answers
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
    }

    // awake -> bored -> sigh -> yawn -> sigh -> asleep. Starts over on every touch.
    LaunchedEffect(lastActive, animated) {
        suspend fun sigh() {
            actEyes = Eyes.Closed
            actMouth = Mouth.Oo
            bump.animateTo(1.03f, tween(800))
            bump.animateTo(1f, tween(1000))
            actEyes = null
            actMouth = null
        }

        suspend fun yawn() {
            actEyes = Eyes.Closed
            actMouth = Mouth.Slight
            delay(300)
            actMouth = Mouth.Ah
            bump.animateTo(1.025f, tween(900))
            delay(500)
            bump.animateTo(1f, tween(700))
            actMouth = Mouth.Slight
            delay(250)
            actEyes = null
            actMouth = null
        }

        val wasAsleep = mood == Mood.Asleep
        mood = Mood.Awake
        actEyes = null
        actMouth = null
        bump.snapTo(1f)
        if (!animated) return@LaunchedEffect
        if (wasAsleep) {
            actEyes = Eyes.Half
            delay(220)
            actEyes = null
        }
        delay(25_000L)
        mood = Mood.Bored
        delay(8_000L)
        sigh()
        delay(12_000L)
        yawn()
        delay(10_000L)
        sigh()
        delay(15_000L)
        mood = Mood.Asleep
    }

    // the art is not mirrored for right-to-left languages, so its pieces line up
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(Modifier.fillMaxSize().background(bg).clipToBounds()) {
            val side = maxWidth * 1.15f
            val left = (maxWidth / 2 - side * 0.56f).coerceIn(maxWidth - side, 0.dp)
            // placed exactly at x = left: requiredSize would re-centre the picture because it is wider than the screen
            Box(
                Modifier.layout { measurable, constraints ->
                    val s = side.roundToPx()
                    val p = measurable.measure(Constraints.fixed(s, s))
                    layout(constraints.maxWidth, s) { p.place(left.roundToPx(), 0) }
                },
            ) {
                Image(
                    painter = painterResource(R.drawable.candy_stage_bg),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        val k = breathe.value * bump.value
                        scaleX = k
                        scaleY = k
                        transformOrigin = TransformOrigin(0.55f, 1f)
                    },
                ) {
                    Image(
                        painter = painterResource(R.drawable.candy_stage_body),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize(),
                    )
                    eyes.res?.let { r ->
                        Image(
                            painter = painterResource(r),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.offset(x = side * EYES_X, y = side * EYES_Y).requiredSize(side * EYES_W, side * EYES_H),
                        )
                    }
                    mouth.res?.let { r ->
                        Image(
                            painter = painterResource(r),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.offset(x = side * MOUTH_X, y = side * MOUTH_Y).requiredSize(side * MOUTH_W, side * MOUTH_H),
                        )
                    }
                    if (mood == Mood.Asleep) {
                        Text(
                            "z z Z",
                            color = cs.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = (side.value * 0.04f).sp,
                            modifier = Modifier.offset(x = side * 0.70f, y = side * 0.16f).graphicsLayer { alpha = zAlpha.value },
                        )
                    }
                }
                // the bottom of the picture melts into the chat
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(side * 0.38f)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, bg))),
                )
            }
        }
    }
}

@Composable
internal fun AiAvatar(size: Dp) {
    Image(
        painter = painterResource(R.drawable.candy_ai_avatar),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(size).clip(CircleShape),
    )
}

@Composable
internal fun AiBadge() {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            stringResource(R.string.ai_badge),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
        )
    }
}

