package com.candyai

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * The conversations drawer: the list (pinned first), long press on a conversation for pin / rename / delete,
 * and at the bottom the settings icon next to the new-chat button.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationsDrawer(
    side: String,
    activeId: String?,
    isOpen: Boolean,
    reloadKey: Int,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
    onProjects: () -> Unit,
    onNotella: () -> Unit,
    onSugar: () -> Unit,
    onArtifacts: () -> Unit,
    onJars: () -> Unit,
    onDeleted: (String) -> Unit,
) {
    val c = LocalCandy.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var convs by remember { mutableStateOf<List<Conversation>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<Conversation?>(null) }
    var deleting by remember { mutableStateOf<Conversation?>(null) }
    var moving by remember { mutableStateOf<Conversation?>(null) }
    var projectColors by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    // the list is refreshed every time the drawer opens, and after every pin / rename / delete
    LaunchedEffect(isOpen, reloadKey, reload) {
        if (!isOpen) return@LaunchedEffect
        try {
            convs = Api.conversations().filter { it.notebookId == null }   // Notella chats live inside their notebook
            projectColors = try { Api.projects().associate { it.id to it.color } } catch (_: Exception) { projectColors }
            error = null
        } catch (e: Exception) {
            error = errorMessage(ctx, e)
        }
    }

    // the inner edge (the one facing the chat) is the rounded one; "start" is the right edge in Arabic and the left edge in English
    val roundStart = (side == "left") == (LocalLayoutDirection.current == LayoutDirection.Rtl)
    val shape = if (roundStart) RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
    else RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)

    ModalDrawerSheet(
        drawerShape = shape,
        drawerContainerColor = c.bg,
        drawerContentColor = c.text,
        drawerTonalElevation = 0.dp,
    ) {
        Text(
            stringResource(R.string.conversations),
            color = c.text,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )

        // the five tools as tiles (two rows instead of five full rows), so the chat list gets the room
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DrawerTile(R.drawable.ic_projects, stringResource(R.string.projects), onProjects, Modifier.weight(1f))
                DrawerTile(R.drawable.ic_notella, stringResource(R.string.notella), onNotella, Modifier.weight(1f))
                DrawerTile(R.drawable.ic_sugarnote, stringResource(R.string.sugar), onSugar, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DrawerTile(R.drawable.ic_artifacts, stringResource(R.string.artifacts), onArtifacts, Modifier.weight(1f))
                DrawerTile(null, stringResource(R.string.jars), onJars, Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val list = convs
            when {
                list == null && error != null -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error ?: "", color = c.subtext, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { reload++ }) { Text(stringResource(R.string.retry), color = c.userBubble) }
                }
                list == null -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = c.accent, strokeWidth = 3.dp)
                list.isEmpty() -> Text(
                    stringResource(R.string.empty_list),
                    color = c.subtext,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                    items(list, key = { it.id }) { conv ->
                        val active = conv.id == activeId
                        val itemShape = RoundedCornerShape(14.dp)
                        Box(Modifier.padding(vertical = 2.dp)) {
                            Surface(
                                shape = itemShape,
                                color = if (active) c.surface else Color.Transparent,
                                border = if (active) BorderStroke(1.dp, c.border) else null,
                                modifier = Modifier.fillMaxWidth().clip(itemShape).combinedClickable(
                                    onClick = { onOpen(conv.id) },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuFor = conv.id
                                    },
                                ),
                            ) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (conv.pinned) {
                                        Icon(Icons.Rounded.PushPin, null, tint = c.accent, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    val pc = conv.projectId?.let { projectColors[it] }
                                    if (pc != null) {
                                        Box(Modifier.size(9.dp).clip(CircleShape).background(projectColor(pc)))
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            conv.title,
                                            color = c.text,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = (if (active) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge)
                                                .copy(textDirection = TextDirection.Content),
                                        )
                                        Text(
                                            DateUtils.getRelativeTimeSpanString(conv.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                                            color = c.subtext,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                            DropdownMenu(
                                expanded = menuFor == conv.id,
                                onDismissRequest = { menuFor = null },
                                modifier = Modifier.background(c.surface),
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (conv.pinned) R.string.chat_unpin else R.string.chat_pin), color = c.text) },
                                    leadingIcon = { Icon(Icons.Rounded.PushPin, null, tint = c.subtext) },
                                    onClick = {
                                        menuFor = null
                                        scope.launch {
                                            try { Api.pinConversation(conv.id, !conv.pinned) } catch (_: Exception) { }
                                            reload++
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.project_move), color = c.text) },
                                    leadingIcon = { androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_projects), null, Modifier.size(20.dp)) },
                                    onClick = {
                                        menuFor = null
                                        moving = conv
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.chat_rename), color = c.text) },
                                    leadingIcon = { Icon(Icons.Rounded.Edit, null, tint = c.subtext) },
                                    onClick = {
                                        menuFor = null
                                        renaming = conv
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        menuFor = null
                                        deleting = conv
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = c.border)
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconButton(
                onClick = onSettings,
                modifier = Modifier.size(48.dp).clip(CircleShape).background(c.surface),
            ) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings), tint = c.subtext) }
            Button(
                onClick = onNew,
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.cta, contentColor = c.onCta),
            ) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.new_chat))
            }
        }
    }

    renaming?.let { conv ->
        var text by remember(conv.id) { mutableStateOf(conv.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = c.surface,
            titleContentColor = c.text,
            title = { Text(stringResource(R.string.chat_rename_title)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(60) },
                    singleLine = true,
                    textStyle = TextStyle(textDirection = TextDirection.Content, color = c.text, fontSize = 15.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.accent,
                        unfocusedBorderColor = c.border,
                        cursorColor = c.accent,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = {
                        val t = text.trim()
                        renaming = null
                        scope.launch {
                            try { Api.renameConversation(conv.id, t) } catch (_: Exception) { }
                            reload++
                        }
                    },
                ) { Text(stringResource(R.string.save), color = c.userBubble) }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.cancel), color = c.subtext) }
            },
        )
    }

    deleting?.let { conv ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = c.surface,
            titleContentColor = c.text,
            textContentColor = c.subtext,
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        try {
                            Api.deleteConversation(conv.id)
                            onDeleted(conv.id)
                        } catch (_: Exception) { }
                        reload++
                    }
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel), color = c.subtext) }
            },
        )
    }

    moving?.let { conv ->
        MoveToProjectDialog(
            current = conv.projectId,
            onDismiss = { moving = null },
            onPick = { pid ->
                moving = null
                scope.launch {
                    try { Api.moveToProject(conv.id, pid) } catch (_: Exception) { }
                    reload++
                }
            },
        )
    }
}

/** One tool in the drawer (projects, Notella...): a picture over a short name. [icon] null means the jar emoji. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerTile(icon: Int?, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalCandy.current
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = c.surface,
        border = BorderStroke(1.dp, c.border),
        modifier = modifier.clip(shape).combinedClickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp).heightIn(min = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            if (icon != null) {
                androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(icon), null, Modifier.size(30.dp))
            } else {
                Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { Text("🫙", style = MaterialTheme.typography.titleLarge) }
            }
            Text(
                label,
                color = c.text,
                style = MaterialTheme.typography.labelMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}
