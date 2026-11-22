package com.candyai

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

/** The colours a project can have (the server stores the index). */
val ProjectColors = listOf(
    Color(0xFFF7567C), Color(0xFFFF9F43), Color(0xFFFFC93C), Color(0xFF4CC38A),
    Color(0xFF1FB5C9), Color(0xFF5B8DEF), Color(0xFF9B6BF2), Color(0xFF8D6E63),
)

fun projectColor(i: Int): Color = ProjectColors[((i % ProjectColors.size) + ProjectColors.size) % ProjectColors.size]

/**
 * Projects: a list of projects; open one to see its chats, edit its name / colour / instructions, or start a chat inside it.
 * [onOpenChat] opens an existing chat, [onNewChat] starts an empty chat that will belong to the project.
 */
@Composable
fun ProjectsScreen(onBack: () -> Unit, onOpenChat: (String) -> Unit, onNewChat: (String) -> Unit) {
    val c = LocalCandy.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var projects by remember { mutableStateOf<List<Project>?>(null) }
    var convs by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var openId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Project?>(null) }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(reload) {
        try {
            projects = Api.projects()
            convs = Api.conversations()
            error = null
        } catch (e: Exception) {
            error = errorMessage(ctx, e)
        }
    }

    val open = projects?.firstOrNull { it.id == openId }
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
                open?.name ?: stringResource(R.string.projects),
                color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                style = TextStyle(textDirection = TextDirection.Content),
            )
            if (open != null) {
                IconButton(onClick = { editing = open }) { Icon(Icons.Rounded.Edit, stringResource(R.string.project_edit), tint = c.subtext) }
            } else {
                IconButton(onClick = { creating = true }) { Icon(Icons.Rounded.Add, stringResource(R.string.project_new), tint = c.accent) }
            }
        }

        val list = projects
        when {
            list == null && error != null -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error ?: "", color = c.subtext, fontSize = 14.sp)
                TextButton(onClick = { reload++ }) { Text(stringResource(R.string.retry), color = c.userBubble) }
            }
            list == null -> Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = c.accent, strokeWidth = 3.dp) }
            open != null -> {
                val mine = convs.filter { it.projectId == open.id }
                Column(Modifier.fillMaxSize()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp), color = c.surface, border = BorderStroke(1.dp, c.border),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { editing = open },
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(stringResource(R.string.project_instr_label), color = c.subtext, fontSize = 12.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                open.instructions.ifBlank { stringResource(R.string.project_no_instr) },
                                color = if (open.instructions.isBlank()) c.subtext else c.text, fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis,
                                style = TextStyle(textDirection = TextDirection.Content),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (mine.isEmpty()) {
                            Text(stringResource(R.string.project_no_chats), color = c.subtext, fontSize = 14.sp, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                        } else {
                            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                                items(mine, key = { it.id }) { conv ->
                                    Surface(
                                        shape = RoundedCornerShape(14.dp), color = Color.Transparent,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(14.dp)).clickable { onOpenChat(conv.id) },
                                    ) {
                                        Text(
                                            conv.title, color = c.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                                            style = TextStyle(textDirection = TextDirection.Content),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Button(
                        onClick = { onNewChat(open.id) },
                        modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = projectColor(open.color), contentColor = Color.White),
                    ) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.project_new_chat), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            list.isEmpty() -> Text(stringResource(R.string.project_empty), color = c.subtext, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(32.dp))
            else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id }) { pr ->
                    Surface(
                        shape = RoundedCornerShape(18.dp), color = c.surface, border = BorderStroke(1.dp, c.border),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { openId = pr.id },
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(R.drawable.ic_projects), null, Modifier.size(46.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(pr.name, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
                                Text(stringResource(R.string.project_chats, pr.count), color = c.subtext, fontSize = 12.sp)
                            }
                            Box(Modifier.size(14.dp).clip(CircleShape).background(projectColor(pr.color)))
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        val pr = editing
        ProjectEditor(
            project = pr,
            onDismiss = { creating = false; editing = null },
            onSave = { name, color, instr ->
                creating = false; editing = null
                scope.launch {
                    try {
                        if (pr == null) openId = Api.createProject(name, color, instr)
                        else Api.updateProject(pr.id, name, color, instr)
                    } catch (_: Exception) { }
                    reload++
                }
            },
            onDelete = pr?.let { p ->
                {
                    creating = false; editing = null; openId = null
                    scope.launch {
                        try { Api.deleteProject(p.id) } catch (_: Exception) { }
                        reload++
                    }
                }
            },
        )
    }
}

@Composable
private fun ProjectEditor(project: Project?, onDismiss: () -> Unit, onSave: (String, Int, String) -> Unit, onDelete: (() -> Unit)?) {
    val c = LocalCandy.current
    var name by remember { mutableStateOf(project?.name ?: "") }
    var color by remember { mutableIntStateOf(project?.color ?: 0) }
    var instr by remember { mutableStateOf(project?.instructions ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    val fieldColors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent, unfocusedBorderColor = c.border, cursorColor = c.accent)
    val fieldStyle = TextStyle(textDirection = TextDirection.Content, color = c.text, fontSize = 15.sp)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        titleContentColor = c.text,
        title = { Text(stringResource(if (project == null) R.string.project_new else R.string.project_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                    label = { Text(stringResource(R.string.project_name), color = c.subtext) },
                    textStyle = fieldStyle, colors = fieldColors, modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.project_color), color = c.subtext, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProjectColors.forEachIndexed { i, col ->
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(col)
                                .then(if (i == color) Modifier.padding(0.dp) else Modifier)
                                .clickable { color = i },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (i == color) Box(Modifier.size(10.dp).clip(CircleShape).background(Color.White))
                        }
                    }
                }
                OutlinedTextField(
                    value = instr, onValueChange = { instr = it.take(4000) }, minLines = 3, maxLines = 6,
                    label = { Text(stringResource(R.string.project_instructions), color = c.subtext) },
                    placeholder = { Text(stringResource(R.string.project_instructions_hint), color = c.subtext, fontSize = 13.sp) },
                    textStyle = fieldStyle, colors = fieldColors, modifier = Modifier.fillMaxWidth(),
                )
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.delete), color = c.userBubble) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), color, instr.trim()) }) { Text(stringResource(R.string.save), color = c.userBubble) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = c.subtext) } },
    )

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.surface, titleContentColor = c.text, textContentColor = c.subtext,
            title = { Text(stringResource(R.string.project_delete_title)) },
            text = { Text(stringResource(R.string.project_delete_body)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.delete), color = c.userBubble) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel), color = c.subtext) } },
        )
    }
}

/** "Move to project" picker for a chat; [onPick] gets the project id, or null to take the chat out of its project. */
@Composable
fun MoveToProjectDialog(current: String?, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    val c = LocalCandy.current
    var projects by remember { mutableStateOf<List<Project>?>(null) }
    LaunchedEffect(Unit) { projects = try { Api.projects() } catch (_: Exception) { emptyList() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface, titleContentColor = c.text,
        title = { Text(stringResource(R.string.project_move)) },
        text = {
            val list = projects
            if (list == null) CircularProgressIndicator(Modifier.size(24.dp), color = c.accent, strokeWidth = 3.dp)
            else if (list.isEmpty()) Text(stringResource(R.string.project_empty), color = c.subtext, fontSize = 14.sp)
            else LazyColumn {
                items(list, key = { it.id }) { pr ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(pr.id) }.padding(vertical = 12.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(projectColor(pr.color)))
                        Spacer(Modifier.width(10.dp))
                        Text(pr.name, color = c.text, fontSize = 15.sp, fontWeight = if (pr.id == current) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
                    }
                }
            }
        },
        confirmButton = {
            if (current != null) TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.project_remove), color = c.userBubble) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = c.subtext) } },
    )
}
