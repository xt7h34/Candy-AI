package com.candyai

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A small picture of her in the outfit she wears now (used in the settings row). */
@Composable
internal fun OutfitThumb(size: Dp) {
    val c = LocalCandy.current
    Box(Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(c.bg)) {
        Image(
            painter = painterResource(R.drawable.candy_stage_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painterResource(CandyOutfits.current.fullRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Candy standing in her café, full length, with the outfits below. Tapping an outfit shows it on her;
 * "Apply" makes it the one she wears everywhere in the app. Free, nothing to unlock.
 */
@Composable
fun OutfitScreen(onBack: () -> Unit) {
    val c = LocalCandy.current
    val outfits = Outfit.values()
    val applied = CandyOutfits.current
    var preview by remember { mutableStateOf(applied) }
    val glass = Color(0x99210B2C)

    Box(Modifier.fillMaxSize().background(c.bg)) {
        // the café looks the same in the light and dark themes
        Image(
            painter = painterResource(R.drawable.candy_stage_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0x66210B2C), Color.Transparent, Color.Transparent, Color(0x66210B2C))),
            ),
        )

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(glass).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), tint = Color.White)
                }
                Box(Modifier.padding(start = 10.dp).clip(RoundedCornerShape(20.dp)).background(glass).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.outfit_title), color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }

            // her, full length (the picture changes with a soft fade)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                Crossfade(targetState = preview, animationSpec = tween(220), label = "outfit-full") { o ->
                    Image(
                        painter = painterResource(o.fullRes),
                        contentDescription = stringResource(o.nameRes),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    )
                }
            }

            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(c.surface.copy(alpha = 0.94f))
                    .navigationBarsPadding()
                    .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 18.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.outfit_pick), color = c.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${preview.ordinal + 1} / ${outfits.size}", color = c.subtext, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (o in outfits) {
                        OutfitCard(o, selected = o == preview, applied = o == applied, modifier = Modifier.weight(1f)) { preview = o }
                    }
                }
                Spacer(Modifier.height(14.dp))
                val canApply = preview != applied
                Box(
                    Modifier.fillMaxWidth().height(52.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (canApply) c.cta else c.border)
                        .then(if (canApply) Modifier.clickable { CandyOutfits.choose(preview) } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(if (canApply) R.string.outfit_apply else R.string.outfit_applied),
                        color = if (canApply) c.onCta else c.subtext,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun OutfitCard(o: Outfit, selected: Boolean, applied: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalCandy.current
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.78f)
                .clip(RoundedCornerShape(14.dp))
                .background(c.bg)
                .border(if (selected) 2.5.dp else 1.dp, if (selected) c.cta else c.border, RoundedCornerShape(14.dp)),
        ) {
            Image(
                painter = painterResource(R.drawable.candy_stage_bg),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Image(
                painter = painterResource(o.fullRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
            // the one she is wearing now
            if (applied) {
                Box(
                    Modifier.align(Alignment.TopStart).padding(4.dp).size(18.dp).clip(CircleShape).background(c.cta),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("\u2713", color = c.onCta, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(
            stringResource(o.nameRes),
            color = if (selected) c.text else c.subtext,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}
