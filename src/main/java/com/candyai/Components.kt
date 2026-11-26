package com.candyai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun AppIconButton(
    icon: ImageVector,
    desc: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Icon(icon, contentDescription = desc, tint = tint)
    }
}

/** A round icon button with a 44dp touch area (the visible icon stays small). Use it instead of a hand-made Box(...).size(34.dp).clickable. */
@Composable
fun TapIcon(
    icon: Painter,
    desc: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = CandyDims.tap,
    iconSize: Dp = 22.dp,
    enabled: Boolean = true,
) {
    Box(
        modifier.size(size).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = desc, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
fun ErrorText(message: String, onRetry: (() -> Unit)? = null) {
    if (message.isBlank()) return
    androidx.compose.foundation.layout.Column {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        if (onRetry != null) {
            TextButton(onClick = onRetry) { Text(androidx.compose.ui.res.stringResource(R.string.retry)) }
        }
    }
}

object CandyBrand {
    /** The thin line under headers, so surfaces separate without heavy shadows. */
    @Composable
    fun hairline(): Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
}
