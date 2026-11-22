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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

    // Candy found an event in a note and asks when to remind (card, then a "done" sheet)
    var ask by remember { mutableStateOf<ReminderAsk?>(null) }
    val analyzed = remember { mutableSetOf<String>() }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun ensureNotifications() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    LaunchedEffect(reload) {
        try {
            val list = Api.notes()
            notes = list
            error = null
            SugarWidget.save(ctx, list)
            Reminders.sync(ctx, list)
            // notes Candy has not read yet: she looks for an upcoming event, once per note
            if (ask == null) {
                for (n in list.filter { !it.analyzed && it.id !in analyzed }.take(4)) {
                    analyzed.add(n.id)
                    val a = askFor(ctx, n)
                    if (a != null) { ask = a; break }
                }
            }
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

    ask?.let { a ->
        NoteReminderFlow(a, onFinished = { ask = null; reload++ })
    }

    if (creating || editing != null) {
        val n = editing
        NoteEditor(
            note = n,
            onDismiss = { creating = false; editing = null },
            onSave = { text, color, pinned, remindAt ->
                creating = false; editing = null
                if (remindAt != null && remindAt != n?.remindAt) ensureNotifications()
                scope.launch {
                    try {
                        if (n == null) Api.createNote(text, color, pinned, remindAt) else Api.updateNote(n.id, text, color, pinned, remindAt)
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
                if (n.remindAt > System.currentTimeMillis()) {
                    Spacer(Modifier.height(8.dp))
                    Text("🔔 " + formatWhen(n.remindAt), color = NoteInk.copy(alpha = 0.6f), fontSize = 11.sp)
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
private fun NoteEditor(note: Note?, onDismiss: () -> Unit, onSave: (String, Int, Boolean, Long?) -> Unit, onDelete: (() -> Unit)?) {
    val ctx = LocalContext.current
    val c = LocalCandy.current
    var text by remember { mutableStateOf(note?.text ?: "") }
    var color by remember { mutableIntStateOf(note?.color ?: 0) }
    var pinned by remember { mutableStateOf(note?.pinned ?: false) }
    var remindAt by remember { mutableStateOf(note?.remindAt?.takeIf { it > System.currentTimeMillis() }) }
    var picking by remember { mutableStateOf(false) }
    if (picking) DateTimePicker(remindAt, onPicked = { remindAt = it; picking = false }, onDismiss = { picking = false })

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔔", fontSize = 15.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(remindAt?.let { formatWhen(it) } ?: stringResource(R.string.sugar_reminder), color = NoteInk, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { picking = true }) {
                        Text(stringResource(if (remindAt == null) R.string.sugar_reminder_set else R.string.sugar_reminder_change), color = NoteInk, fontWeight = FontWeight.Bold)
                    }
                }
                if (remindAt != null) TextButton(onClick = { remindAt = null }) { Text(stringResource(R.string.sugar_reminder_clear), color = NoteInk.copy(alpha = 0.7f)) }
                if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = Color(0xFFB3261E)) }
            }
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onSave(text.trim(), color, pinned, remindAt) }) { Text(stringResource(R.string.save), color = NoteInk, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = NoteInk.copy(alpha = 0.7f)) } },
    )
}

internal fun formatWhen(at: Long): String =
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(at))

/** The phone's local date and time as plain text, so the server can work out what "tomorrow" or "Thursday" means. */
private fun nowText(): String {
    val n = java.time.LocalDateTime.now()
    val day = n.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale("ar"))
    return n.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + " ($day)"
}

/** Reminder times to offer for an event: only ones that are still in the future. */
private fun reminderOptions(ctx: android.content.Context, sg: NoteSuggest): List<Pair<String, Long>> {
    val zone = java.time.ZoneId.systemDefault()
    val day = try { java.time.LocalDate.parse(sg.day) } catch (_: Exception) { return emptyList() }
    fun at(d: java.time.LocalDate, t: java.time.LocalTime) = java.time.ZonedDateTime.of(d, t, zone).toInstant().toEpochMilli()
    val out = mutableListOf<Pair<String, Long>>()
    val t = sg.time?.let { try { java.time.LocalTime.parse(it) } catch (_: Exception) { null } }
    if (t != null) {
        val ev = at(day, t)
        out += ctx.getString(R.string.sugar_opt_hour_before) to ev - 3_600_000L
        out += ctx.getString(R.string.sugar_opt_night_before) to at(day.minusDays(1), java.time.LocalTime.of(20, 0))
        out += ctx.getString(R.string.sugar_opt_at_time) to ev
    } else {
        out += ctx.getString(R.string.sugar_opt_night_before) to at(day.minusDays(1), java.time.LocalTime.of(20, 0))
        out += ctx.getString(R.string.sugar_opt_morning_of) to at(day, java.time.LocalTime.of(8, 0))
    }
    val now = System.currentTimeMillis() + 60_000L
    return out.filter { it.second > now }
}

class ReminderAsk(val note: Note, val question: String, val options: List<Pair<String, Long>>)

/** Candy reads a note (once). If it has an upcoming event, returns the question to ask and the reminder times to offer. */
suspend fun askFor(ctx: android.content.Context, n: Note): ReminderAsk? {
    val sg = try { Api.analyzeNote(n.id, nowText()) } catch (_: Exception) { null } ?: return null
    val opts = reminderOptions(ctx, sg)
    if (opts.isEmpty()) return null
    val q = ctx.getString(
        if (sg.time == null) R.string.sugar_ask_no_time else R.string.sugar_ask_time,
        sg.event.ifBlank { Reminders.title(n.text) }, sg.whenText.ifBlank { sg.day },
    )
    return ReminderAsk(n, q, opts)
}

/** The whole "when should I remind you?" flow: the question card, a custom time if wanted, the "done" sheet, and the battery hint. */
@Composable
fun NoteReminderFlow(a: ReminderAsk, onFinished: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var stage by remember { mutableIntStateOf(0) }   // 0 question, 1 custom time, 2 done, 3 battery hint
    var doneText by remember { mutableStateOf("") }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun set(at: Long) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        scope.launch {
            try {
                Api.updateNote(a.note.id, a.note.text, a.note.color, a.note.pinned, at)
                try { Reminders.sync(ctx, Api.notes()) } catch (_: Exception) { }
                doneText = ctx.getString(R.string.sugar_done_body, Reminders.title(a.note.text), formatWhen(at))
                stage = 2
            } catch (_: Exception) {
                onFinished()
            }
        }
    }

    when (stage) {
        0 -> {
            val labels = a.options.map { it.first } + listOf(ctx.getString(R.string.sugar_opt_custom), ctx.getString(R.string.sugar_opt_none))
            AskCard(
                question = a.question, options = labels, onClose = onFinished,
                onNext = { i ->
                    when {
                        i < a.options.size -> set(a.options[i].second)
                        i == a.options.size -> stage = 1
                        else -> onFinished()
                    }
                },
            )
        }
        1 -> DateTimePicker(null, onPicked = { set(it) }, onDismiss = onFinished)
        2 -> DoneSheet(
            title = stringResource(R.string.sugar_done_title), body = doneText,
            onDone = { if (Reminders.needsBatteryHint(ctx)) stage = 3 else onFinished() },
        )
        else -> androidx.compose.material3.AlertDialog(
            onDismissRequest = onFinished,
            containerColor = androidx.compose.ui.graphics.Color.White,
            title = { Text(stringResource(R.string.battery_title), color = NoteInk) },
            text = { Text(stringResource(R.string.battery_body), color = NoteInk.copy(alpha = 0.75f)) },
            confirmButton = {
                TextButton(onClick = { Reminders.askBatteryExemption(ctx); onFinished() }) { Text(stringResource(R.string.battery_allow), color = LocalCandy.current.userBubble, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = onFinished) { Text(stringResource(R.string.battery_later), color = NoteInk.copy(alpha = 0.7f)) } },
        )
    }
}
