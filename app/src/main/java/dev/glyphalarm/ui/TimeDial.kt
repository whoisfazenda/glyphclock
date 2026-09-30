package dev.glyphalarm.ui

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.text.DateFormatSymbols
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Follows the phone's 12/24-hour setting and updates when it changes. */
@Composable
fun rememberIs24h(): Boolean {
    val ctx = LocalContext.current
    var v by remember { mutableStateOf(DateFormat.is24HourFormat(ctx)) }
    LifecycleResumeEffect(Unit) { v = DateFormat.is24HourFormat(ctx); onPauseOrDispose { } }
    return v
}

/** "07:30" in 24 h, "7:30" + "AM" in 12 h. */
fun formatClock(hour: Int, minute: Int, is24: Boolean): Pair<String, String> {
    if (is24) return "%02d:%02d".format(hour, minute) to ""
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    val ampm = DateFormatSymbols.getInstance().amPmStrings
    return "%d:%02d".format(h12, minute) to (if (hour < 12) ampm[0] else ampm[1])
}

/**
 * Round clock-face time picker. Drag or tap on the dial; hours first, then it flips to minutes.
 * 12-hour phones get AM/PM, 24-hour phones get the two-ring face (1–12 outside, 13–00 inside).
 */
@Composable
fun TimeDial(hour: Int, minute: Int, is24: Boolean, onChange: (hour: Int, minute: Int) -> Unit, modifier: Modifier = Modifier) {
    val n = LocalN.current
    var mode by remember { mutableIntStateOf(0) } // 0 = hours, 1 = minutes
    val curHour by rememberUpdatedState(hour)
    val curMinute by rememberUpdatedState(minute)
    val cb by rememberUpdatedState(onChange)
    val measurer = rememberTextMeasurer()
    val pm = hour >= 12
    val ampm = remember { DateFormatSymbols.getInstance().amPmStrings }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // header: HH : MM  [AM/PM]
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            val shownH = if (is24) "%02d".format(hour) else (if (hour % 12 == 0) 12 else hour % 12).toString().padStart(2, '0')
            TimeBox(shownH, selected = mode == 0) { mode = 0 }
            Text(":", color = n.secondary, style = NType.dotDisplay.copy(fontSize = 50.sp), modifier = Modifier.padding(horizontal = 6.dp))
            TimeBox("%02d".format(minute), selected = mode == 1) { mode = 1 }
            if (!is24) {
                Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmPmButton(ampm[0], on = !pm) { if (pm) cb(curHour - 12, curMinute) }
                    AmPmButton(ampm[1], on = pm) { if (!pm) cb(curHour + 12, curMinute) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        val accent = n.display
        Canvas(
            Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .pointerInput(mode, is24) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        fun apply(pos: Offset) {
                            val c = Offset(size.width / 2f, size.height / 2f)
                            val dx = pos.x - c.x
                            val dy = pos.y - c.y
                            var ang = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble()))
                            if (ang < 0) ang += 360.0
                            val dist = hypot(dx, dy) / (size.width / 2f)
                            if (mode == 0) {
                                val idx = ((ang + 15) / 30).toInt() % 12
                                val h = if (is24) {
                                    if (dist < 0.66f) (if (idx == 0) 0 else idx + 12) else (if (idx == 0) 12 else idx)
                                } else {
                                    val h12 = if (idx == 0) 12 else idx
                                    (h12 % 12) + (if (curHour >= 12) 12 else 0)
                                }
                                if (h != curHour) cb(h, curMinute)
                            } else {
                                val m = ((ang + 3) / 6).toInt() % 60
                                if (m != curMinute) cb(curHour, m)
                            }
                        }
                        apply(down.position)
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull() ?: break
                            if (ch.pressed) { apply(ch.position); ch.consume() } else { ch.consume(); break }
                        }
                        if (mode == 0) mode = 1
                    }
                },
        ) {
            val R = size.width / 2f
            val center = Offset(R, R)
            drawCircle(n.surface, R, center)
            drawCircle(n.border, R, center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))

            fun pos(angleDeg: Double, radius: Float) = Offset(
                center.x + radius * sin(Math.toRadians(angleDeg)).toFloat(),
                center.y - radius * cos(Math.toRadians(angleDeg)).toFloat(),
            )

            // selected value -> angle/radius
            val selAngle: Double
            val selRadius: Float
            if (mode == 0) {
                val idx = if (is24) (if (hour == 0 || hour == 12) 0 else hour % 12) else (hour % 12)
                selAngle = idx * 30.0
                selRadius = if (is24 && (hour == 0 || hour >= 13)) R * 0.50f else R * 0.80f
            } else {
                selAngle = minute * 6.0
                selRadius = R * 0.80f
            }
            val tip = pos(selAngle, selRadius)
            val knob = 21.dp.toPx()
            drawLine(accent, center, tip, strokeWidth = 2.dp.toPx())
            drawCircle(accent, 4.dp.toPx(), center)
            drawCircle(accent, knob, tip)
            if (mode == 1 && minute % 5 != 0) drawCircle(n.bg, 3.dp.toPx(), tip) // exact minute between labels

            fun label(text: String, at: Offset, selected: Boolean, small: Boolean = false) {
                val style = TextStyle(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Default, fontSize = if (small) 14.sp else 17.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) n.bg else n.primary,
                )
                val layout = measurer.measure(text, style)
                drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
            }

            if (mode == 0) {
                if (is24) {
                    for (i in 0 until 12) {
                        label(if (i == 0) "12" else "$i", pos(i * 30.0, R * 0.80f), selected = hour == (if (i == 0) 12 else i))
                        label(if (i == 0) "00" else "${i + 12}", pos(i * 30.0, R * 0.50f), selected = hour == (if (i == 0) 0 else i + 12), small = true)
                    }
                } else {
                    for (i in 0 until 12) {
                        val v = if (i == 0) 12 else i
                        label("$v", pos(i * 30.0, R * 0.80f), selected = (hour % 12 == i))
                    }
                }
            } else {
                for (i in 0 until 12) label("%02d".format(i * 5), pos(i * 30.0, R * 0.80f), selected = minute == i * 5)
            }
        }
    }
}

@Composable
private fun TimeBox(text: String, selected: Boolean, onClick: () -> Unit) {
    val n = LocalN.current
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(n.surface, RoundedCornerShape(20.dp))
            .then(if (selected) Modifier.border(1.5.dp, n.display, RoundedCornerShape(20.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (selected) n.display else n.secondary, style = NType.dotDisplay.copy(fontSize = 50.sp)) }
}

@Composable
private fun AmPmButton(text: String, on: Boolean, onClick: () -> Unit) {
    val n = LocalN.current
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (on) n.display else n.surface, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (on) n.bg else n.secondary, style = NType.bodyMedium) }
}
