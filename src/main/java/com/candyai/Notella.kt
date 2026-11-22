package com.candyai

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Notella: notebooks whose sources (files or pasted text) Candy answers from, and nothing else.
 * [onOpenChat] opens an existing notebook chat, [onNewChat] starts an empty chat that belongs to the notebook.
 */
@Composable
fun NotellaScreen(onBack: () -> Unit, onOpenChat: (String) -> Unit, onNewChat: (String) -> Unit) {
    val c = LocalCandy.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var notebooks by remember { mutableStateOf<List<Notebook>?>(null) }
    var sources by remember { mutableStateOf<List<Source>>(emptyList()) }
    var chats by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var openId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Notebook?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pasting by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val open = notebooks?.firstOrNull { it.id == openId }

    LaunchedEffect(reload, openId) {
        try {
            notebooks = Api.notebooks()
            val id = openId
            if (id != null) {
                sources = Api.sources(id)
                chats = Api.conversations().filter { it.notebookId == id }
            }
            error = null
        } catch (e: Exception) {
            error = errorMessage(ctx, e)
        }
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val id = openId
        if (uri != null && id != null) {
            busy = true
            error = null
            scope.launch {
                try {
                    val p = AttachKit.fromDocument(ctx, uri)
                    if (p == null) error = ctx.getString(R.string.attach_fail)
                    else Api.addSource(id, p.name, b64 = p.b64)
                } catch (e: Exception) {
                    error = errorMessage(ctx, e)
                }
                busy = false
                reload++
            }
        }
    }

    BackHandler { if (openId != null) openId = null else onBack() }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (openId != null) openId = null else onBack() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = c.text)
            }
            if (open != null) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(projectColor(open.color)))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                open?.name ?: stringResource(R.string.notella),
                color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                style = TextStyle(textDirection = TextDirection.Content),
            )
            if (open != null) {
                IconButton(onClick = { editing = open }) { Icon(Icons.Rounded.Edit, stringResource(R.string.notella_edit), tint = c.subtext) }
            } else {
                IconButton(onClick = { creating = true }) { Icon(Icons.Rounded.Add, stringResource(R.string.notella_new), tint = c.accent) }
            }
        }

        val list = notebooks
        when {
            list == null && error != null -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error ?: "", color = c.subtext, fontSize = 14.sp)
                TextButton(onClick = { reload++ }) { Text(stringResource(R.string.retry), color = c.userBubble) }
            }
            list == null -> Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = c.accent, strokeWidth = 3.dp) }
            open != null -> Column(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
                    item {
                        Text(stringResource(R.string.notella_sources), color = c.subtext, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    if (sources.isEmpty()) {
                        item { Text(stringResource(R.string.notella_no_sources), color = c.subtext, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp)) }
                    }
                    items(sources, key = { it.id }) { src ->
                        Surface(
                            shape = RoundedCornerShape(12.dp), color = c.surface, border = BorderStroke(1.dp, c.border),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        ) {
                            Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                                    Text(src.name, color = c.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
                                    Text(sizeLabel(src.chars), color = c.subtext, fontSize = 11.sp)
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        try { Api.deleteSource(src.id) } catch (_: Exception) { }
                                        reload++
                                    }
                                }) { Icon(Icons.Rounded.Close, stringResource(R.string.notella_source_delete), tint = c.subtext, modifier = Modifier.size(18.dp)) }
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(enabled = !busy, onClick = { pickFile.launch("*/*") }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.Add, null, tint = c.accent)
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.notella_add_file), color = c.text)
                            }
                            OutlinedButton(enabled = !busy, onClick = { pasting = true }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.Add, null, tint = c.accent)
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.notella_add_text), color = c.text)
                            }
                        }
                        if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = c.accent, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.notella_adding), color = c.subtext, fontSize = 13.sp)
                        }
                        error?.let { Text(it, color = c.userBubble, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp)) }
                    }
                    if (chats.isNotEmpty()) {
                        item { Text(stringResource(R.string.notella_chats), color = c.subtext, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)) }
                        items(chats, key = { "c" + it.id }) { conv ->
                            Text(
                                conv.title, color = c.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpenChat(conv.id) }.padding(horizontal = 10.dp, vertical = 12.dp),
                                style = TextStyle(textDirection = TextDirection.Content),
                            )
                        }
                    }
                }
                Button(
                    onClick = { onNewChat(open.id) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = projectColor(open.color), contentColor = Color.White),
                ) {
                    Text(stringResource(R.string.notella_chat), fontWeight = FontWeight.SemiBold)
                }
            }
            list.isEmpty() -> Text(stringResource(R.string.notella_empty), color = c.subtext, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(32.dp))
            else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id }) { nb ->
                    Surface(
                        shape = RoundedCornerShape(18.dp), color = c.surface, border = BorderStroke(1.dp, c.border),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { openId = nb.id },
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(R.drawable.ic_notella), null, Modifier.size(46.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(nb.name, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
                                Text(stringResource(R.string.notella_counts, nb.sources, nb.chats), color = c.subtext, fontSize = 12.sp)
                            }
                            Box(Modifier.size(14.dp).clip(CircleShape).background(projectColor(nb.color)))
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        val nb = editing
        NotebookEditor(
            notebook = nb,
            onDismiss = { creating = false; editing = null },
            onSave = { name, color ->
                creating = false; editing = null
                scope.launch {
                    try {
                        if (nb == null) openId = Api.createNotebook(name, color) else Api.updateNotebook(nb.id, name, color)
                    } catch (_: Exception) { }
                    reload++
                }
            },
            onDelete = nb?.let { n ->
                {
                    creating = false; editing = null; openId = null
                    scope.launch {
                        try { Api.deleteNotebook(n.id) } catch (_: Exception) { }
                        reload++
                    }
                }
            },
        )
    }

    if (pasting) {
        var name by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        val fieldColors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent, unfocusedBorderColor = c.border, cursorColor = c.accent)
        val fieldStyle = TextStyle(textDirection = TextDirection.Content, color = c.text, fontSize = 15.sp)
        AlertDialog(
            onDismissRequest = { pasting = false },
            containerColor = c.surface, titleContentColor = c.text,
            title = { Text(stringResource(R.string.notella_paste_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(80) }, singleLine = true,
                        label = { Text(stringResource(R.string.notella_paste_name), color = c.subtext) },
                        textStyle = fieldStyle, colors = fieldColors, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = body, onValueChange = { body = it }, minLines = 5, maxLines = 10,
                        placeholder = { Text(stringResource(R.string.notella_paste_body), color = c.subtext, fontSize = 13.sp) },
                        textStyle = fieldStyle, colors = fieldColors, modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = body.isNotBlank(), onClick = {
                    val id = openId
                    val n = name.trim().ifBlank { body.trim().take(30) }
                    val t = body
                    pasting = false
                    if (id != null) {
                        busy = true
                        error = null
                        scope.launch {
                            try { Api.addSource(id, n, text = t) } catch (e: Exception) { error = errorMessage(ctx, e) }
                            busy = false
                            reload++
                        }
                    }
                }) { Text(stringResource(R.string.save), color = c.userBubble) }
            },
            dismissButton = { TextButton(onClick = { pasting = false }) { Text(stringResource(R.string.cancel), color = c.subtext) } },
        )
    }
}

private fun sizeLabel(chars: Int): String = if (chars >= 1000) "${chars / 1000}K" else "$chars"

@Composable
private fun NotebookEditor(notebook: Notebook?, onDismiss: () -> Unit, onSave: (String, Int) -> Unit, onDelete: (() -> Unit)?) {
    val c = LocalCandy.current
    var name by remember { mutableStateOf(notebook?.name ?: "") }
    var color by remember { mutableIntStateOf(notebook?.color ?: 4) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface, titleContentColor = c.text,
        title = { Text(stringResource(if (notebook == null) R.string.notella_new else R.string.notella_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                    label = { Text(stringResource(R.string.notella_name), color = c.subtext) },
                    textStyle = TextStyle(textDirection = TextDirection.Content, color = c.text, fontSize = 15.sp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent, unfocusedBorderColor = c.border, cursorColor = c.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProjectColors.forEachIndexed { i, col ->
                        Box(Modifier.size(28.dp).clip(CircleShape).background(col).clickable { color = i }, contentAlignment = Alignment.Center) {
                            if (i == color) Box(Modifier.size(10.dp).clip(CircleShape).background(Color.White))
                        }
                    }
                }
                if (onDelete != null) TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.delete), color = c.userBubble) }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), color) }) { Text(stringResource(R.string.save), color = c.userBubble) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = c.subtext) } },
    )

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.surface, titleContentColor = c.text, textContentColor = c.subtext,
            title = { Text(stringResource(R.string.notella_delete_title)) },
            text = { Text(stringResource(R.string.notella_delete_body)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.delete), color = c.userBubble) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel), color = c.subtext) } },
        )
    }
}
