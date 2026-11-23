package com.candyai

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Is Candy the phone's main assistant? (Then her screenshots need no "share your screen" prompt.) null = the phone would not say. */
private fun isMainAssistant(ctx: android.content.Context): Boolean? = try {
    val cr = ctx.contentResolver
    val v = Settings.Secure.getString(cr, "assistant") ?: Settings.Secure.getString(cr, "voice_interaction_service")
    if (v == null) null else v.contains(ctx.packageName)
} catch (_: Exception) { null }

/** Settings card for Candy the agent: the screen-control service, the assistant status, and hiding the floating Candy's notification. */
@Composable
fun AgentSettingsCard() {
    val c = LocalCandy.current
    val ctx = LocalContext.current
    var serviceOn by remember { mutableStateOf(CandyAgentService.enabled(ctx)) }
    var assistant by remember { mutableStateOf(isMainAssistant(ctx)) }
    // the person comes back from the system settings screen: re-read every moment while this card is on screen
    LaunchedEffect(Unit) {
        while (true) {
            serviceOn = CandyAgentService.enabled(ctx)
            assistant = isMainAssistant(ctx)
            delay(1500)
        }
    }
    fun open(i: Intent) { try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { } }

    SettingsCardPublic {
        Text(stringResource(R.string.agent_settings_title), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.agent_settings_hint), color = c.subtext, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))

        Text(stringResource(if (serviceOn) R.string.agent_svc_on else R.string.agent_svc_off), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (!serviceOn) {
            Text(stringResource(R.string.agent_restricted_hint), color = c.subtext, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            Column(Modifier.fillMaxWidth()) {
                TextButton(onClick = { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text(stringResource(R.string.agent_enable), color = c.userBubble) }
                TextButton(onClick = { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))) }) {
                    Text(stringResource(R.string.agent_app_info), color = c.userBubble)
                }
            }
        }

        assistant?.let {
            Text(
                stringResource(if (it) R.string.agent_assistant_on else R.string.agent_assistant_off), color = c.text, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp),
            )
        }

        // "ask once, then always": the kinds Candy may do without asking
        var auto by remember { mutableStateOf(Store.autoActions) }
        Text(stringResource(R.string.agent_auto_title), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
        Text(stringResource(R.string.agent_auto_hint), color = c.subtext, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
        for (g in AgentActions.GROUPS) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(when (g) { "clock" -> R.string.agent_g_clock; "calendar" -> R.string.agent_g_calendar; "phone" -> R.string.agent_g_phone; "agent" -> R.string.agent_g_agent; else -> R.string.agent_g_open }),
                    color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f),
                )
                Switch(checked = g in auto, onCheckedChange = { on -> auto = if (on) auto + g else auto - g; Store.autoActions = auto })
            }
        }

        if (Build.VERSION.SDK_INT >= 26) {
            Text(stringResource(R.string.agent_hide_notif_hint), color = c.subtext, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            TextButton(onClick = {
                open(
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, "candy_overlay_quiet"),
                )
            }) { Text(stringResource(R.string.agent_hide_notif), color = c.userBubble) }
        }
    }
}
