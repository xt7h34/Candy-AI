package com.candyai

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** The pastel paper colours of the notes (the server stores the index; the widget's note_bg_N drawables use the same list). */
val NoteColors = listOf(
    Color(0xFFFFF3A8), Color(0xFFFFC8DD), Color(0xFFBDE0FE), Color(0xFFCAF0C0),
    Color(0xFFFFD6A5), Color(0xFFE0C3FC), Color(0xFFB9F3E4), Color(0xFFFFB5A7),
)
private val NoteInk = Color(0xFF2B1B20)

fun noteColor(i: Int): Color = NoteColors[((i % NoteColors.size) + NoteColors.size) % NoteColors.size]

/** A small steady tilt per note (-2.5 to +2.5 degrees), so the cards look stuck on by hand but never jump around. */
fun noteTilt(id: String): Float = ((id.hashCode() and 0x7fffffff) % 11 - 5) * 0.5f

/** SugarNote: pinned, slightly tilted pastel cards, saved on the server with the account. */
@Composable
fun SugarNoteScreen(onBack: () -> Unit, startNew: Boolean = false) {
    val c = LocalCandy.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var notes by remember { mutableStateOf<List<Note>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Note?>(null) }
    var creating by remember { mutableStateOf(startNew) }

    LaunchedEffect(reload) {
        try {
            val list = Api.notes()
            notes = list
            error = null
            SugarWidget.save(ctx, list)
        } catch (e: Exception) {
            error = errorMessage(ctx, e)
        }
    }

    BackHandler { onBack() }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = c.text) }
            Text(stringResource(R.string.sugar), color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { creating = true }) { Icon(Icons.Rounded.Add, stringResource(R.string.sugar_new), tint = c.accent) }
        }

        val list = notes
        when {
            list == null && error != null -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error ?: "", color = c.subtext, fontSize = 14.sp)
                TextButton(onClick = { reload++ }) { Text(stringResource(R.string.retry), color = c.userBubble) }
            }
            list == null -> Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = c.accent, strokeWidth = 3.dp) }
            list.isEmpty() -> Text(stringResource(R.string.sugar_empty), color = c.subtext, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(32.dp))
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 6.dp)) {
                // cards zig-zag left and right, joined by dashed curves, like notes pinned on a board
                list.forEachIndexed { i, n ->
                    val left = i % 2 == 0
                    Row(Modifier.fillMaxWidth()) {
                        if (!left) Spacer(Modifier.weight(0.2f))
                        Box(Modifier.weight(0.8f)) { NoteCard(n, i + 1) { editing = n } }
                        if (left) Spacer(Modifier.weight(0.2f))
                    }
                    if (i < list.lastIndex) NoteConnector(fromLeft = left)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (creating || editing != null) {
        val n = editing
        NoteEditor(
            note = n,
            onDismiss = { creating = false; editing = null },
            onSave = { text, color, pinned ->
                creating = false; editing = null
                scope.launch {
                    try {
                        if (n == null) Api.createNote(text, color, pinned) else Api.updateNote(n.id, text, color, pinned)
                    } catch (e: Exception) {
                        error = errorMessage(ctx, e)
                    }
                    reload++
                }
            },
            onDelete = n?.let { x ->
                {
                    creating = false; editing = null
                    scope.launch {
                        try { Api.deleteNote(x.id) } catch (_: Exception) { }
                        reload++
                    }
                }
            },
        )
    }
}

/** The pin colours that go with the paper colours. */
private val PinColors = listOf(
    Color(0xFFF5B700), Color(0xFFFF5C8A), Color(0xFF4D8DF7), Color(0xFF3DBE6E),
    Color(0xFFFF8A3D), Color(0xFF8B5CF6), Color(0xFF14B8A6), Color(0xFFFF6B5A),
)

private fun pinColor(i: Int): Color = PinColors[((i % PinColors.size) + PinColors.size) % PinColors.size]

@Composable
private fun NoteConnector(fromLeft: Boolean) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(34.dp)) {
        val a = size.width * (if (fromLeft) 0.4f else 0.6f)
        val b = size.width * (if (fromLeft) 0.6f else 0.4f)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(a, 0f)
            cubicTo(a, size.height * 0.85f, b, size.height * 0.15f, b, size.height)
        }
        drawPath(
            path, Color(0x66888888),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 1.5.dp.toPx(),
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
            ),
        )
    }
}

@Composable
private fun NoteCard(n: Note, number: Int, onClick: () -> Unit) {
    val outer = RoundedCornerShape(22.dp)
    val pin = pinColor(n.color)
    val title = n.text.substringBefore('\n').trim()
    val body = n.text.substringAfter('\n', "").trim()
    Box(Modifier.fillMaxWidth().graphicsLayer { rotationZ = noteTilt(n.id) * 1.6f }.padding(top = 9.dp)) {
        Box(
            Modifier.fillMaxWidth()
                .shadow(if (n.pinned) 14.dp else 8.dp, outer, ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
                .clip(outer)
                .background(Color.White)
                .clickable(onClick = onClick)
                .padding(10.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(noteColor(n.color).copy(alpha = 0.6f)).padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 14.dp),
            ) {
                Text("%02d".format(number), color = pin, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                Text(
                    title, color = NoteInk, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = TextDirection.Content),
                )
                if (body.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        body, color = NoteInk.copy(alpha = 0.62f), fontSize = 13.sp, lineHeight = 19.sp, maxLines = 6, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(textDirection = TextDirection.Content),
                    )
                }
            }
            if (n.pinned) Text("★", color = pin, fontSize = 14.sp, modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 8.dp))
        }
        // the glossy pin
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = (-3).dp).size(20.dp)
                .shadow(5.dp, CircleShape, ambientColor = pin, spotColor = pin)
                .clip(CircleShape)
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.85f), pin, pin.copy(alpha = 0.75f)),
                        center = androidx.compose.ui.geometry.Offset(18f, 14f),
                        radius = 46f,
                    ),
                ),
        )
    }
}

@Composable
private fun NoteEditor(note: Note?, onDismiss: () -> Unit, onSave: (String, Int, Boolean) -> Unit, onDelete: (() -> Unit)?) {
    val c = LocalCandy.current
    var text by remember { mutableStateOf(note?.text ?: "") }
    var color by remember { mutableIntStateOf(note?.color ?: 0) }
    var pinned by remember { mutableStateOf(note?.pinned ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = noteColor(color),
        titleContentColor = NoteInk,
        title = { Text(stringResource(if (note == null) R.string.sugar_new else R.string.sugar_edit), color = NoteInk) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth().height(150.dp)) {
                    if (text.isEmpty()) Text(stringResource(R.string.sugar_hint), color = NoteInk.copy(alpha = 0.5f), fontSize = 15.sp)
                    BasicTextField(
                        value = text, onValueChange = { text = it.take(2000) },
                        textStyle = TextStyle(textDirection = TextDirection.Content, color = NoteInk, fontSize = 15.sp, lineHeight = 22.sp),
                        cursorBrush = SolidColor(NoteInk),
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NoteColors.forEachIndexed { i, col ->
                        Box(
                            Modifier.size(26.dp).clip(CircleShape).background(col)
                                .then(if (i == color) Modifier.background(Color.Transparent) else Modifier)
                                .clickable { color = i },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (i == color) Box(Modifier.size(10.dp).clip(CircleShape).background(NoteInk))
                        }
                    }
                }
                Row(Modifier.clickable { pinned = !pinned }, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(18.dp).clip(CircleShape).background(if (pinned) Color(0xFFE5384F) else NoteInk.copy(alpha = 0.2f)))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.sugar_pin), color = NoteInk, fontSize = 14.sp)
                }
                if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = Color(0xFFB3261E)) }
            }
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onSave(text.trim(), color, pinned) }) { Text(stringResource(R.string.save), color = NoteInk, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = NoteInk.copy(alpha = 0.7f)) } },
    )
}
