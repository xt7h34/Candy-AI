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

internal fun aiDayLabel(ctx: Context, t: Long): String = when {
    DateUtils.isToday(t) -> ctx.getString(R.string.chat_today)
    DateUtils.isToday(t + DateUtils.DAY_IN_MILLIS) -> ctx.getString(R.string.chat_yesterday)
    else -> DateUtils.formatDateTime(ctx, t, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR)
}

internal fun sameDay(a: Long, b: Long): Boolean {
    val day = DateUtils.DAY_IN_MILLIS
    val tz = java.util.TimeZone.getDefault()
    return (a + tz.getOffset(a)) / day == (b + tz.getOffset(b)) / day
}


@Composable
internal fun AiTypingBubble(label: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.Bottom) {
        AiAvatar(28.dp)
        Spacer(Modifier.size(6.dp))
        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("• • •", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun AiBubble(m: AiMsg) {
    val big = 20.dp
    val small = 6.dp
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!m.mine) {
            AiAvatar(28.dp)
            Spacer(Modifier.size(6.dp))
        }
        if (m.mine) {
            Box(
                Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(topStart = big, topEnd = big, bottomEnd = small, bottomStart = big))
                    .background(Brush.linearGradient(listOf(cs.primary, lerp(cs.primary, cs.tertiary, 0.5f))))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
                    for (a in m.atts) AttachView(a)
                    if (m.text.isNotBlank()) Text(m.text, color = cs.onPrimary, style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else {
            Box(
                Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(topStart = big, topEnd = big, bottomEnd = big, bottomStart = small))
                    .background(cs.surfaceContainerHigh)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (a in m.atts) AttachView(a, large = true)
                    if (m.text.isNotBlank()) SelectionContainer {
                        AiMarkdown(m.text)
                    }
                }
            }
        }
    }
}

@Composable
internal fun MemChip(label: String, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

internal fun aiPlain(text: String): String = text.replace("**", "")

internal fun copyAiText(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("Candy", aiPlain(text)))
    // Android 13 and newer show their own "copied" note
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(ctx, R.string.ai_copied, Toast.LENGTH_SHORT).show()
}

@Composable
internal fun AiActionIcon(painter: Painter, description: Int, active: Boolean, flip: Boolean = false, size: Dp = 17.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter,
            contentDescription = stringResource(description),
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size).graphicsLayer { scaleY = if (flip) -1f else 1f },
        )
    }
}

/** Under each of Candy's replies: listen (Pro and Elite), copy, like, dislike, share, and on the last one, answer again. */
@Composable
internal fun AiActionRow(
    voice: Boolean,
    playing: Boolean,
    loading: Boolean,
    onVoice: () -> Unit,
    react: Int,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onResend: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 34.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (voice) {
            if (playing) {
                AiActionIcon(rememberVectorPainter(Icons.Rounded.Stop), R.string.ai_voice_stop, true, size = 22.dp, onClick = onVoice)
            } else {
                AiActionIcon(painterResource(R.drawable.ic_ear), R.string.ai_voice_play, false, onClick = { if (!loading) onVoice() })
            }
        }
        AiActionIcon(painterResource(R.drawable.ic_copy), R.string.ai_act_copy, false, onClick = onCopy)
        AiActionIcon(painterResource(R.drawable.ic_like), R.string.ai_act_like, react == 1, onClick = onLike)
        AiActionIcon(painterResource(R.drawable.ic_like), R.string.ai_act_dislike, react == -1, flip = true, onClick = onDislike)
        AiActionIcon(painterResource(R.drawable.ic_share), R.string.ai_act_share, false, onClick = onShare)
        if (onResend != null) AiActionIcon(painterResource(R.drawable.ic_resend), R.string.ai_act_resend, false, onClick = onResend)
    }
}

/** "Saved to memory: ..." under the reply that changed Candy's memory. */
@Composable
internal fun AiMemChips(m: AiMsg, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 34.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (t in m.memAdded) MemChip("🧠 " + stringResource(R.string.ai_mem_saved, t), onClick)
        for (t in m.memRemoved) MemChip("🧠 " + stringResource(R.string.ai_mem_removed, t), onClick)
    }
}

@Composable
internal fun AiDayChip(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

