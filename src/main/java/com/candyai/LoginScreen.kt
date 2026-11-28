package com.candyai

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** The first screen: Candy peeking over her pillow, and three ways in. Only the guest way works for now. */
@Composable
fun LoginScreen(onGuest: () -> Unit, onGoogle: () -> Unit, googleBusy: Boolean = false, googleError: String? = null) {
    val c = LocalCandy.current
    // where she looks: the last touch; a few seconds after it she goes back to looking around by herself
    var gaze by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(gaze) {
        if (gaze != null) {
            delay(3500)
            gaze = null
        }
    }

    Box(
        Modifier.fillMaxSize().background(c.bg).pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    // watch only (Initial pass, nothing is consumed), so the buttons still get their touches
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    val p = e.changes.firstOrNull { it.pressed }?.position
                    if (p != null) gaze = Offset(p.x / size.width * 2f - 1f, p.y / size.height * 2f - 1f)
                }
            }
        },
    ) {
        PeekCandy(gaze, Modifier.fillMaxSize())

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(0f to Color.Transparent, 0.3f to c.bg.copy(alpha = 0.94f), 1f to c.bg),
                    ),
                )
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 80.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.login_title), color = c.text, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(stringResource(R.string.login_subtitle), color = c.subtext, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))

            Button(
                onClick = onGuest,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.cta, contentColor = c.onCta),
            ) {
                Icon(Icons.Rounded.Person, null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.login_guest), style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(
                onClick = onGoogle,
                enabled = !googleBusy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, c.border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = c.text),
            ) {
                Icon(Icons.Rounded.Email, null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.login_google), style = MaterialTheme.typography.bodyLarge)
            }
            if (googleError != null) {
                Text(googleError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
            SoonButton(Icons.Rounded.Favorite, stringResource(R.string.login_candy))
        }
    }
}

/** A way in that is not ready yet: visible, greyed out, with a small "soon" tag. */
@Composable
private fun SoonButton(icon: ImageVector, label: String) {
    val c = LocalCandy.current
    OutlinedButton(
        onClick = {},
        enabled = false,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, c.border),
        colors = ButtonDefaults.outlinedButtonColors(disabledContentColor = c.subtext.copy(alpha = 0.8f)),
    ) {
        Icon(icon, null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Surface(shape = CircleShape, color = c.surface) {
            Text(stringResource(R.string.login_soon), color = c.subtext, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
        }
    }
}
