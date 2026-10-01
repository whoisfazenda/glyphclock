package dev.glyphalarm.ui

import dev.glyphalarm.data.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glyphalarm.data.StopwatchRepo

private fun stopwatchParts(ms: Long): Pair<String, String> {
    val h = ms / 3_600_000
    val m = (ms / 60_000) % 60
    val s = (ms / 1000) % 60
    val cs = (ms / 10) % 100
    val main = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    return main to ".%02d".format(cs)
}

private fun stopwatchText(ms: Long) = stopwatchParts(ms).let { it.first + it.second }

@Composable
fun StopwatchScreen(onSettings: () -> Unit) {
    val n = LocalN.current
    val ctx = LocalContext.current
    val sw by StopwatchRepo.state.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sw.running) {
        while (sw.running) { withFrameMillis { now = System.currentTimeMillis() } }
        now = System.currentTimeMillis()
    }

    val elapsed = sw.elapsed(now)
    val (main, cs) = stopwatchParts(elapsed)
    val lapMs = elapsed - (sw.laps.firstOrNull()?.totalMs ?: 0)
    val best = sw.laps.takeIf { it.size >= 2 }?.minByOrNull { it.lapMs }?.number
    val worst = sw.laps.takeIf { it.size >= 2 }?.maxByOrNull { it.lapMs }?.number

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenTitle(tr("Секундомер", "Stopwatch")) { NIconButton(Ic.GEAR, onSettings) }

        // the time: dot-matrix, centred
        Column(Modifier.fillMaxWidth().padding(top = if (sw.laps.isEmpty()) 56.dp else 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(main, style = NType.dotDisplay.copy(fontSize = if (main.length > 5) 54.sp else 68.sp), color = if (sw.running || elapsed > 0) n.display else n.disabled)
                Text(cs, style = NType.dotNumber.copy(fontSize = 30.sp), color = n.secondary, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
            }
            Spacer(Modifier.height(8.dp))
            NCaps(
                when {
                    sw.laps.isNotEmpty() -> tr("Круг ", "Lap ") + "${sw.laps.size + 1} · ${stopwatchText(lapMs)}"
                    sw.running -> tr("Идёт", "Running")
                    elapsed > 0 -> tr("Пауза", "Paused")
                    else -> tr("Готов", "Ready")
                },
                color = if (sw.running) n.primary else n.secondary,
            )
        }

        // laps, newest first, as one group of rows
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (sw.laps.isNotEmpty()) LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(sw.laps, key = { _, l -> l.number }) { i, l ->
                    val color = when (l.number) { best -> n.success; worst -> n.accent; else -> n.display }
                    Row(
                        Modifier.animateItem().nRow(groupShape(i, sw.laps.size)).padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NCaps("%02d".format(l.number), Modifier.width(44.dp), color = n.secondary)
                        Text(stopwatchText(l.lapMs), style = NType.bodyMedium, color = color, modifier = Modifier.weight(1f))
                        NMeta(stopwatchText(l.totalMs))
                    }
                }
            }
        }

        // controls sit right above the floating bar, within thumb reach
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleButton({ if (elapsed > 0) StopwatchRepo.reset(ctx) }, size = 64.dp) { NIcon(Ic.RESET, tint = if (elapsed > 0) n.display else n.disabled) }
            CircleButton({ StopwatchRepo.toggle(ctx) }, size = 84.dp, filled = true) {
                NIcon(if (sw.running) Ic.PAUSE else Ic.PLAY, tint = n.bg, size = 32.dp)
            }
            CircleButton({ StopwatchRepo.lap(ctx) }, size = 64.dp) { NIcon(Ic.LAP, tint = if (sw.running) n.display else n.disabled) }
        }
        Spacer(Modifier.height(navBarClearance() + 8.dp))
    }
}
