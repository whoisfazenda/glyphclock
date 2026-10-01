package dev.glyphalarm.ui

import dev.glyphalarm.data.tr
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glyphalarm.alarm.AlarmEngine
import dev.glyphalarm.alarm.AlarmScheduler
import dev.glyphalarm.data.TimerItem
import dev.glyphalarm.data.TimerRepo
import dev.glyphalarm.data.TimerState
import kotlinx.coroutines.delay

fun formatDuration(ms: Long, roundUp: Boolean = true): String {
    val total = if (roundUp) (ms + 999) / 1000 else ms / 1000
    val h = total / 3600
    val m = (total / 60) % 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
fun TimerScreen(creating: Boolean, onCreating: (Boolean) -> Unit, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val timers by TimerRepo.timers.collectAsStateWithLifecycle()
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(200); tick = System.currentTimeMillis() } }

    // Show the soonest running timer as a bar on the long glyph while this tab is open
    LaunchedEffect(timers, tick / 500) {
        val t = timers.filter { it.state == TimerState.RUNNING }.minByOrNull { it.remaining() }
        AlarmEngine.progress = t?.let { it.remaining().toFloat() / it.totalMs.coerceAtLeast(1) }
    }
    DisposableEffect(Unit) { onDispose { AlarmEngine.progress = null } }

    fun start(t: TimerItem) {
        val end = System.currentTimeMillis() + t.remaining()
        TimerRepo.upsert(ctx, t.copy(state = TimerState.RUNNING, endAt = end))
        AlarmScheduler.scheduleTimer(ctx, t.id, end)
    }

    if (creating || timers.isEmpty()) {
        TimerCreator(
            canCancel = timers.isNotEmpty(),
            onCancel = { onCreating(false) },
            onSettings = onSettings,
            onStart = { ms ->
                start(TimerItem(TimerRepo.newId(ctx), ms, remainingMs = ms))
                onCreating(false)
            },
        )
        return
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenTitle(tr("Таймер", "Timer")) { NIconButton(Ic.GEAR, onSettings) }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = navBarClearance() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(timers, key = { it.id }) { t -> TimerCard(t, tick, Modifier.animateItem(), ::start) }
        }
    }
}

@Composable
private fun TimerCard(t: TimerItem, tick: Long, modifier: Modifier, start: (TimerItem) -> Unit) {
    val n = LocalN.current
    val ctx = LocalContext.current
    val left = t.remaining(tick)
    val frac = if (t.totalMs == 0L) 0f else (left.toFloat() / t.totalMs).coerceIn(0f, 1f)
    val running = t.state == TimerState.RUNNING

    Row(modifier.fillMaxWidth().nCard(24.dp).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
            val tint = n.display
            val track = n.display.copy(alpha = 0.10f)
            Canvas(Modifier.fillMaxSize()) {
                val sw = 7.dp.toPx()
                val inset = sw / 2
                val arcSize = Size(size.width - sw, size.height - sw)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw))
                drawArc(tint, -90f, 360f * frac, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            }
            Text(formatDuration(left), style = NType.heading.copy(fontFamily = DotFont, fontSize = 22.sp), color = if (running) n.display else n.secondary)
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            NCaps(if (running) tr("Идёт", "Running") else if (left == 0L) tr("Завершён", "Finished") else tr("Пауза", "Paused"), color = if (running) n.display else n.secondary)
            Spacer(Modifier.height(4.dp))
            NMeta(tr("из ", "of ") + formatDuration(t.totalMs))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleButton(
                    onClick = {
                        if (running) {
                            AlarmScheduler.cancelTimer(ctx, t.id)
                            TimerRepo.upsert(ctx, t.copy(state = TimerState.PAUSED, remainingMs = t.remaining()))
                        } else start(t.copy(remainingMs = if (left == 0L) t.totalMs else left))
                    },
                    size = 50.dp, filled = true,
                ) { NIcon(if (running) Ic.PAUSE else Ic.PLAY, tint = n.bg, size = 22.dp) }
                CircleButton(
                    onClick = {
                        val extra = 60_000L
                        if (running) {
                            val end = t.endAt + extra
                            TimerRepo.upsert(ctx, t.copy(totalMs = t.totalMs + extra, endAt = end))
                            AlarmScheduler.scheduleTimer(ctx, t.id, end)
                        } else TimerRepo.upsert(ctx, t.copy(totalMs = t.totalMs + extra, remainingMs = t.remainingMs + extra))
                    },
                    size = 50.dp,
                ) { Text("+1", style = NType.bodyMedium.copy(fontSize = 14.sp, color = n.display)) }
                CircleButton(
                    onClick = {
                        AlarmScheduler.cancelTimer(ctx, t.id)
                        TimerRepo.upsert(ctx, t.copy(state = TimerState.PAUSED, remainingMs = t.totalMs))
                    },
                    size = 50.dp,
                ) { NIcon(Ic.RESET, size = 20.dp) }
                CircleButton(
                    onClick = { AlarmScheduler.cancelTimer(ctx, t.id); TimerRepo.delete(ctx, t.id) },
                    size = 50.dp,
                ) { NIcon(Ic.TRASH, tint = n.accent, size = 20.dp) }
            }
        }
    }
}

@Composable
private fun TimerCreator(canCancel: Boolean, onCancel: () -> Unit, onSettings: () -> Unit, onStart: (Long) -> Unit) {
    val n = LocalN.current
    val tap = rememberTap()
    var digits by remember { mutableStateOf("") } // up to 6 digits: HHMMSS, typed from the right like the stock Clock

    val padded = digits.padStart(6, '0')
    val h = padded.substring(0, 2).toInt()
    val m = padded.substring(2, 4).toInt()
    val s = padded.substring(4, 6).toInt()
    val totalMs = ((h * 3600L) + (m * 60L) + s) * 1000

    fun press(d: String) { if ((digits + d).trimStart('0').length <= 6) digits = (digits + d).trimStart('0') }

    BackHandler(enabled = canCancel) { onCancel() }
    Column(Modifier.fillMaxSize().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        ScreenTitle(if (canCancel) tr("Новый таймер", "New timer") else tr("Таймер", "Timer")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (canCancel) NIconButton(Ic.CLOSE, onCancel)
                NIconButton(Ic.GEAR, onSettings)
            }
        }
        Spacer(Modifier.weight(0.6f))
        Row(verticalAlignment = Alignment.Bottom) {
            UnitBlock("%02d".format(h), tr("ч", "h"), lit = h > 0)
            Spacer(Modifier.width(14.dp))
            UnitBlock("%02d".format(m), tr("м", "m"), lit = h > 0 || m > 0)
            Spacer(Modifier.width(14.dp))
            UnitBlock("%02d".format(s), tr("с", "s"), lit = totalMs > 0)
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to tr("1 мин", "1 min"), 5 to tr("5 мин", "5 min"), 10 to tr("10 мин", "10 min"), 30 to tr("30 мин", "30 min")).forEach { (min, label) ->
                Box(Modifier.nCard(999.dp).clickable { tap(); onStart(min * 60_000L) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    NText(label, style = NType.meta, color = n.primary)
                }
            }
        }
        Spacer(Modifier.weight(1f))

        // number pad in the style of the Nothing dialler: round keys, the action in the last row
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) { row.forEach { KeyButton(it) { press(it) } } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                CircleButton({ digits = digits.dropLast(1) }, size = KEY) { NIcon(Ic.BACKSPACE, tint = if (digits.isEmpty()) n.disabled else n.display) }
                KeyButton("0") { press("0") }
                CircleButton({ if (totalMs > 0) onStart(totalMs) }, size = KEY, filled = totalMs > 0) {
                    NIcon(Ic.PLAY, tint = if (totalMs > 0) n.bg else n.disabled, size = 28.dp)
                }
            }
        }
        Spacer(Modifier.height(navBarClearance() + 12.dp))
    }
}

private val KEY = 76.dp

@Composable
private fun UnitBlock(value: String, unit: String, lit: Boolean) {
    val n = LocalN.current
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = NType.dotDisplay.copy(fontSize = 52.sp), color = if (lit) n.display else n.disabled)
        Text(unit, style = NType.bodyMedium, color = n.secondary, modifier = Modifier.padding(start = 3.dp, bottom = 10.dp))
    }
}

@Composable
private fun KeyButton(label: String, onClick: () -> Unit) {
    CircleButton(onClick, size = KEY) { Text(label, style = NType.key, color = LocalN.current.display) }
}
