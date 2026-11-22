package com.candyai

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.launch
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

/** Everything adjustable lives here, so new options can simply be added as new cards. */
@Composable
fun SettingsScreen(
    side: String,
    onSide: (String) -> Unit,
    sendSide: String,
    onSendSide: (String) -> Unit,
    onClose: () -> Unit,
    onOpenMemory: () -> Unit,
    onServerChanged: () -> Unit,
) {
    val c = LocalCandy.current
    val ctx = LocalContext.current
    var overlayOn by remember { mutableStateOf(Store.overlayOn && android.provider.Settings.canDrawOverlays(ctx)) }
    var ctsOn by remember { mutableStateOf(Store.ctsOn) }
    var ctsSave by remember { mutableStateOf(Store.ctsSave) }
    var seeUnknown by remember { mutableStateOf(Store.seeUnknown) }

    // "try with a picture": opens the Circle to Search screen on a picture from the gallery instead of a real screenshot
    val ctsPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            ctx.startActivity(Intent(ctx, CtsActivity::class.java).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
    }

    fun openAssistantSettings() {
        val tries = listOf(
            android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS,
            android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
            android.provider.Settings.ACTION_SETTINGS,
        )
        for (a in tries) {
            try {
                ctx.startActivity(Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
            }
        }
    }

    fun setOverlay(on: Boolean) {
        if (!on) {
            Store.overlayOn = false
            CandyOverlay.stop(ctx)
            overlayOn = false
        } else if (android.provider.Settings.canDrawOverlays(ctx)) {
            Store.overlayOn = true
            CandyOverlay.start(ctx)
            overlayOn = true
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

            // language
            SettingsCard {
                val current = Store.language
                Text(stringResource(R.string.settings_language), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.settings_language_hint), color = c.subtext, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LangChip(stringResource(R.string.lang_ar), current == "ar", Modifier.weight(1f)) { if (current != "ar") Lang.change(ctx, "ar") }
                    LangChip(stringResource(R.string.lang_en), current == "en", Modifier.weight(1f)) { if (current != "en") Lang.change(ctx, "en") }
                }
            }

            // theme: switches at once, no restart
            SettingsCard {
                val mode = ThemeMode.mode
                Text(stringResource(R.string.settings_theme), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.settings_theme_hint), color = c.subtext, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemeChoice(Icons.Rounded.BrightnessAuto, stringResource(R.string.theme_system), mode == "system", Modifier.weight(1f)) { ThemeMode.set("system") }
                    ThemeChoice(Icons.Rounded.LightMode, stringResource(R.string.theme_light), mode == "light", Modifier.weight(1f)) { ThemeMode.set("light") }
                    ThemeChoice(Icons.Rounded.DarkMode, stringResource(R.string.theme_dark), mode == "dark", Modifier.weight(1f)) { ThemeMode.set("dark") }
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

            // what floating Candy looks like while she is tucked away
            if (overlayOn) SettingsCard {
                var skin by remember { mutableStateOf(TabSkin.of(Store.tabSkin)) }
                Text(stringResource(R.string.skin_title), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.skin_hint), color = c.subtext, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    for (k in TabSkin.values()) {
                        val on = skin == k
                        Column(
                            Modifier.clip(RoundedCornerShape(14.dp)).clickable { skin = k; Store.tabSkin = k.key }.padding(3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(c.bg)
                                    .border(if (on) 2.5.dp else 1.dp, if (on) c.accent else c.border, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center,
                            ) { SkinArt(k, 40.dp) }
                            Text(
                                stringResource(when (k) { TabSkin.Rect -> R.string.skin_rect; TabSkin.Donut -> R.string.skin_donut; TabSkin.Crown -> R.string.skin_crown; TabSkin.Bear -> R.string.skin_bear; else -> R.string.skin_faces }),
                                color = if (on) c.text else c.subtext, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
                if (skin == TabSkin.Faces) Text(stringResource(R.string.skin_faces_hint), color = c.subtext, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            }

            // Circle to Search
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.cts_title), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.cts_settings_hint), color = c.subtext, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Switch(checked = ctsOn, onCheckedChange = { ctsOn = it; Store.ctsOn = it }, modifier = Modifier.padding(start = 8.dp))
                }
                if (ctsOn) {
                    Text(stringResource(R.string.cts_keep_title), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LangChip(stringResource(R.string.cts_keep_no), !ctsSave, Modifier.weight(1f)) { ctsSave = false; Store.ctsSave = false }
                        LangChip(stringResource(R.string.cts_keep_yes), ctsSave, Modifier.weight(1f)) { ctsSave = true; Store.ctsSave = true }
                    }
                    Text(stringResource(R.string.cts_keep_hint), color = c.subtext, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    Text(stringResource(R.string.cts_setup_hint), color = c.subtext, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        TextButton(onClick = { openAssistantSettings() }) { Text(stringResource(R.string.cts_set_assistant), color = c.userBubble) }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { ctx.startActivity(Intent(ctx, EdgeActivity::class.java).putExtra("voice", true)) }) {
                                Text(stringResource(R.string.see_try_voice), color = c.userBubble)
                            }
                            TextButton(onClick = { ctsPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                                Text(stringResource(R.string.cts_try), color = c.userBubble, maxLines = 1)
                            }
                        }
                    }
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

            // which side the send / voice button is on
            SettingsCard {
                Text(stringResource(R.string.settings_send_side), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.settings_send_side_hint), color = c.subtext, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    SendOption(left = false, selected = sendSide != "left", label = stringResource(R.string.side_right)) { onSendSide("right") }
                    SendOption(left = true, selected = sendSide == "left", label = stringResource(R.string.side_left)) { onSendSide("left") }
                }
            }

            // app updates
            UpdateSection()

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

@Composable
private fun LangChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalCandy.current
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.clip(shape)
            .background(if (selected) c.accent.copy(alpha = 0.18f) else c.bg)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.border, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) c.text else c.subtext, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/** A tiny phone with the message box at the bottom: the round button on the left or on the right. */
@Composable
private fun SendOption(left: Boolean, selected: Boolean, label: String, onClick: () -> Unit) {
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
                Column(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.fillMaxWidth(0.65f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(c.subtext.copy(alpha = 0.25f)))
                    Box(Modifier.align(Alignment.End).fillMaxWidth(0.55f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(c.userBubble.copy(alpha = 0.55f)))
                    Box(Modifier.fillMaxWidth(0.8f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(c.subtext.copy(alpha = 0.25f)))
                }
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val circle: @Composable () -> Unit = { Box(Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(c.accent)) }
                    if (left) circle()
                    Box(Modifier.weight(1f).height(22.dp).clip(RoundedCornerShape(11.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(11.dp)))
                    if (!left) circle()
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

/** One theme button. When it is picked its icon spins and bounces, and the whole button pops, so the change feels playful. */
@Composable
private fun ThemeChoice(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalCandy.current
    val shape = RoundedCornerShape(16.dp)
    var taps by remember { mutableIntStateOf(0) }
    val spin = remember { Animatable(0f) }
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(taps) {
        if (taps == 0) return@LaunchedEffect
        launch {
            spin.snapTo(0f)
            spin.animateTo(360f, tween(650, easing = FastOutSlowInEasing))
        }
        bounce.animateTo(1.5f, tween(140))
        bounce.animateTo(1f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessLow))
    }
    val pop by animateFloatAsState(
        if (selected) 1f else 0.94f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "pop",
    )
    Column(
        modifier
            .graphicsLayer { scaleX = pop; scaleY = pop }
            .clip(shape)
            .background(if (selected) c.accent.copy(alpha = 0.18f) else c.bg)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.border, shape)
            .clickable {
                taps++
                onClick()
            }
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            icon,
            null,
            tint = if (selected) c.accent else c.subtext,
            modifier = Modifier.size(28.dp).graphicsLayer {
                rotationZ = spin.value
                scaleX = bounce.value
                scaleY = bounce.value
            },
        )
        Text(label, color = if (selected) c.text else c.subtext, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
