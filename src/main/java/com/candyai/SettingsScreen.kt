package com.candyai

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
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
    onOpenOutfits: () -> Unit,
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
            Text(stringResource(R.string.settings), color = c.text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 6.dp))
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = CandyDims.screenPad, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            // ---- look: language, theme, font ----
            SettingsSection(stringResource(R.string.settings_group_look)) {
                SettingsRows {
                    SettingRow(stringResource(R.string.settings_language), stringResource(R.string.settings_language_hint)) {
                        val current = Store.language
                        Segmented {
                            LangChip(stringResource(R.string.lang_ar), current == "ar") { if (current != "ar") Lang.change(ctx, "ar") }
                            LangChip(stringResource(R.string.lang_en), current == "en") { if (current != "en") Lang.change(ctx, "en") }
                        }
                    }
                    SettingDivider()
                    // theme: switches at once, no restart
                    SettingRow(stringResource(R.string.settings_theme), stringResource(R.string.settings_theme_hint)) {
                        val mode = ThemeMode.mode
                        Segmented {
                            ThemeChoice(Icons.Rounded.BrightnessAuto, stringResource(R.string.theme_system), mode == "system") { ThemeMode.set("system") }
                            ThemeChoice(Icons.Rounded.LightMode, stringResource(R.string.theme_light), mode == "light") { ThemeMode.set("light") }
                            ThemeChoice(Icons.Rounded.DarkMode, stringResource(R.string.theme_dark), mode == "dark") { ThemeMode.set("dark") }
                        }
                    }
                    SettingDivider()
                    // font: each choice is shown in its own font
                    SettingRow(stringResource(R.string.settings_font), stringResource(R.string.settings_font_hint)) {
                        val chosen = ThemeMode.font
                        Segmented {
                            FontChip("Amman", fontFamilyFor(ctx, "amman"), chosen != "cairo", true) { ThemeMode.chooseFont("amman") }
                            FontChip("Cairo", fontFamilyFor(ctx, "cairo"), chosen == "cairo", cairoAvailable(ctx)) { ThemeMode.chooseFont("cairo") }
                        }
                    }
                }
            }

            // ---- Candy: memory, floating Candy, Circle to Search, agent, wake word ----
            SettingsSection(stringResource(R.string.settings_group_candy)) {
                SettingsRows {
                    SettingRow(
                        stringResource(R.string.ai_mem_title),
                        stringResource(R.string.settings_memory_hint),
                        onClick = onOpenMemory,
                        leading = { Icon(Icons.Rounded.Psychology, null, tint = c.cta, modifier = Modifier.size(28.dp)) },
                        trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.subtext) },
                    )
                    SettingDivider()
                    // her outfits: opens the full-body screen
                    SettingRow(
                        stringResource(R.string.outfit_title),
                        stringResource(R.string.outfit_hint),
                        onClick = onOpenOutfits,
                        leading = { OutfitThumb(34.dp) },
                        trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.subtext) },
                    )
                    SettingDivider()
                    // Candy over the other apps (the whole row is the switch)
                    SettingRow(
                        stringResource(R.string.overlay_title),
                        stringResource(R.string.overlay_hint),
                        onClick = { setOverlay(!overlayOn) },
                        trailing = { Switch(checked = overlayOn, onCheckedChange = null) },
                    )
                    if (overlayOn) {
                        SettingDivider()
                        // what floating Candy looks like while she is tucked away
                        SettingRow(stringResource(R.string.skin_title), stringResource(R.string.skin_hint)) {
                var skin by remember { mutableStateOf(TabSkin.of(Store.tabSkin)) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    for (k in TabSkin.values()) {
                        val on = skin == k
                        Column(
                            Modifier.clip(RoundedCornerShape(14.dp)).clickable { skin = k; Store.tabSkin = k.key }.padding(3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(c.bg)
                                    .border(if (on) 2.5.dp else 1.dp, if (on) c.cta else c.border, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center,
                            ) { SkinArt(k, 40.dp) }
                            Text(
                                stringResource(when (k) { TabSkin.Rect -> R.string.skin_rect; TabSkin.Donut -> R.string.skin_donut; TabSkin.Crown -> R.string.skin_crown; TabSkin.Bear -> R.string.skin_bear; else -> R.string.skin_faces }),
                                color = if (on) c.text else c.subtext, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
                if (skin == TabSkin.Faces) Text(stringResource(R.string.skin_faces_hint), color = c.subtext, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
                        }
                    }
                    SettingDivider()
                    // Circle to Search
                    SettingRow(
                        stringResource(R.string.cts_title),
                        stringResource(R.string.cts_settings_hint),
                        onClick = { ctsOn = !ctsOn; Store.ctsOn = ctsOn },
                        trailing = { Switch(checked = ctsOn, onCheckedChange = null) },
                        expanded = ctsOn,
                    ) {
                    Text(stringResource(R.string.cts_keep_title), color = c.text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
                    Segmented {
                        LangChip(stringResource(R.string.cts_keep_no), !ctsSave) { ctsSave = false; Store.ctsSave = false }
                        LangChip(stringResource(R.string.cts_keep_yes), ctsSave) { ctsSave = true; Store.ctsSave = true }
                    }
                    Text(stringResource(R.string.cts_keep_hint), color = c.subtext, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    Text(stringResource(R.string.cts_setup_hint), color = c.subtext, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
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
                // Candy the agent: screen control, assistant status
                AgentSettingsCard()
                // wake word
                WakeSettingsCard()
            }

            // ---- layout: which side things are on, each with a small picture ----
            SettingsSection(stringResource(R.string.settings_group_layout)) {
                SettingsRows {
                    SettingRow(stringResource(R.string.settings_drawer_side), stringResource(R.string.settings_drawer_side_hint)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            SideOption(left = false, selected = side != "left", label = stringResource(R.string.side_right)) { onSide("right") }
                            SideOption(left = true, selected = side == "left", label = stringResource(R.string.side_left)) { onSide("left") }
                        }
                    }
                    SettingDivider()
                    SettingRow(stringResource(R.string.settings_send_side), stringResource(R.string.settings_send_side_hint)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            SendOption(left = false, selected = sendSide != "left", label = stringResource(R.string.side_right)) { onSendSide("right") }
                            SendOption(left = true, selected = sendSide == "left", label = stringResource(R.string.side_left)) { onSendSide("left") }
                        }
                    }
                }
            }

            // app updates
            UpdateSection()

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
internal fun SettingsCardPublic(content: @Composable ColumnScope.() -> Unit) = SettingsCard(content = content)

/** A small label, then what belongs under it. */
@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            color = LocalCandy.current.subtext,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 6.dp).padding(bottom = 8.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** One rounded card whose rows are separated by hairlines. */
@Composable
internal fun SettingsRows(content: @Composable ColumnScope.() -> Unit) {
    val c = LocalCandy.current
    Surface(
        shape = RoundedCornerShape(CandyDims.cardRadius),
        color = c.surface,
        border = BorderStroke(1.dp, c.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

@Composable
internal fun SettingDivider() {
    androidx.compose.material3.HorizontalDivider(color = LocalCandy.current.border)
}

/**
 * Every setting is one of these: a title and a hint, an optional icon before them and control after them,
 * and (optionally) a wider control underneath. With [onClick] the whole row is the target.
 */
@Composable
internal fun SettingRow(
    title: String,
    hint: String? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    expanded: Boolean = true,
    below: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val c = LocalCandy.current
    Column(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = CandyDims.rowMin)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = c.text, style = MaterialTheme.typography.titleMedium)
                if (hint != null) Text(hint, color = c.subtext, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
        if (below != null && expanded) Column(Modifier.fillMaxWidth().padding(top = 12.dp), content = below)
    }
}

/** One pill that holds a few choices side by side; the picked one is filled with the main colour. */
@Composable
private fun Segmented(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        content = content,
    )
}

/** One choice inside [Segmented]. [content] receives the text colour that fits the current state. */
@Composable
private fun RowScope.Segment(
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable (androidx.compose.ui.graphics.Color) -> Unit,
) {
    val c = LocalCandy.current
    val fill by androidx.compose.animation.animateColorAsState(
        if (selected) c.cta else androidx.compose.ui.graphics.Color.Transparent,
        label = "segment",
    )
    Box(
        Modifier.weight(1f)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clip(RoundedCornerShape(50))
            .background(fill)
            .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick)
            .heightIn(min = CandyDims.tapLarge)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { content(if (selected) c.onCta else c.subtext) }
}

@Composable
private fun SegText(text: String, color: androidx.compose.ui.graphics.Color, selected: Boolean) {
    Text(
        text,
        color = color,
        style = if (selected) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

/** A line of status with a dot: green when fine, red when not. */
@Composable
internal fun SettingStatus(text: String, ok: Boolean = true) {
    val c = LocalCandy.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (ok) Color(0xFF2E9E5B) else MaterialTheme.colorScheme.error))
        Spacer(Modifier.width(10.dp))
        Text(text, color = c.text, style = MaterialTheme.typography.titleSmall)
    }
}

/** A few lines of explanation inside a settings card. */
@Composable
internal fun SettingNote(text: String, error: Boolean = false) {
    Text(
        text,
        color = if (error) MaterialTheme.colorScheme.error else LocalCandy.current.subtext,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** A button inside a settings card: 48dp outlined, or 56dp filled when it is the main thing to do. */
@Composable
internal fun SettingButton(text: String, onClick: () -> Unit, primary: Boolean = false, full: Boolean = false, enabled: Boolean = true) {
    val c = LocalCandy.current
    val width = if (full) Modifier.fillMaxWidth() else Modifier
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        if (primary) {
            androidx.compose.material3.Button(
                onClick = onClick,
                enabled = enabled,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = c.cta, contentColor = c.onCta),
                modifier = width.heightIn(min = 56.dp),
            ) { Text(text) }
        } else {
            androidx.compose.material3.OutlinedButton(
                onClick = onClick,
                enabled = enabled,
                border = BorderStroke(1.dp, if (enabled) c.cta else c.border),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = c.cta),
                modifier = width.heightIn(min = 48.dp),
            ) { Text(text) }
        }
    }
}

/** One choice of a list of choices: the whole 52dp row picks it. */
@Composable
internal fun SettingRadio(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalCandy.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.RadioButton(
            selected = selected,
            onClick = null,
            colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = c.cta),
        )
        Spacer(Modifier.width(12.dp))
        Text(text, color = c.text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** A font choice that shows a sample written in that very font. */
@Composable
private fun RowScope.FontChip(name: String, family: FontFamily, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Segment(selected, onClick, enabled) { fg ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.font_sample), color = fg, style = MaterialTheme.typography.titleMedium.copy(fontFamily = family))
            Text(name, color = fg, style = MaterialTheme.typography.labelMedium)
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
                    .border(if (selected) 2.5.dp else 1.dp, if (selected) c.cta else c.border, frame),
            ) {
                // the drawer
                Column(
                    Modifier.align(if (left) Alignment.CenterStart else Alignment.CenterEnd)
                        .fillMaxHeight().width(48.dp)
                        .background(c.cta.copy(alpha = 0.2f))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    repeat(5) { i ->
                        Box(
                            Modifier.fillMaxWidth(if (i == 0) 1f else 0.75f).height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (i == 0) c.cta.copy(alpha = 0.7f) else c.subtext.copy(alpha = 0.4f)),
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
                Icon(Icons.Rounded.CheckCircle, null, tint = c.cta, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(label, color = if (selected) c.text else c.subtext, style = if (selected) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RowScope.LangChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Segment(selected, onClick) { fg -> SegText(label, fg, selected) }
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
                    .border(if (selected) 2.5.dp else 1.dp, if (selected) c.cta else c.border, frame),
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
                    val circle: @Composable () -> Unit = { Box(Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(c.cta)) }
                    if (left) circle()
                    Box(Modifier.weight(1f).height(22.dp).clip(RoundedCornerShape(11.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(11.dp)))
                    if (!left) circle()
                }
            }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Icon(Icons.Rounded.CheckCircle, null, tint = c.cta, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(label, color = if (selected) c.text else c.subtext, style = if (selected) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium)
        }
    }
}

/** One theme choice. When it is picked its icon spins and bounces, so the change feels playful. */
@Composable
private fun RowScope.ThemeChoice(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
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
    Segment(selected, { taps++; onClick() }) { fg ->
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                icon,
                null,
                tint = fg,
                modifier = Modifier.size(24.dp).graphicsLayer {
                    rotationZ = spin.value
                    scaleX = bounce.value
                    scaleY = bounce.value
                },
            )
            SegText(label, fg, selected)
        }
    }
}
