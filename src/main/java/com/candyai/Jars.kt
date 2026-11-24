package com.candyai

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
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = c.text) }
            Text(stringResource(R.string.jars), color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        val list = items
        when {
            list == null && error != null -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error ?: "", color = c.subtext, fontSize = 14.sp)
                TextButton(onClick = { reload++ }) { Text(stringResource(R.string.retry), color = c.userBubble) }
            }
            list == null -> Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = c.accent, strokeWidth = 3.dp) }
            list.isEmpty() -> Text(stringResource(R.string.jars_empty), color = c.subtext, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(32.dp))
            else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id }) { a ->
                    Surface(
                        shape = RoundedCornerShape(18.dp), color = c.surface, border = BorderStroke(1.dp, c.border),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { onOpen(a.id) },
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            JarIcon(44)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
                                Text("${stringResource(R.string.jar_files_n, a.files)} · ${timeText(a.updatedAt)}", color = c.subtext, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ───────────── the viewer ─────────────

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
    BackHandler { onBack() }

    LaunchedEffect(id, reload) {
        try { jar = Api.jar(id); error = null } catch (e: Exception) { error = errorMessage(ctx, e) }
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
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = c.text) }
            Text(
                j?.title ?: "", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                style = TextStyle(textDirection = TextDirection.Content),
            )
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
                            text = { Text(stringResource(R.string.delete), color = Color(0xFFD64545)) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Color(0xFFD64545)) },
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
                    Row(Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.artifact_old_version, viewV), color = c.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewV = 0; oldFiles = null }) { Text(stringResource(R.string.artifact_back_current), color = c.subtext, fontSize = 13.sp) }
                        TextButton(onClick = {
                            scope.launch {
                                try {
                                    Api.restoreJar(j.id, viewV)
                                    viewV = 0; oldFiles = null; reload++; onChanged()
                                } catch (e: Exception) { Toast.makeText(ctx, errorMessage(ctx, e), Toast.LENGTH_SHORT).show() }
                            }
                        }) { Text(stringResource(R.string.artifact_restore), color = c.userBubble, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                if (files.isEmpty()) {
                    Text(stringResource(R.string.jar_empty), color = c.subtext, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(32.dp))
                } else {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (f in files) {
                            val on = f.path == file?.path
                            Surface(
                                shape = RoundedCornerShape(50), color = if (on) c.userBubble else c.surface,
                                border = BorderStroke(1.dp, c.border),
                                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { sel = f.path },
                            ) {
                                Text(
                                    f.path, color = if (on) c.onUserBubble else c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace, maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                )
                            }
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
                }) { Text(stringResource(R.string.delete), color = Color(0xFFD64545)) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
