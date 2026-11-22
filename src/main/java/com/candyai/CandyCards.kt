package com.candyai

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.cos
import kotlin.math.sin

private val CardInk = Color(0xFF2B1B20)
private val CardMuted = Color(0xFF9A8F95)

/**
 * Candy asks you something: a white card (with a tilted one behind it), a progress bar, the question, one answer to pick.
 * [step] / [total] draw the progress bar; [onNext] gets the index of the picked answer.
 */
@Composable
fun AskCard(
    question: String,
    options: List<String>,
    onNext: (Int) -> Unit,
    onClose: () -> Unit,
    step: Int = 1,
    total: Int = 1,
    nextLabel: String = stringResource(R.string.sugar_next),
    backLabel: String = stringResource(R.string.sugar_back),
    onBack: () -> Unit = onClose,
) {
    val c = LocalCandy.current
    var picked by remember { mutableIntStateOf(-1) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().padding(horizontal = 26.dp), contentAlignment = Alignment.Center) {
            // the tilted card behind
            Box(
                Modifier.fillMaxWidth().height(380.dp).graphicsLayer { rotationZ = -5f; translationX = -12f }
                    .clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha = 0.55f)),
            )
            Column(
                Modifier.fillMaxWidth().shadow(18.dp, RoundedCornerShape(28.dp), ambientColor = Color(0x44000000), spotColor = Color(0x44000000))
                    .clip(RoundedCornerShape(28.dp)).background(Color.White).padding(22.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        for (i in 1..total) {
                            Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (i <= step) c.userBubble else Color(0xFFE6E1E4)))
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).background(Color(0xFFB9B0B5)).clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) { Text("✕", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(22.dp))
                Text(stringResource(R.string.sugar_ask_label, step), color = CardInk, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(question, color = CardInk, fontSize = 19.sp, lineHeight = 27.sp, style = TextStyle(textDirection = TextDirection.Content))
                Spacer(Modifier.height(18.dp))
                Text(stringResource(R.string.sugar_ask_hint), color = CardMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(6.dp))
                options.forEachIndexed { i, label ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { picked = i }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(20.dp).clip(CircleShape).border(1.6.dp, if (picked == i) c.userBubble else CardInk, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (picked == i) Box(Modifier.size(10.dp).clip(CircleShape).background(c.userBubble)) }
                        Spacer(Modifier.width(12.dp))
                        Text(label, color = CardInk, fontSize = 15.sp, style = TextStyle(textDirection = TextDirection.Content))
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text(backLabel, color = CardInk, fontSize = 15.sp) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = picked >= 0,
                        onClick = { onNext(picked) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.userBubble, contentColor = Color.White, disabledContainerColor = Color(0xFFE6E1E4), disabledContentColor = Color.White),
                    ) { Text(nextLabel, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

/** "It worked": a sheet from the bottom with a scalloped badge and a check, a title, one line and a Done button. */
@Composable
fun DoneSheet(title: String, body: String, buttonLabel: String = stringResource(R.string.sugar_done_btn), onDone: () -> Unit) {
    val c = LocalCandy.current
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp).navigationBarsPadding().padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(30.dp)).background(Color.White)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ScallopedCheck(c.userBubble)
                Spacer(Modifier.height(16.dp))
                Text(title, color = CardInk, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text(body, color = CardMuted, fontSize = 14.sp, lineHeight = 20.sp, style = TextStyle(textDirection = TextDirection.Content, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
                Spacer(Modifier.height(26.dp))
                Button(
                    onClick = onDone, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CardInk, contentColor = Color.White),
                ) { Text(buttonLabel, fontSize = 15.sp) }
            }
        }
    }
}

@Composable
private fun ScallopedCheck(color: Color) {
    Canvas(Modifier.size(84.dp)) {
        val cx = size.width / 2
        val cy = size.height / 2
        val r = size.minDimension * 0.44f
        val path = Path()
        val n = 12
        for (i in 0..360) {
            val a = Math.toRadians(i.toDouble())
            val rr = r * (1f + 0.07f * cos(n * a).toFloat())
            val x = cx + rr * cos(a).toFloat()
            val y = cy + rr * sin(a).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color.copy(alpha = 0.28f))
        val tick = Path().apply {
            moveTo(size.width * 0.34f, size.height * 0.52f)
            lineTo(size.width * 0.45f, size.height * 0.63f)
            lineTo(size.width * 0.67f, size.height * 0.39f)
        }
        drawPath(tick, color, style = Stroke(width = size.width * 0.075f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
