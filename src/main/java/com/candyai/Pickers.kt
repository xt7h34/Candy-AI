package com.candyai

import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private val PickInk = Color(0xFF2B1B20)
private val PickMuted = Color(0xFF9A8F95)
private val PickSoft = Color(0xFFF4F0F2)

/**
 * Pick a day, then a time, on one white card: a big number and month, a two-step progress bar, the month's grid;
 * then a big clock time and a dial. Only moments in the future can be picked. [onPicked] gets the moment as epoch millis.
 */
@Composable
fun DateTimePicker(initial: Long?, onPicked: (Long) -> Unit, onDismiss: () -> Unit) {
    val c = LocalCandy.current
    // the card, text and soft shapes follow the theme (the picker was always white)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val card = if (dark) Color(0xFF3A1F4D) else Color.White
    val ink = if (dark) Color(0xFFF3EAFB) else PickInk
    val muted = if (dark) Color(0xFFC9B6DB) else Color(0xFF6B5B5F)
    val soft = if (dark) Color(0xFF5A3573) else PickSoft
    val track = if (dark) Color(0xFF5A3573) else Color(0xFFE6E1E4)
    val zone = remember { ZoneId.systemDefault() }
    val locale = remember { Locale.getDefault() }
    val start = remember {
        val z = if (initial != null && initial > System.currentTimeMillis()) ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(initial), zone)
        else ZonedDateTime.now(zone).plusHours(1).withMinute(0).withSecond(0).withNano(0)
        z
    }
    var step by remember { mutableIntStateOf(0) }
    var date by remember { mutableStateOf(start.toLocalDate()) }
    var month by remember { mutableStateOf(YearMonth.from(start)) }
    var hour12 by remember { mutableIntStateOf(if (start.hour % 12 == 0) 12 else start.hour % 12) }
    var minute by remember { mutableIntStateOf(start.minute) }
    var pm by remember { mutableStateOf(start.hour >= 12) }
    var minuteMode by remember { mutableStateOf(false) }

    fun moment(): Long {
        val h24 = (hour12 % 12) + if (pm) 12 else 0
        return ZonedDateTime.of(date, LocalTime.of(h24, minute), zone).toInstant().toEpochMilli()
    }
    val valid = moment() > System.currentTimeMillis() + 30_000

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().padding(horizontal = 22.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxWidth().height(500.dp).graphicsLayer { rotationZ = 4f; translationX = 10f }
                    .clip(RoundedCornerShape(30.dp)).background(Color.White.copy(alpha = if (dark) 0.12f else 0.5f)),
            )
            Column(
                Modifier.fillMaxWidth().shadow(18.dp, RoundedCornerShape(30.dp), ambientColor = Color(0x44000000), spotColor = Color(0x44000000))
                    .clip(RoundedCornerShape(30.dp)).background(card).padding(22.dp),
            ) {
                // header: the chosen day, or the chosen time
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (step == 0) {
                        Column(Modifier.weight(1f)) {
                            Text(date.dayOfMonth.toString(), color = ink, fontSize = 46.sp, fontWeight = FontWeight.Bold, lineHeight = 50.sp)
                            Text(date.month.getDisplayName(TextStyle.FULL, locale) + " " + date.year, color = muted, fontSize = 17.sp)
                        }
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(11.dp)).background(soft)) {
                            Box(Modifier.fillMaxWidth().height(14.dp).background(c.cta))
                        }
                    } else {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text(hour12.toString(), color = if (!minuteMode) c.cta else ink, fontSize = 50.sp, fontWeight = FontWeight.Light, modifier = Modifier.clickable { minuteMode = false })
                            Text(":", color = ink, fontSize = 50.sp, fontWeight = FontWeight.Light)
                            Text("%02d".format(minute), color = if (minuteMode) c.cta else ink, fontSize = 50.sp, fontWeight = FontWeight.Light, modifier = Modifier.clickable { minuteMode = true })
                            Spacer(Modifier.width(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                AmPm("AM", !pm, soft, muted) { pm = false }
                                AmPm("PM", pm, soft, muted) { pm = true }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                // two steps: day, then time
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 0..1) Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (i <= step) c.cta else track))
                }
                Spacer(Modifier.height(16.dp))

                if (step == 0) {
                    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                    val thisMonth = YearMonth.now(zone)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val canPrev = month > thisMonth
                        Text(
                            if (rtl) "›" else "‹", color = if (canPrev) ink else muted.copy(alpha = 0.4f), fontSize = 26.sp,
                            modifier = Modifier.size(CandyDims.tap).clip(CircleShape).clickable(enabled = canPrev) { month = month.minusMonths(1) }.wrapContentSize(Alignment.Center),
                        )
                        Text(
                            month.month.getDisplayName(TextStyle.FULL, locale) + " " + month.year, color = ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Text(
                            if (rtl) "‹" else "›", color = ink, fontSize = 26.sp,
                            modifier = Modifier.size(CandyDims.tap).clip(CircleShape).clickable { month = month.plusMonths(1) }.wrapContentSize(Alignment.Center),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val first = WeekFields.of(locale).firstDayOfWeek
                    Row(Modifier.fillMaxWidth()) {
                        for (k in 0..6) {
                            val d: DayOfWeek = first.plus(k.toLong())
                            Text(d.getDisplayName(TextStyle.NARROW, locale), color = muted, fontSize = 13.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    val offset = (month.atDay(1).dayOfWeek.value - first.value + 7) % 7
                    val rows = (offset + month.lengthOfMonth() + 6) / 7
                    val today = LocalDate.now(zone)
                    for (r in 0 until 6) {
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            for (k in 0..6) {
                                val day = r * 7 + k - offset + 1
                                Box(Modifier.weight(1f).height(42.dp), contentAlignment = Alignment.Center) {
                                    if (r < rows && day in 1..month.lengthOfMonth()) {
                                        val d = month.atDay(day)
                                        val past = d.isBefore(today)
                                        val sel = d == date
                                        Box(
                                            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                                                .background(if (sel) c.cta else Color.Transparent)
                                                .clickable(enabled = !past) { date = d },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                day.toString(),
                                                color = if (sel) c.onCta else if (past) muted.copy(alpha = 0.45f) else ink,
                                                fontSize = 15.sp, fontWeight = if (d == today || sel) FontWeight.Bold else FontWeight.Normal,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Dial(
                            minuteMode = minuteMode, hour12 = hour12, minute = minute, accent = c.cta, inkColor = ink, softColor = soft, onAccent = c.onCta,
                            onHour = { hour12 = it }, onMinute = { minute = it }, onHourDone = { minuteMode = true },
                        )
                    }
                    if (!valid) Text(stringResource(R.string.picker_future), color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }

                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { if (step == 0) onDismiss() else step = 0 }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(if (step == 0) R.string.sugar_back else R.string.picker_back), color = ink, fontSize = 15.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = step == 0 || valid,
                        onClick = { if (step == 0) step = 1 else onPicked(moment()) },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.heightIn(min = 52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.cta, contentColor = c.onCta, disabledContainerColor = track, disabledContentColor = muted),
                    ) { Text(stringResource(if (step == 0) R.string.picker_next else R.string.sugar_next), fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
private fun AmPm(label: String, selected: Boolean, soft: Color, muted: Color, onClick: () -> Unit) {
    val c = LocalCandy.current
    Box(
        Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(10.dp)).background(if (selected) c.cta else soft).clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (selected) c.onCta else muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

/** The clock face: tap or drag to pick the hour (then it moves on to the minutes). */
@Composable
private fun Dial(
    minuteMode: Boolean, hour12: Int, minute: Int, accent: Color, inkColor: Color, softColor: Color, onAccent: Color,
    onHour: (Int) -> Unit, onMinute: (Int) -> Unit, onHourDone: () -> Unit,
) {
    val density = LocalDensity.current
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textAlign = android.graphics.Paint.Align.CENTER } }
    val ink = inkColor.toArgb()
    Canvas(
        Modifier.size(250.dp)
            .pointerInput(minuteMode) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun pick(p: Offset) {
                        val dx = p.x - size.width / 2f
                        val dy = p.y - size.height / 2f
                        var deg = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble()))
                        if (deg < 0) deg += 360.0
                        if (minuteMode) onMinute(((deg / 6.0).roundToInt()) % 60)
                        else onHour(((deg / 30.0).roundToInt() % 12).let { if (it == 0) 12 else it })
                    }
                    pick(down.position)
                    do {
                        val ev = awaitPointerEvent()
                        ev.changes.firstOrNull()?.let { pick(it.position); it.consume() }
                    } while (ev.changes.any { it.pressed })
                    if (!minuteMode) onHourDone()
                }
            },
    ) {
        val cx = size.width / 2
        val cy = size.height / 2
        val r = size.minDimension / 2
        drawCircle(softColor, r, Offset(cx, cy))
        val tipDeg = if (minuteMode) minute * 6.0 else (hour12 % 12) * 30.0
        val tipR = r * 0.78f
        val tx = cx + tipR * sin(Math.toRadians(tipDeg)).toFloat()
        val ty = cy - tipR * cos(Math.toRadians(tipDeg)).toFloat()
        drawLine(accent, Offset(cx, cy), Offset(tx, ty), strokeWidth = with(density) { 2.dp.toPx() })
        drawCircle(accent, with(density) { 4.dp.toPx() }, Offset(cx, cy))
        val onFive = !minuteMode || minute % 5 == 0
        drawCircle(accent, with(density) { 20.dp.toPx() }, Offset(tx, ty))
        if (!onFive) drawCircle(onAccent, with(density) { 3.dp.toPx() }, Offset(tx, ty))
        paint.textSize = with(density) { 16.sp.toPx() }
        for (i in 0 until 12) {
            val value = if (minuteMode) i * 5 else if (i == 0) 12 else i
            val a = Math.toRadians(i * 30.0)
            val x = cx + tipR * sin(a).toFloat()
            val y = cy - tipR * cos(a).toFloat()
            val selected = onFive && (if (minuteMode) minute == value else hour12 == value)
            paint.color = if (selected) onAccent.toArgb() else ink
            drawContext.canvas.nativeCanvas.drawText(if (minuteMode) "%02d".format(value) else value.toString(), x, y + paint.textSize / 3f, paint)
        }
    }
}
