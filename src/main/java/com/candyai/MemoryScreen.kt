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
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import java.io.IOException
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject


private fun memError(ctx: Context, e: Throwable): String {
    if (e is ApiException) {
        when (e.code) {
            "dup" -> return ctx.getString(R.string.mem_err_dup)
            "sensitive" -> return ctx.getString(R.string.mem_err_sensitive)
            "too_long" -> return ctx.getString(R.string.mem_err_too_long)
        }
    }
    return errorMessage(ctx, e)
}

/** What Candy remembers about you: see it, add to it, delete from it, or turn it off. */
@Composable
fun MemoryScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = remember { mutableStateListOf<MemItem>() }
    var on by remember { mutableStateOf(true) }
    var max by remember { mutableStateOf(50) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    var confirmAll by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    fun load() {
        scope.launch {
            try {
                val m = Api.memory()
                items.clear()
                items.addAll(m.items)
                on = m.on
                max = m.max
                error = ""
            } catch (e: Exception) {
                error = memError(ctx, e)
            }
            loading = false
        }
    }

    fun add() {
        val t = draft.trim()
        if (t.isEmpty()) return
        scope.launch {
            try {
                Api.memoryAdd(t)
                draft = ""
                error = ""
                load()
            } catch (e: Exception) {
                error = memError(ctx, e)
            }
        }
    }

    fun remove(id: Long) {
        scope.launch {
            try {
                Api.memoryDelete(id)
                items.removeAll { it.id == id }
            } catch (e: Exception) {
                error = memError(ctx, e)
            }
        }
    }

    fun removeAll() {
        scope.launch {
            try {
                Api.memoryClear()
                items.clear()
            } catch (e: Exception) {
                error = memError(ctx, e)
            }
        }
    }

    fun toggle(v: Boolean) {
        on = v
        scope.launch {
            try {
                Api.memoryToggle(v)
            } catch (e: Exception) {
                on = !v
                error = memError(ctx, e)
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    val c = LocalCandy.current
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        ScreenHeader(stringResource(R.string.ai_mem_title), onBack) {
            // "clear all" deletes things, so it wears the error colour, not the colour of normal buttons
            if (items.isNotEmpty()) {
                TextButton(onClick = { confirmAll = true }) { Text(stringResource(R.string.ai_mem_clear_all), color = cs.error) }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(CandyBrand.hairline()))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "switch") {
                val cardShape = RoundedCornerShape(CandyDims.cardRadius)
                Surface(
                    shape = cardShape,
                    color = c.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, c.border),
                    modifier = Modifier.fillMaxWidth().clip(cardShape).clickable(role = androidx.compose.ui.semantics.Role.Switch) { toggle(!on) },
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.ai_mem_on), style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
                            Switch(checked = on, onCheckedChange = null)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.ai_mem_desc), style = MaterialTheme.typography.bodySmall, color = c.subtext)
                    }
                }
            }
            if (error.isNotBlank()) item(key = "error") { ErrorText(error) { error = ""; load() } }
            if (!loading && items.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = { Icon(Icons.Rounded.Psychology, null, tint = c.cta, modifier = Modifier.size(44.dp)) },
                        message = stringResource(R.string.ai_mem_empty),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else if (items.isNotEmpty()) {
                item(key = "count") {
                    Surface(shape = CircleShape, color = cs.surfaceContainerHigh) {
                        Text(
                            stringResource(R.string.ai_mem_count, items.size, max),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.subtext,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            items(items, key = { it.id }) { m ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cs.surfaceContainerHigh,
                    border = androidx.compose.foundation.BorderStroke(1.dp, cs.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(start = 14.dp, top = 2.dp, bottom = 2.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(m.text, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                        AppIconButton(Icons.Rounded.Delete, stringResource(R.string.delete), { remove(m.id) }, tint = c.subtext)
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = cs.surfaceContainerHigh,
                border = androidx.compose.foundation.BorderStroke(1.dp, cs.outlineVariant),
                modifier = Modifier.weight(1f),
            ) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (draft.isEmpty()) Text(stringResource(R.string.ai_mem_add_hint), color = cs.onSurfaceVariant)
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it.take(120) },
                        textStyle = LocalTextStyle.current.copy(color = cs.onSurface),
                        cursorBrush = SolidColor(cs.primary),
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            val canAdd = draft.isNotBlank()
            Surface(
                shape = CircleShape,
                color = if (canAdd) c.cta else cs.surfaceContainerHigh,
                modifier = Modifier.size(52.dp).clip(CircleShape).clickable(enabled = canAdd) { add() },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.ai_mem_add_hint), tint = if (canAdd) c.onCta else c.subtext)
                }
            }
        }
    }

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            containerColor = cs.surfaceContainerHigh,
            text = { Text(stringResource(R.string.ai_mem_clear_q), color = cs.onSurface) },
            confirmButton = { TextButton(onClick = { confirmAll = false; removeAll() }) { Text(stringResource(R.string.ai_delete), color = cs.error) } },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text(stringResource(R.string.cancel), color = cs.onSurfaceVariant) } },
        )
    }
}
