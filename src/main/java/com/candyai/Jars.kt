package com.candyai

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// ───────────── Code Jar: a multi-file project Candy writes ─────────────

/** The server replaces every <jar> in a reply with [[jar:ID|Title|changed|failed]]. */
internal val JarToken = Regex("\\[\\[jar:([a-z0-9]+)\\|([^|\\]]*)\\|(\\d+)\\|(\\d+)\\]\\]")

/** Opens a jar. The default (null) starts the main screen with it, which also works from the edge panel. */
val LocalOpenJar = compositionLocalOf<((String) -> Unit)?> { null }

private fun launchJar(ctx: Context, id: String) {
    ctx.startActivity(
        Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("open_jar", id)
    )
}

private val JarColor = Color(0xFFE08A3C)

private fun timeText(at: Long): String =
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(at))

@Composable
private fun JarIcon(size: Int) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)).background(JarColor), contentAlignment = Alignment.Center) {
        Text("🫙", fontSize = (size * 0.5f).sp)
    }
}

/** The card Candy's reply shows instead of the project. */
@Composable
internal fun JarCard(id: String, title: String, changed: Int, failed: Int) {
    val c = LocalCandy.current
    val ctx = LocalContext.current
    val open = LocalOpenJar.current
    Surface(
        shape = RoundedCornerShape(16.dp), color = c.bg, border = BorderStroke(1.dp, c.border),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { if (open != null) open(id) else launchJar(ctx, id) },
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            JarIcon(42)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
                Text(
                    stringResource(R.string.jar_changed, changed) + (if (failed > 0) " · " + stringResource(R.string.jar_failed, failed) else "") + " · " + stringResource(R.string.artifact_tap),
                    color = c.subtext, fontSize = 12.sp,
                )
            }
        }
    }
}

// ───────────── the list ─────────────

@Composable
fun JarsScreen(onBack: () -> Unit, onOpen: (String) -> Unit, reloadKey: Int = 0) {
    val c = LocalCandy.current
    val ctx = LocalContext.current
    var items by remember { mutableStateOf<List<JarItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    BackHandler { onBack() }
    LaunchedEffect(reload, reloadKey) {
        try { items = Api.jars(); error = null } catch (e: Exception) { error = errorMessage(ctx, e) }
    }
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        ScreenHeader(stringResource(R.string.jars), onBack)
        val list = items
        when {
            list == null && error != null -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error ?: "", color = c.subtext, fontSize = 14.sp)
                TextButton(onClick = { reload++ }) { Text(stringResource(R.string.retry), color = c.userBubble) }
            }
            list == null -> Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = c.accent, strokeWidth = 3.dp) }
            list.isEmpty() -> EmptyState(
                icon = { JarIcon(56) },
                message = stringResource(R.string.jars_empty),
                modifier = Modifier.fillMaxSize(),
            )
            else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id }) { a ->
                    ListCard(
                        title = a.title,
                        subtitle = "${stringResource(R.string.jar_files_n, a.files)} · ${timeText(a.updatedAt)}",
                        onClick = { onOpen(a.id) },
                        icon = { JarIcon(44) },
                    )
                }
            }
        }
    }
}

private fun zipOf(files: List<JarFile>): ByteArray {
    val bos = java.io.ByteArrayOutputStream()
    ZipOutputStream(bos).use { z ->
        for (f in files) {
            z.putNextEntry(ZipEntry(f.path))
            z.write(f.content.toByteArray(Charsets.UTF_8))
            z.closeEntry()
        }
    }
    return bos.toByteArray()
}

@Composable
fun JarViewer(id: String, onBack: () -> Unit, onChanged: () -> Unit = {}) {
    val c = LocalCandy.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var jar by remember(id) { mutableStateOf<JarFull?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var sel by remember(id) { mutableStateOf<String?>(null) }
    var viewV by remember(id) { mutableIntStateOf(0) }            // 0 = the current version
    var oldFiles by remember(id) { mutableStateOf<List<JarFile>?>(null) }
    var menu by remember { mutableStateOf(false) }
    var versionsMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var diffMode by remember(id) { mutableStateOf(false) }
    var prevFiles by remember(id) { mutableStateOf<List<JarFile>?>(null) }   // the version before the one on screen
    BackHandler { onBack() }

    LaunchedEffect(id, reload) {
        try { jar = Api.jar(id); error = null } catch (e: Exception) { error = errorMessage(ctx, e) }
    }
    LaunchedEffect(jar, viewV, diffMode) {
        val jj = jar
        if (!diffMode || jj == null) { prevFiles = null; return@LaunchedEffect }
        val shown = if (viewV != 0) viewV else jj.current
        val before = jj.versions.map { it.first }.filter { it < shown }.maxOrNull()
        prevFiles = try { if (before == null) emptyList() else Api.jarVersion(jj.id, before) } catch (_: Exception) { null }
    }
    val j = jar
    val files = if (viewV != 0 && oldFiles != null) oldFiles!! else j?.files ?: emptyList()
    val file = files.firstOrNull { it.path == sel } ?: files.firstOrNull()
    val savedMsg = stringResource(R.string.artifact_saved)
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(zipOf(files)) }
                Toast.makeText(ctx, savedMsg, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) { }
        }
    }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        ScreenHeader(j?.title ?: "", onBack) {
            if (j != null) {
                IconButton(onClick = { if (file != null) copyRawText(ctx, file.content) }) { Icon(Icons.Rounded.ContentCopy, stringResource(R.string.artifact_copy), tint = c.subtext) }
                IconButton(onClick = { saver.launch("${j.title.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().ifBlank { "candy-jar" }}.zip") }) {
                    Icon(Icons.Rounded.Download, stringResource(R.string.jar_export), tint = c.subtext)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, null, tint = c.subtext) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (j.versions.size > 1) DropdownMenuItem(
                            text = { Text(stringResource(R.string.artifact_versions)) },
                            leadingIcon = { Icon(Icons.Rounded.History, null) },
                            onClick = { menu = false; versionsMenu = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.artifact_rename)) },
                            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                            onClick = { menu = false; renaming = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; deleting = true },
                        )
                    }
                }
            }
        }

        when {
            j == null && error != null -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error ?: "", color = c.subtext, fontSize = 14.sp)
                TextButton(onClick = { reload++ }) { Text(stringResource(R.string.retry), color = c.userBubble) }
            }
            j == null -> Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = c.accent, strokeWidth = 3.dp) }
            else -> {
                if (viewV != 0 && viewV != j.current) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(14.dp)).background(c.surface).padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.artifact_old_version, viewV), color = c.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewV = 0; oldFiles = null }, modifier = Modifier.heightIn(min = 44.dp)) { Text(stringResource(R.string.artifact_back_current), color = c.subtext, style = MaterialTheme.typography.labelLarge) }
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = c.cta, contentColor = c.onCta),
                            modifier = Modifier.heightIn(min = 44.dp),
                            onClick = {
                            scope.launch {
                                try {
                                    Api.restoreJar(j.id, viewV)
                                    viewV = 0; oldFiles = null; reload++; onChanged()
                                } catch (e: Exception) { Toast.makeText(ctx, errorMessage(ctx, e), Toast.LENGTH_SHORT).show() }
                            }
                        }) { Text(stringResource(R.string.artifact_restore)) }
                    }
                }
                if (files.isEmpty()) {
                    Text(stringResource(R.string.jar_empty), color = c.subtext, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(32.dp))
                } else {
                    if (j.versions.size > 1) {
                        PillTabs(listOf(stringResource(R.string.jar_view_code), stringResource(R.string.jar_view_diff)), if (diffMode) 1 else 0) { diffMode = it == 1 }
                    }
                    val before = prevFiles
                    if (diffMode && before != null) {
                        JarDiffView(files, before)
                    } else if (diffMode) {
                        Box(Modifier.weight(1f).fillMaxWidth()) { CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = c.accent, strokeWidth = 3.dp) }
                    } else {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (f in files) {
                            val on = f.path == file?.path
                            ChipPill(f.path, on, { sel = f.path }, mono = true)
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp)) {
                        // code always reads left to right, and scrolls both ways
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            SelectionContainer {
                                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(16.dp)) {
                                    Text(file?.content ?: "", color = c.text, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp, softWrap = false)
                                }
                            }
                        }
                    }
                    }
                }
            }
        }
    }

    if (versionsMenu && j != null) {
        AlertDialog(
            onDismissRequest = { versionsMenu = false },
            confirmButton = { TextButton(onClick = { versionsMenu = false }) { Text(stringResource(R.string.cancel)) } },
            title = { Text(stringResource(R.string.artifact_versions)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    for ((v, at) in j.versions) {
                        Text(
                            "${stringResource(R.string.artifact_version_n, v)}${if (v == j.current) " ✓" else ""} · ${timeText(at)}",
                            color = c.text, fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                                versionsMenu = false
                                if (v == j.current) { viewV = 0; oldFiles = null }
                                else scope.launch {
                                    try { oldFiles = Api.jarVersion(j.id, v); viewV = v } catch (e: Exception) { Toast.makeText(ctx, errorMessage(ctx, e), Toast.LENGTH_SHORT).show() }
                                }
                            }.padding(vertical = 12.dp, horizontal = 4.dp),
                        )
                    }
                }
            },
        )
    }
    if (renaming && j != null) {
        var name by remember { mutableStateOf(j.title) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.artifact_rename)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    renaming = false
                    scope.launch {
                        try { Api.renameJar(j.id, name.trim()); reload++; onChanged() } catch (e: Exception) { Toast.makeText(ctx, errorMessage(ctx, e), Toast.LENGTH_SHORT).show() }
                    }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (deleting && j != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.jar_delete_title)) },
            text = { Text(stringResource(R.string.jar_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    scope.launch {
                        try { Api.deleteJar(j.id); onChanged(); onBack() } catch (e: Exception) { Toast.makeText(ctx, errorMessage(ctx, e), Toast.LENGTH_SHORT).show() }
                    }
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

// ───────────── what changed between two versions ─────────────

private class DiffLine(val mark: Char, val text: String)          // ' ' kept, '+' added, '-' removed
private class DiffEntry(val path: String, val mark: String, val lines: List<DiffLine>)

/** Line diff: the common start and end are cut off first, then the middle goes through a longest-common-subsequence table. */
private fun diffLines(old: List<String>, new: List<String>): List<DiffLine> {
    var a = 0
    while (a < old.size && a < new.size && old[a] == new[a]) a++
    var e = 0
    while (e < old.size - a && e < new.size - a && old[old.size - 1 - e] == new[new.size - 1 - e]) e++
    val mo = old.subList(a, old.size - e)
    val mn = new.subList(a, new.size - e)
    val out = ArrayList<DiffLine>()
    for (i in 0 until a) out.add(DiffLine(' ', old[i]))
    if (mo.size.toLong() * mn.size > 4_000_000L) {                      // too big for the table: show it as one replaced block
        mo.forEach { out.add(DiffLine('-', it)) }
        mn.forEach { out.add(DiffLine('+', it)) }
    } else {
        val w = mn.size + 1
        val t = IntArray((mo.size + 1) * w)
        for (i in mo.size - 1 downTo 0) for (j in mn.size - 1 downTo 0)
            t[i * w + j] = if (mo[i] == mn[j]) t[(i + 1) * w + j + 1] + 1 else maxOf(t[(i + 1) * w + j], t[i * w + j + 1])
        var i = 0
        var j = 0
        while (i < mo.size && j < mn.size) {
            when {
                mo[i] == mn[j] -> { out.add(DiffLine(' ', mo[i])); i++; j++ }
                t[(i + 1) * w + j] >= t[i * w + j + 1] -> { out.add(DiffLine('-', mo[i])); i++ }
                else -> { out.add(DiffLine('+', mn[j])); j++ }
            }
        }
        while (i < mo.size) { out.add(DiffLine('-', mo[i])); i++ }
        while (j < mn.size) { out.add(DiffLine('+', mn[j])); j++ }
    }
    for (k in old.size - e until old.size) out.add(DiffLine(' ', old[k]))
    return out
}

private fun changes(now: List<JarFile>, before: List<JarFile>): List<DiffEntry> {
    val o = before.associate { it.path to it.content }
    val n = now.associate { it.path to it.content }
    return (o.keys + n.keys).sorted().mapNotNull { p ->
        val a = o[p]
        val b = n[p]
        when {
            a == null && b != null -> DiffEntry(p, "+", diffLines(emptyList(), b.split("\n")))
            a != null && b == null -> DiffEntry(p, "−", diffLines(a.split("\n"), emptyList()))
            a != null && b != null && a != b -> DiffEntry(p, "~", diffLines(a.split("\n"), b.split("\n")))
            else -> null
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.JarDiffView(now: List<JarFile>, before: List<JarFile>) {
    val c = LocalCandy.current
    val entries = remember(now, before) { changes(now, before) }
    var sel by remember(now, before) { mutableStateOf<String?>(null) }
    if (entries.isEmpty()) {
        Text(stringResource(R.string.jar_no_changes), color = c.subtext, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(32.dp))
        return
    }
    val cur = entries.firstOrNull { it.path == sel } ?: entries.first()
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (en in entries) {
            val on = en.path == cur.path
            Surface(
                shape = RoundedCornerShape(50), color = if (on) c.userBubble else c.surface,
                border = BorderStroke(1.dp, c.border),
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { sel = en.path },
            ) {
                Text(
                    "${en.mark} ${en.path}", color = if (on) c.onUserBubble else c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace, maxLines = 1, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
    // unchanged stretches are folded: three lines of context around each change
    val rows = remember(cur) {
        val keep = BooleanArray(cur.lines.size)
        cur.lines.forEachIndexed { i, l -> if (l.mark != ' ') for (k in maxOf(0, i - 3)..minOf(cur.lines.lastIndex, i + 3)) keep[k] = true }
        val out = ArrayList<Pair<Char, String>>()
        var hidden = 0
        cur.lines.forEachIndexed { i, l ->
            if (keep[i]) { if (hidden > 0) { out.add('…' to hidden.toString()); hidden = 0 }; out.add(l.mark to l.text) } else hidden++
        }
        if (hidden > 0) out.add('…' to hidden.toString())
        out
    }
    val green = Color(0xFF2E9E5B)
    val red = MaterialTheme.colorScheme.error
    Box(Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            SelectionContainer {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                    for ((mark, text) in rows) {
                        val bg = when (mark) { '+' -> green.copy(alpha = 0.18f); '-' -> red.copy(alpha = 0.18f); else -> Color.Transparent }
                        Text(
                            if (mark == '…') "⋯  $text" else "$mark $text",
                            color = if (mark == '…') c.subtext else c.text, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp, softWrap = false,
                            modifier = Modifier.fillMaxWidth().background(bg).padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }
    }
}
