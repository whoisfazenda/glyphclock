package dev.glyphalarm.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ---- text ------------------------------------------------------------------------------------

/** Small grey section caption, sentence case like the system settings. */
@Composable
fun NLabel(text: String, modifier: Modifier = Modifier, color: Color = LocalN.current.secondary, align: TextAlign? = null) {
    Text(text, modifier, color = color, style = NType.label, textAlign = align)
}

@Composable
fun NMeta(text: String, modifier: Modifier = Modifier, color: Color = LocalN.current.secondary) {
    Text(text, modifier, color = color, style = NType.meta)
}

@Composable
fun NText(
    text: String, modifier: Modifier = Modifier, style: TextStyle = NType.body, color: Color = LocalN.current.display,
    align: TextAlign? = null, maxLines: Int = Int.MAX_VALUE,
) {
    Text(text, modifier, color = color, style = style, textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** Screen headline in the narrow serif. */
@Composable
fun Title(text: String, modifier: Modifier = Modifier, style: TextStyle = NType.title, color: Color = LocalN.current.display) {
    Text(text, modifier, color = color, style = style)
}

/** Small upper-case caption in the dot-matrix face: section names, states. */
@Composable
fun NCaps(text: String, modifier: Modifier = Modifier, color: Color = LocalN.current.secondary) {
    Text(text.uppercase(), modifier, color = color, style = NType.caps, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
}

// ---- icons (monoline, round caps) --------------------------------------------------------------

enum class Ic { ALARM, WORLD, TIMER, STOPWATCH, PLUS, BACK, GEAR, CLOSE, PLAY, PAUSE, RESET, LAP, BACKSPACE, TRASH, CHECK, MUSIC, CHEVRON, LABEL }

@Composable
fun NIcon(ic: Ic, modifier: Modifier = Modifier, tint: Color = LocalN.current.display, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        val w = 1.6f * s
        val st = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, p(x1, y1), p(x2, y2), w, StrokeCap.Round)
        fun poly(vararg v: Float, close: Boolean = false, fill: Boolean = false) {
            val path = Path().apply {
                moveTo(v[0] * s, v[1] * s)
                for (i in 2 until v.size step 2) lineTo(v[i] * s, v[i + 1] * s)
                if (close) close()
            }
            drawPath(path, tint, style = if (fill) Fill else st)
        }
        fun circle(cx: Float, cy: Float, r: Float, fill: Boolean = false) =
            drawCircle(tint, r * s, p(cx, cy), style = if (fill) Fill else st)

        when (ic) {
            Ic.ALARM -> {
                circle(12f, 13f, 7.5f); line(12f, 13f, 12f, 9f); line(12f, 13f, 14.6f, 14.6f)
                line(4.2f, 6.2f, 7.2f, 3.6f); line(19.8f, 6.2f, 16.8f, 3.6f)
            }
            Ic.WORLD -> {
                circle(12f, 12f, 8.6f); line(3.4f, 12f, 20.6f, 12f)
                drawOval(tint, p(7.6f, 3.4f), Size(8.8f * s, 17.2f * s), style = st)
            }
            Ic.TIMER -> {
                line(6f, 3.8f, 18f, 3.8f); line(6f, 20.2f, 18f, 20.2f)
                poly(7.2f, 3.8f, 7.2f, 7.4f, 12f, 12f, 7.2f, 16.6f, 7.2f, 20.2f)
                poly(16.8f, 3.8f, 16.8f, 7.4f, 12f, 12f, 16.8f, 16.6f, 16.8f, 20.2f)
            }
            Ic.STOPWATCH -> {
                circle(12f, 13.5f, 7.6f); line(10f, 3.6f, 14f, 3.6f); line(12f, 3.6f, 12f, 5.9f)
                line(12f, 13.5f, 14.8f, 10.7f); line(18f, 7.6f, 19.3f, 6.3f)
            }
            Ic.PLUS -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
            Ic.BACK -> { line(4.5f, 12f, 20f, 12f); poly(10.5f, 5.5f, 4f, 12f, 10.5f, 18.5f) }
            Ic.CHEVRON -> poly(9f, 5f, 16f, 12f, 9f, 19f)
            Ic.LABEL -> { poly(4f, 6f, 14f, 6f, 20.5f, 12f, 14f, 18f, 4f, 18f, close = true); circle(9f, 12f, 1.3f, fill = true) }
            Ic.GEAR -> {
                circle(12f, 12f, 3.2f); circle(12f, 12f, 7.4f)
                for (k in 0 until 8) {
                    val a = k * PI.toFloat() / 4f
                    line(12f + 8.6f * cos(a), 12f + 8.6f * sin(a), 12f + 10.6f * cos(a), 12f + 10.6f * sin(a))
                }
            }
            Ic.CLOSE -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            Ic.PLAY -> poly(8f, 5.2f, 18.6f, 12f, 8f, 18.8f, close = true, fill = true)
            Ic.PAUSE -> { line(9f, 5.5f, 9f, 18.5f); line(15f, 5.5f, 15f, 18.5f) }
            Ic.RESET -> {
                // counter-clockwise arrow: an open circle with the arrowhead in the upper left
                drawArc(tint, 180f, -318f, false, p(3f, 3f), Size(18f * s, 18f * s), style = st)
                poly(3.2f, 3.4f, 3.2f, 8.2f, 8f, 8.2f)
            }
            Ic.LAP -> { line(6f, 3.5f, 6f, 20.5f); poly(6f, 4.5f, 18f, 4.5f, 15f, 8.5f, 18f, 12.5f, 6f, 12.5f) }
            Ic.BACKSPACE -> {
                poly(9f, 6f, 20f, 6f, 20f, 18f, 9f, 18f, 3.5f, 12f, close = true)
                line(12.5f, 9.5f, 16.5f, 14.5f); line(16.5f, 9.5f, 12.5f, 14.5f)
            }
            Ic.TRASH -> {
                line(4.5f, 6.5f, 19.5f, 6.5f); poly(9f, 6.5f, 9f, 4f, 15f, 4f, 15f, 6.5f)
                poly(6.5f, 6.5f, 7.5f, 20f, 16.5f, 20f, 17.5f, 6.5f); line(10.3f, 10f, 10.3f, 16.5f); line(13.7f, 10f, 13.7f, 16.5f)
            }
            Ic.CHECK -> poly(5f, 12.5f, 10f, 17.5f, 19f, 7f)
            Ic.MUSIC -> { poly(9f, 17.5f, 9f, 5f, 19f, 3.5f, 19f, 16f); circle(6.5f, 17.5f, 2.5f); circle(16.5f, 16f, 2.5f) }
        }
    }
}

// ---- dot matrix clock digits ------------------------------------------------------------------

private val DOT_FONT: Map<Char, List<String>> = mapOf(
    '0' to listOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
    '1' to listOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
    '2' to listOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
    '3' to listOf("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
    '4' to listOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
    '5' to listOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
    '6' to listOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
    '7' to listOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
    '8' to listOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
    '9' to listOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
    ':' to listOf("0", "0", "1", "0", "1", "0", "0"),
    '.' to listOf("0", "0", "0", "0", "0", "0", "1"),
    '-' to listOf("000", "000", "000", "111", "000", "000", "000"),
    '+' to listOf("000", "010", "010", "111", "010", "010", "000"),
    ' ' to List(7) { "00" },
)

/** Clock digits built from round dots, the signature Nothing look. */
@Composable
fun DotText(text: String, modifier: Modifier = Modifier, color: Color = LocalN.current.display, maxPitch: Dp = 16.dp, showUnlit: Boolean = true) {
    val glyphs = remember(text) { text.map { DOT_FONT[it] ?: DOT_FONT[' ']!! } }
    val cols = remember(text) { glyphs.sumOf { it[0].length } + (glyphs.size - 1) }
    val unlit = color.copy(alpha = if (showUnlit) 0.12f else 0f)
    BoxWithConstraints(modifier) {
        val pitch = minOf(maxWidth / cols, maxPitch)
        Canvas(Modifier.size(pitch * cols, pitch * 7)) {
            val p = pitch.toPx()
            var x = 0
            for (g in glyphs) {
                val w = g[0].length
                for (r in 0 until 7) for (c in 0 until w) {
                    drawCircle(
                        if (g[r][c] == '1') color else unlit,
                        radius = p * 0.38f,
                        center = Offset((x + c + 0.5f) * p, (r + 0.5f) * p),
                    )
                }
                x += w + 1
            }
        }
    }
}

// ---- Phone (3a) Pro: the camera "washer" and the three glyph strips around it ----------------------

/**
 * Only what matters for the light show: the round camera ring and the glyph strips, lit exactly as the LEDs are driven.
 * C (20 LEDs): long arc on the upper left, bottom-left to top. B (5 LEDs): short arc on the lower left.
 * A (11 LEDs): the bar on the right, top to bottom.
 */
@Composable
fun GlyphPhone(frame: IntArray, modifier: Modifier = Modifier) {
    val n = LocalN.current
    Canvas(modifier.aspectRatio(1f)) {
        val s = size.width
        val c = Offset(s / 2f, s / 2f)
        val ringR = s * 0.335f

        // the washer: metal ring, dark disc, fine machining lines
        drawCircle(Color(0xFF2E2E31), ringR, c)
        drawCircle(Color(0xFF5A5A5F), ringR, c, style = Stroke(s * 0.012f))
        drawCircle(Color(0xFF131315), ringR * 0.88f, c)
        drawCircle(Color(0xFF1E1E21), ringR * 0.74f, c, style = Stroke(1.dp.toPx()))
        drawCircle(Color(0xFF1E1E21), ringR * 0.60f, c, style = Stroke(1.dp.toPx()))
        // sensor block and lenses of the module
        drawRoundRect(
            Color(0xFF26262A),
            topLeft = Offset(c.x - ringR * 0.19f, c.y - ringR * 0.27f),
            size = Size(ringR * 0.96f, ringR * 0.62f),
            cornerRadius = CornerRadius(ringR * 0.12f),
        )
        drawRoundRect(Color.Black, topLeft = Offset(c.x + ringR * 0.22f, c.y - ringR * 0.12f), size = Size(ringR * 0.32f, ringR * 0.32f), cornerRadius = CornerRadius(ringR * 0.07f))
        fun lens(dx: Float, dy: Float, r: Float) {
            drawCircle(Color(0xFF3A3A3E), ringR * r, Offset(c.x + ringR * dx, c.y + ringR * dy))
            drawCircle(Color.Black, ringR * r * 0.80f, Offset(c.x + ringR * dx, c.y + ringR * dy))
            drawCircle(Color(0xFF17171C), ringR * r * 0.34f, Offset(c.x + ringR * dx, c.y + ringR * dy))
        }
        lens(-0.54f, -0.42f, 0.20f)
        lens(-0.58f, 0.10f, 0.25f)
        lens(-0.08f, -0.58f, 0.17f)

        // glyph strips
        val rg = ringR + s * 0.064f
        val sw = s * 0.038f
        val dim = Color(0xFF2B2B2E)
        fun lit(i: Int): Float = (frame.getOrElse(i) { 0 } / 4095f).coerceIn(0f, 1f)

        fun arcLed(index: Int, startDeg: Double, endDeg: Double, count: Int, i: Int) {
            val step = (endDeg - startDeg) / count
            val canvasStart = (startDeg + step * i - 90.0).toFloat()
            val sweep = step.toFloat() + 0.7f
            val tl = Offset(c.x - rg, c.y - rg)
            val sz = Size(rg * 2, rg * 2)
            drawArc(dim, canvasStart, sweep, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Butt))
            val v = lit(index)
            if (v > 0.01f) {
                drawArc(Color.White.copy(alpha = 0.20f * v), canvasStart, sweep, false, tl, sz, style = Stroke(sw * 2.1f, cap = StrokeCap.Butt))
                drawArc(Color.White.copy(alpha = 0.22f + 0.78f * v), canvasStart, sweep, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Butt))
            }
        }
        for (i in 0 until 20) arcLed(i, 278.0, 330.0, 20, i)        // C: bottom-left → top
        for (i in 0 until 5) arcLed(31 + i, 221.0, 245.0, 5, i)     // B: bottom-right → top-left
        for (i in 0 until 11) arcLed(20 + i, 76.0, 124.0, 11, i)    // A: right side, top → bottom
    }
}

// ---- controls --------------------------------------------------------------------------------

/** Light "key press" vibration for buttons and switches. */
@Composable
fun rememberTap(): () -> Unit {
    val v = LocalView.current
    return remember(v) { { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) } }
}

/** Fine tick for the time dial. */
@Composable
fun rememberTick(): () -> Unit {
    val v = LocalView.current
    return remember(v) { { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } }
}

@Composable
fun NSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val n = LocalN.current
    val x by animateDpAsState(if (checked) 24.dp else 3.dp, tween(150), label = "x")
    val track by animateColorAsState(if (checked) n.display else n.surfaceRaised, tween(150), label = "track")
    val knob by animateColorAsState(if (checked) n.bg else n.secondary, tween(150), label = "knob")
    val tap = rememberTap()
    Box(
        modifier
            .size(54.dp, 30.dp)
            .clip(CircleShape)
            .background(track, CircleShape)
            .clickable(remember { MutableInteractionSource() }, null) { tap(); onChange(!checked) },
    ) {
        Box(Modifier.offset(x, 0.dp).align(Alignment.CenterStart).size(24.dp).background(knob, CircleShape))
    }
}

@Composable
fun NButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    danger: Boolean = false,
    icon: Ic? = null,
    height: Dp = 52.dp,
) {
    val n = LocalN.current
    val shape = RoundedCornerShape(999.dp)
    val fg = when { danger && filled -> Color.White; danger -> n.accent; filled -> n.bg; else -> n.display }
    val tap = rememberTap()
    Row(
        modifier
            .height(height)
            .clip(shape)
            .then(
                when {
                    danger && filled -> Modifier.background(n.accent, shape)
                    filled -> Modifier.background(n.display, shape)
                    else -> Modifier.border(1.dp, if (danger) n.accent else n.borderVisible, shape)
                },
            )
            .clickable { tap(); onClick() }
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { NIcon(icon, tint = fg, size = 20.dp); Spacer(Modifier.width(8.dp)) }
        Text(text, color = fg, style = NType.bodyMedium, maxLines = 1, softWrap = false)
    }
}

/** Round icon/text button used by timers and the stopwatch. */
@Composable
fun CircleButton(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 72.dp, filled: Boolean = false, danger: Boolean = false, content: @Composable () -> Unit) {
    val n = LocalN.current
    val tap = rememberTap()
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .then(
                when {
                    danger -> Modifier.background(n.accent, CircleShape)
                    filled -> Modifier.background(n.display, CircleShape)
                    else -> Modifier.background(n.surface, CircleShape)
                },
            )
            .clickable { tap(); onClick() },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Outlined pill row; the chosen option is a solid white pill. */
@Composable
fun NSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val n = LocalN.current
    val tap = rememberTap()
    Row(modifier.fillMaxWidth().clip(CircleShape).background(n.bg, CircleShape).padding(4.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            val bg by animateColorAsState(if (on) n.display else Color.Transparent, tween(140), label = "seg")
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(bg, RoundedCornerShape(999.dp))
                    .clickable(remember { MutableInteractionSource() }, null) { tap(); onSelect(i) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) { Text(o, color = if (on) n.bg else n.secondary, style = NType.meta.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)) }
        }
    }
}

@Composable
fun DayChip(letter: String, on: Boolean, onClick: () -> Unit) {
    val n = LocalN.current
    val tap = rememberTap()
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .then(if (on) Modifier.background(n.display, CircleShape) else Modifier.border(1.dp, n.borderVisible, CircleShape))
            .clickable { tap(); onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(letter, color = if (on) n.bg else n.secondary, style = NType.meta.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)) }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(LocalN.current.border))
}

@Composable
fun NCard(modifier: Modifier = Modifier, padding: Dp = 20.dp, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().nCard().padding(padding)) { content() }
}

/** Big headline with an optional action on the right, like every Nothing settings page. */
@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 28.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Title(title, Modifier.weight(1f))
        trailing()
    }
}

/** Plain monoline icon button (no background), 48 dp touch target. */
@Composable
fun NIconButton(ic: Ic, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = LocalN.current.display) {
    Box(modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        NIcon(ic, tint = tint, size = 26.dp)
    }
}

/** Top row of inner pages: back arrow on the left, optional action on the right. */
@Composable
fun TopBar(onBack: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        NIconButton(Ic.BACK, onBack)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** "1 звук", "2 звука", "5 звуков". */
fun plural(n: Int, one: String, few: String, many: String): String {
    val m100 = n % 100
    val m10 = n % 10
    return when {
        m100 in 11..14 -> many
        m10 == 1 -> one
        m10 in 2..4 -> few
        else -> many
    }
}
