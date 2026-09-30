package dev.glyphalarm.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
        ScreenTitle("Секундомер") { NIconButton(Ic.GEAR, onSettings) }

        Column(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(main, style = NType.dotDisplay.copy(fontSize = 64.sp), color = if (sw.running || elapsed > 0) n.display else n.disabled)
                Text(cs, style = NType.dotNumber.copy(fontSize = 30.sp), color = n.secondary, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
            }
            if (sw.laps.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                NMeta("Круг ${sw.laps.size + 1} · ${stopwatchParts(lapMs).let { it.first + it.second }}")
            }
            Spacer(Modifier.height(36.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleButton({ StopwatchRepo.reset(ctx) }, size = 68.dp) { NIcon(Ic.RESET, tint = if (elapsed > 0) n.display else n.disabled) }
                CircleButton({ StopwatchRepo.toggle(ctx) }, size = 92.dp, filled = true) {
                    NIcon(if (sw.running) Ic.PAUSE else Ic.PLAY, tint = n.bg, size = 34.dp)
                }
                CircleButton({ StopwatchRepo.lap(ctx) }, size = 68.dp) { NIcon(Ic.LAP, tint = if (sw.running) n.display else n.disabled) }
            }
        }

        Spacer(Modifier.height(28.dp))
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(sw.laps, key = { it.number }) { l ->
                val color = when (l.number) { best -> n.success; worst -> n.accent; else -> n.display }
                Row(
                    Modifier.fillMaxWidth().nCard(20.dp).padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NMeta("Круг ${l.number}", Modifier.width(72.dp))
                    Text(stopwatchParts(l.lapMs).let { it.first + it.second }, style = NType.bodyMedium, color = color, modifier = Modifier.weight(1f))
                    NMeta(stopwatchParts(l.totalMs).let { it.first + it.second })
                }
            }
        }
    }
}
