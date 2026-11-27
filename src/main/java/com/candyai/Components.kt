package com.candyai

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Surface
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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

// ───────────── shared pieces of the list-style screens (Memory, Projects, Notella, ...) ─────────────

/** The top bar of a screen: back arrow, title, and one optional action on the far side. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    dot: Color? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val c = LocalCandy.current
    Row(modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), onBack, tint = c.text)
        if (dot != null) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            title,
            color = c.text,
            style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Content),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = if (dot == null) 6.dp else 0.dp),
        )
        if (action != null) action()
    }
}

/** The "+" of a header: a filled round button, so it can be seen and tapped easily. */
@Composable
fun AddAction(desc: String, onClick: () -> Unit) {
    val c = LocalCandy.current
    Box(
        Modifier.size(CandyDims.tap).clip(CircleShape).background(c.cta).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = desc, tint = c.onCta)
    }
}

/** What a list shows when it is empty: a picture, one sentence, and (optionally) the button that fixes it. */
@Composable
fun EmptyState(
    icon: @Composable () -> Unit,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = LocalCandy.current
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Text(message, color = c.subtext, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = c.cta, contentColor = c.onCta),
                modifier = Modifier.height(48.dp),
            ) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(6.dp))
                Text(actionLabel)
            }
        }
    }
}

/** One row of a list: a picture, a name, a short line under it, an optional mark, and an arrow. At least 76dp tall. */
@Composable
fun ListCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = LocalCandy.current
    val shape = RoundedCornerShape(CandyDims.cardRadius)
    Surface(
        shape = shape,
        color = c.surface,
        border = BorderStroke(1.dp, c.border),
        modifier = modifier.fillMaxWidth().clip(shape).clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(Modifier.heightIn(min = 76.dp).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = c.text,
                    style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(subtitle, color = c.subtext, style = MaterialTheme.typography.bodySmall)
            }
            if (trailing != null) {
                trailing()
                Spacer(Modifier.width(8.dp))
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.subtext)
        }
    }
}
