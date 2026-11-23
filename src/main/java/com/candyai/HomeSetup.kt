package com.candyai

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Chooses "home": the phone's current place plus a radius the person picks with a slider.
 * Asks for the location permission (and "all the time", which leaving home with the app closed needs). Nothing leaves the phone.
 */
@Composable
fun HomeSetupDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalCandy.current
    var home by remember { mutableStateOf(HomeGeo.home(ctx)) }
    var radius by remember { mutableFloatStateOf((home?.radius ?: HomeGeo.DEFAULT_RADIUS).toFloat()) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var needBg by remember { mutableStateOf(home != null && !HomeGeo.hasBackground(ctx)) }

    fun locate() {
        busy = true
        failed = false
        HomeGeo.locate(ctx) { r ->
            busy = false
            if (r == null) failed = true
            else {
                HomeGeo.setHome(ctx, r.first, r.second, radius.toInt())
                home = HomeGeo.home(ctx)
                needBg = !HomeGeo.hasBackground(ctx)
            }
        }
    }

    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { locate() }
    val fineLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) failed = true
        else if (!HomeGeo.hasBackground(ctx)) bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        else locate()
    }
    fun start() {
        when {
            !HomeGeo.hasFine(ctx) -> fineLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            !HomeGeo.hasBackground(ctx) -> bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            else -> locate()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(stringResource(R.string.home_title), color = c.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.home_desc), color = c.subtext, fontSize = 13.sp)
                Button(onClick = { start() }, enabled = !busy) {
                    if (busy) CircularProgressIndicator(Modifier.height(18.dp), color = c.onAccent, strokeWidth = 2.dp)
                    else Text(stringResource(if (home == null) R.string.home_use_here else R.string.home_update_here))
                }
                if (home != null) Text(stringResource(R.string.home_set), color = c.text, fontSize = 14.sp)
                if (failed) Text(stringResource(R.string.home_failed), color = c.subtext, fontSize = 13.sp)
                if (needBg) {
                    Text(stringResource(R.string.home_need_bg), color = c.subtext, fontSize = 13.sp)
                    TextButton(onClick = {
                        try {
                            ctx.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Exception) { }
                    }) { Text(stringResource(R.string.home_open_settings), color = c.userBubble) }
                }
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.home_radius, radius.toInt()), color = c.text, fontSize = 14.sp)
                Slider(
                    value = radius,
                    onValueChange = { radius = (Math.round(it / 10f) * 10).toFloat() },
                    onValueChangeFinished = { if (home != null) HomeGeo.setRadius(ctx, radius.toInt()) },
                    valueRange = HomeGeo.MIN_RADIUS.toFloat()..HomeGeo.MAX_RADIUS.toFloat(),
                    steps = (HomeGeo.MAX_RADIUS - HomeGeo.MIN_RADIUS) / 10 - 1,
                )
                Text(stringResource(R.string.home_radius_hint), color = c.subtext, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = home != null, onClick = {
                if (home != null) HomeGeo.setRadius(ctx, radius.toInt())
                onSaved()
            }) { Text(stringResource(R.string.home_done), color = c.userBubble) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = c.subtext) } },
    )
}
