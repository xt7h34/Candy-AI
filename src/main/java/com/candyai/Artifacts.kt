package com.candyai

import androidx.compose.material3.MaterialTheme
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color as AColor
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream

// ───────────── what Candy's reply contains ─────────────

/** The server replaces every <artifact> in a reply with this marker: [[artifact:ID|type|Title]]. */
private val ArtifactToken = Regex("\\[\\[artifact:([a-z0-9]+)\\|([a-z]+)\\|([^\\]]*)\\]\\]")

internal sealed class ArtPart {
    class Text(val s: String) : ArtPart()
    class Card(val id: String, val type: String, val title: String) : ArtPart()
    class Jar(val id: String, val title: String, val changed: Int, val failed: Int) : ArtPart()
}

internal fun splitArtifacts(text: String): List<ArtPart> {
    val out = ArrayList<ArtPart>()
    var at = 0
    val found = ArtifactToken.findAll(text).map { it to false }.toList() + JarToken.findAll(text).map { it to true }.toList()
    for ((m, isJar) in found.sortedBy { it.first.range.first }) {
        if (m.range.first > at) out.add(ArtPart.Text(text.substring(at, m.range.first)))
        if (isJar) out.add(ArtPart.Jar(m.groupValues[1], m.groupValues[2], m.groupValues[3].toIntOrNull() ?: 0, m.groupValues[4].toIntOrNull() ?: 0))
        else out.add(ArtPart.Card(m.groupValues[1], m.groupValues[2], m.groupValues[3]))
        at = m.range.last + 1
    }
    if (at < text.length) out.add(ArtPart.Text(text.substring(at)))
    return out
}

/** Text without the markers (for copying and sharing). */
internal fun stripArtifacts(text: String): String = JarToken.replace(ArtifactToken.replace(text, ""), "").replace(Regex("\n{3,}"), "\n\n")

/** Opens an artifact. The default (null) starts the main screen with it, which also works from the edge panel. */
val LocalOpenArtifact = compositionLocalOf<((String) -> Unit)?> { null }

private fun launchArtifact(ctx: Context, id: String) {
    ctx.startActivity(
        Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("open_artifact", id)
    )
}

@Composable
private fun typeName(type: String): String = stringResource(
    when (type) {
        "html" -> R.string.artifact_type_html
        "svg" -> R.string.artifact_type_svg
        else -> R.string.artifact_type_code
    }
)

private fun typeGlyph(type: String) = when (type) {
    "html" -> "</>"
    "svg" -> "◇"
    else -> "{ }"
}

private fun typeColor(type: String) = when (type) {
    "html" -> Color(0xFF5B8DEF)
    "svg" -> Color(0xFFF7567C)
    else -> Color(0xFF4CC38A)
}

/** The card Candy's reply shows instead of the work itself. */
@Composable
internal fun ArtifactCard(id: String, type: String, title: String) {
    val c = LocalCandy.current
    val ctx = LocalContext.current
    val open = LocalOpenArtifact.current
    Surface(
        shape = RoundedCornerShape(16.dp), color = c.bg, border = BorderStroke(1.dp, c.border),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { if (open != null) open(id) else launchArtifact(ctx, id) },
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(typeColor(type)), contentAlignment = Alignment.Center) {
                Text(typeGlyph(type), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
                Text("${typeName(type)} · ${stringResource(R.string.artifact_tap)}", color = c.subtext, fontSize = 12.sp)
            }
        }
    }
}

// ───────────── the list ─────────────

private fun timeText(at: Long): String =
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(at))

@Composable
fun ArtifactsScreen(onBack: () -> Unit, onOpen: (String) -> Unit, reloadKey: Int = 0) {
    val c = LocalCandy.current
    val ctx = LocalContext.current
    var items by remember { mutableStateOf<List<ArtifactItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    BackHandler { onBack() }
    LaunchedEffect(reload, reloadKey) {
        try { items = Api.artifacts(); error = null } catch (e: Exception) { error = errorMessage(ctx, e) }
    }
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        ScreenHeader(stringResource(R.string.artifacts), onBack)
        val list = items
        when {
            list == null && error != null -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error ?: "", color = c.subtext, fontSize = 14.sp)
                TextButton(onClick = { reload++ }) { Text(stringResource(R.string.retry), color = c.userBubble) }
            }
            list == null -> Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = c.accent, strokeWidth = 3.dp) }
            list.isEmpty() -> EmptyState(
                icon = { Text("</>", color = c.cta, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace)) },
                message = stringResource(R.string.artifacts_empty),
                modifier = Modifier.fillMaxSize(),
            )
            else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id }) { a ->
                    ListCard(
                        title = a.title,
                        subtitle = "${typeName(a.type)} · ${timeText(a.updatedAt)}",
                        onClick = { onOpen(a.id) },
                        icon = {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(typeColor(a.type)), contentAlignment = Alignment.Center) {
                                Text(typeGlyph(a.type), color = Color.White, style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace))
                            }
                        },
                    )
                }
            }
        }
    }
}

// ───────────── the viewer ─────────────

private val Csp = "default-src 'none'; img-src data: blob:; style-src 'unsafe-inline'; script-src 'unsafe-inline'; font-src data:; media-src data: blob:"

/** Wraps the work in a page with no network access (CSP) and a phone viewport. */
internal fun artifactPage(type: String, content: String): String {
    val meta = "<meta http-equiv=\"Content-Security-Policy\" content=\"$Csp\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
    if (type == "svg") {
        return "<!doctype html><html><head><meta charset=\"utf-8\">$meta<style>html,body{margin:0;height:100%;background:#fff}body{display:flex;align-items:center;justify-content:center}svg{max-width:100%;max-height:100vh;height:auto}</style></head><body>$content</body></html>"
    }
    val head = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(content)
    if (head != null) return content.substring(0, head.range.last + 1) + meta + content.substring(head.range.last + 1)
    val html = Regex("<html[^>]*>", RegexOption.IGNORE_CASE).find(content)
    if (html != null) return content.substring(0, html.range.last + 1) + "<head>$meta</head>" + content.substring(html.range.last + 1)
    return "<!doctype html><html><head><meta charset=\"utf-8\">$meta</head><body>$content</body></html>"
}

/** A WebView that runs the work with JavaScript but without any way out: no network, no files, no bridge, no popups. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SandboxView(type: String, content: String, modifier: Modifier) {
    val page = remember(type, content) { artifactPage(type, content) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(AColor.WHITE)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                settings.setGeolocationEnabled(false)
                settings.databaseEnabled = false
                @Suppress("DEPRECATION")
                settings.allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                settings.allowUniversalAccessFromFileURLs = false
                try { settings.blockNetworkLoads = true } catch (_: Exception) { }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        val u = request.url.scheme ?: ""
                        return if (u == "data" || u == "blob" || u == "about") null
                        else WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                    }
                }
            }
        },
        update = { w ->
            if (w.tag != page) {
                w.tag = page
                w.loadDataWithBaseURL(null, page, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.stopLoading(); it.destroy() },
    )
}

private fun extFor(type: String, lang: String): String = when (type) {
    "html" -> "html"
    "svg" -> "svg"
    else -> when (lang.lowercase()) {
        "python", "py" -> "py"
        "javascript", "js" -> "js"
        "typescript", "ts" -> "ts"
        "kotlin", "kt" -> "kt"
        "java" -> "java"
        "css" -> "css"
        "json" -> "json"
        "sql" -> "sql"
        "bash", "sh", "shell" -> "sh"
        "c" -> "c"
        "cpp", "c++" -> "cpp"
        "csharp", "c#" -> "cs"
        "go" -> "go"
        "rust", "rs" -> "rs"
        "php" -> "php"
        "swift" -> "swift"
        "dart" -> "dart"
        else -> "txt"
    }
}

@Composable
fun ArtifactViewer(id: String, onBack: () -> Unit, onChanged: () -> Unit = {}) {
    val c = LocalCandy.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var art by remember(id) { mutableStateOf<ArtifactFull?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var tab by remember(id) { mutableIntStateOf(0) }
    var viewV by remember(id) { mutableIntStateOf(0) }            // 0 = the current version
    var oldContent by remember(id) { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var versionsMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    BackHandler { onBack() }

    LaunchedEffect(id, reload) {
        try { art = Api.artifact(id); error = null } catch (e: Exception) { error = errorMessage(ctx, e) }
    }
    val a = art
    val content = if (viewV != 0 && oldContent != null) oldContent!! else a?.content ?: ""
    val savedMsg = stringResource(R.string.artifact_saved)
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                Toast.makeText(ctx, savedMsg, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) { }
        }
    }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = c.text) }
            Text(
                a?.title ?: "", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                style = TextStyle(textDirection = TextDirection.Content),
            )
            if (a != null) {
                IconButton(onClick = { copyRawText(ctx, content) }) { Icon(Icons.Rounded.ContentCopy, stringResource(R.string.artifact_copy), tint = c.subtext) }
                IconButton(onClick = { saver.launch("${a.title.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().ifBlank { "candy" }}.${extFor(a.type, a.lang)}") }) {
                    Icon(Icons.Rounded.Download, stringResource(R.string.artifact_save), tint = c.subtext)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, null, tint = c.subtext) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.artifact_share)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Send, null) },
                            onClick = {
                                menu = false
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, content).putExtra(Intent.EXTRA_SUBJECT, a.title)
                                ctx.startActivity(Intent.createChooser(send, null))
                            },
                        )
                        if (a.versions.size > 1) DropdownMenuItem(
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
            a == null && error != null -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error ?: "", color = c.subtext, fontSize = 14.sp)
                TextButton(onClick = { reload++ }) { Text(stringResource(R.string.retry), color = c.userBubble) }
            }
            a == null -> Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = c.accent, strokeWidth = 3.dp) }
            else -> {
                if (viewV != 0 && viewV != a.current) {
                    Row(Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.artifact_old_version, viewV), color = c.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewV = 0; oldContent = null }) { Text(stringResource(R.string.artifact_back_current), color = c.subtext, fontSize = 13.sp) }
                        TextButton(onClick = {
                            scope.launch {
                                try {
                                    Api.restoreArtifact(a.id, viewV)
                                    viewV = 0; oldContent = null; reload++; onChanged()
                                } catch (e: Exception) { Toast.makeText(ctx, errorMessage(ctx, e), Toast.LENGTH_SHORT).show() }
                            }
                        }) { Text(stringResource(R.string.artifact_restore), color = c.userBubble, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                val canPreview = a.type == "html" || a.type == "svg"
                if (canPreview) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((i, label) in listOf(R.string.artifact_preview, R.string.artifact_code).withIndex()) {
                            Surface(
                                shape = RoundedCornerShape(50), color = if (tab == i) c.userBubble else c.surface,
                                border = BorderStroke(1.dp, c.border),
                                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { tab = i },
                            ) {
                                Text(
                                    stringResource(label), color = if (tab == i) c.onUserBubble else c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                                )
                            }
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp)) {
                    if (canPreview && tab == 0) {
                        SandboxView(a.type, content, Modifier.fillMaxSize())
                    } else {
                        // code always reads left to right, and scrolls both ways
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            SelectionContainer {
                                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(16.dp)) {
                                    Text(content, color = c.text, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp, softWrap = false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (versionsMenu && a != null) {
        AlertDialog(
            onDismissRequest = { versionsMenu = false },
            confirmButton = { TextButton(onClick = { versionsMenu = false }) { Text(stringResource(R.string.cancel)) } },
            title = { Text(stringResource(R.string.artifact_versions)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    for ((v, at) in a.versions) {
                        Text(
                            "${stringResource(R.string.artifact_version_n, v)}${if (v == a.current) " ✓" else ""} · ${timeText(at)}",
                            color = c.text, fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                                versionsMenu = false
                                if (v == a.current) { viewV = 0; oldContent = null }
                                else scope.launch {
                                    try { oldContent = Api.artifactVersion(a.id, v); viewV = v } catch (e: Exception) { Toast.makeText(ctx, errorMessage(ctx, e), Toast.LENGTH_SHORT).show() }
                                }
                            }.padding(vertical = 12.dp, horizontal = 4.dp),
                        )
                    }
                }
            },
        )
    }
    if (renaming && a != null) {
        var name by remember { mutableStateOf(a.title) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.artifact_rename)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    renaming = false
                    scope.launch {
                        try { Api.renameArtifact(a.id, name.trim()); reload++; onChanged() } catch (e: Exception) { Toast.makeText(ctx, errorMessage(ctx, e), Toast.LENGTH_SHORT).show() }
                    }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (deleting && a != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.artifact_delete_title)) },
            text = { Text(stringResource(R.string.artifact_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    scope.launch {
                        try { Api.deleteArtifact(a.id); onChanged(); onBack() } catch (e: Exception) { Toast.makeText(ctx, errorMessage(ctx, e), Toast.LENGTH_SHORT).show() }
                    }
                }) { Text(stringResource(R.string.delete), color = Color(0xFFD64545)) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
