package com.candyai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.material3.Switch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun ConversationsScreen(onOpen: (String) -> Unit, onNew: () -> Unit, onOpenMemory: () -> Unit) {
    val c = LocalCandy.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<Conversation>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Conversation?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        error = null
        try {
            items = Api.conversations()
        } catch (e: Exception) {
            error = errorMessage(ctx, e)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.conversations),
                color = c.text, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenMemory) {
                Icon(Icons.Rounded.Psychology, contentDescription = stringResource(R.string.ai_mem_title), tint = c.subtext)
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings), tint = c.subtext)
            }
        }

        val list = items
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                error != null -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error ?: "", color = c.text, fontSize = 16.sp)
                    Button(
                        onClick = { items = null; reload++ },
                        modifier = Modifier.padding(top = 16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                    ) { Text(stringResource(R.string.retry)) }
                }
                list == null -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { CircularProgressIndicator(color = c.accent) }
                list.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { Text(stringResource(R.string.empty_list), color = c.subtext, fontSize = 16.sp) }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(list, key = { it.id }) { conv ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = c.surface,
                            border = BorderStroke(1.dp, c.border),
                            modifier = Modifier.fillMaxWidth().clickable { onOpen(conv.id) },
                        ) {
                            Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        conv.title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = TextStyle(textDirection = TextDirection.Content),
                                    )
                                    Text(
                                        DateUtils.getRelativeTimeSpanString(
                                            conv.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                                        ).toString(),
                                        color = c.subtext, fontSize = 13.sp,
                                    )
                                }
                                IconButton(onClick = { toDelete = conv }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete), tint = c.subtext)
                                }
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = onNew,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            shape = RoundedCornerShape(26.dp),
            contentPadding = PaddingValues(vertical = 14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Text(stringResource(R.string.new_chat), fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }
    }

    toDelete?.let { conv ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = c.surface,
            titleContentColor = c.text,
            textContentColor = c.subtext,
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    scope.launch {
                        try { Api.deleteConversation(conv.id) } catch (_: Exception) { }
                        reload++
                    }
                }) { Text(stringResource(R.string.delete), color = c.userBubble) }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel), color = c.subtext) }
            },
        )
    }

    if (showSettings) {
        SettingsDialog(onDismiss = { showSettings = false }, onSaved = { items = null; reload++ })
    }
}

@Composable
fun SettingsDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
    val c = LocalCandy.current
    val ctx = LocalContext.current
    var url by remember { mutableStateOf(Store.serverUrl) }
    var overlayOn by remember { mutableStateOf(Store.overlayOn && android.provider.Settings.canDrawOverlays(ctx)) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun setOverlay(on: Boolean) {
        if (!on) {
            Store.overlayOn = false
            CandyOverlay.stop(ctx)
            overlayOn = false
        } else if (android.provider.Settings.canDrawOverlays(ctx)) {
            Store.overlayOn = true
            CandyOverlay.start(ctx)
            overlayOn = true
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            // first time: send the person to the "display over other apps" screen; she appears when they come back
            Store.overlayOn = true
            Toast.makeText(ctx, ctx.getString(R.string.overlay_perm), Toast.LENGTH_LONG).show()
            ctx.startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        titleContentColor = c.text,
        title = { Text(stringResource(R.string.settings)) },
        text = {
          Column {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                singleLine = true,
                label = { Text(stringResource(R.string.server_address)) },
                textStyle = TextStyle(textDirection = TextDirection.Ltr, color = c.text, fontSize = 15.sp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.accent,
                    unfocusedBorderColor = c.border,
                    cursorColor = c.accent,
                    focusedLabelColor = c.subtext,
                    unfocusedLabelColor = c.subtext,
                ),
            )
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.overlay_title), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.overlay_hint), color = c.subtext, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Switch(checked = overlayOn, onCheckedChange = { setOverlay(it) }, modifier = Modifier.padding(start = 8.dp))
            }
          }
        },
        confirmButton = {
            TextButton(onClick = {
                val clean = url.trim().trimEnd('/')
                if (clean != Store.serverUrl) {
                    Store.serverUrl = clean
                    Store.token = null // a token from another server is useless
                }
                onDismiss()
                onSaved()
            }) { Text(stringResource(R.string.save), color = c.userBubble) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = c.subtext) }
        },
    )
}
