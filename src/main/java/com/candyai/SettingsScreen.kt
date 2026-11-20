package com.candyai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

/** Everything adjustable lives here, so new options can simply be added as new cards. */
@Composable
fun SettingsScreen(
    side: String,
    onSide: (String) -> Unit,
    onClose: () -> Unit,
    onOpenMemory: () -> Unit,
    onServerChanged: () -> Unit,
) {
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
            onClose()
        }
    }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), onClose, tint = c.text)
            Text(stringResource(R.string.settings), color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // memory
            SettingsCard(Modifier.clickable { onOpenMemory() }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Psychology, null, tint = c.accent, modifier = Modifier.size(28.dp))
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(stringResource(R.string.ai_mem_title), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.settings_memory_hint), color = c.subtext, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }

            // Candy over the other apps
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.overlay_title), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.overlay_hint), color = c.subtext, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Switch(checked = overlayOn, onCheckedChange = { setOverlay(it) }, modifier = Modifier.padding(start = 8.dp))
                }
            }

            // drawer side, with a small picture of each choice
            SettingsCard {
                Text(stringResource(R.string.settings_drawer_side), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.settings_drawer_side_hint), color = c.subtext, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    SideOption(left = false, selected = side != "left", label = stringResource(R.string.side_right)) { onSide("right") }
                    SideOption(left = true, selected = side == "left", label = stringResource(R.string.side_left)) { onSide("left") }
                }
            }

            // server
            SettingsCard {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.server_address)) },
                    textStyle = TextStyle(textDirection = TextDirection.Ltr, color = c.text, fontSize = 15.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.accent,
                        unfocusedBorderColor = c.border,
                        cursorColor = c.accent,
                        focusedLabelColor = c.subtext,
                        unfocusedLabelColor = c.subtext,
                    ),
                )
                TextButton(
                    onClick = {
                        val clean = url.trim().trimEnd('/')
                        if (clean.isNotEmpty() && clean != Store.serverUrl) {
                            Store.serverUrl = clean
                            Store.token = null // a token from another server is useless
                            onServerChanged()
                        }
                        Toast.makeText(ctx, ctx.getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.align(Alignment.End),
                ) { Text(stringResource(R.string.save), color = c.userBubble) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalCandy.current
    val shape = RoundedCornerShape(18.dp)
    Surface(
        shape = shape,
        color = c.surface,
        border = BorderStroke(1.dp, c.border),
        modifier = Modifier.fillMaxWidth().clip(shape).then(modifier),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/** A tiny phone: the open drawer on one side (list lines) and the chat on the other. Always drawn left-to-right so "left" really is left. */
@Composable
private fun SideOption(left: Boolean, selected: Boolean, label: String, onClick: () -> Unit) {
    val c = LocalCandy.current
    val frame = RoundedCornerShape(14.dp)
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                Modifier.size(width = 96.dp, height = 150.dp)
                    .clip(frame)
                    .background(c.bg)
                    .border(if (selected) 2.5.dp else 1.dp, if (selected) c.accent else c.border, frame),
            ) {
                // the drawer
                Column(
                    Modifier.align(if (left) Alignment.CenterStart else Alignment.CenterEnd)
                        .fillMaxHeight().width(48.dp)
                        .background(c.accent.copy(alpha = 0.2f))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    repeat(5) { i ->
                        Box(
                            Modifier.fillMaxWidth(if (i == 0) 1f else 0.75f).height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (i == 0) c.accent.copy(alpha = 0.7f) else c.subtext.copy(alpha = 0.4f)),
                        )
                    }
                }
                // the chat behind it
                Column(
                    Modifier.align(if (left) Alignment.CenterEnd else Alignment.CenterStart).width(48.dp).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(c.subtext.copy(alpha = 0.25f)))
                    Box(Modifier.fillMaxWidth(0.7f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(c.userBubble.copy(alpha = 0.55f)))
                }
            }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Icon(Icons.Rounded.CheckCircle, null, tint = c.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(label, color = if (selected) c.text else c.subtext, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}
