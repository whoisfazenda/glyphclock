package dev.glyphalarm.ui

import dev.glyphalarm.data.tr
import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glyphalarm.alarm.AlarmEngine
import dev.glyphalarm.alarm.AlarmService
import dev.glyphalarm.data.TimerRepo
import dev.glyphalarm.glyph.GlyphEngine
import kotlinx.coroutines.delay
import java.time.LocalTime

@Composable
fun RingingScreen(onDone: () -> Unit) {
    val n = LocalN.current
    val ctx: Context = LocalContext.current
    val is24 = rememberIs24h()
    val ringing by AlarmEngine.ringing.collectAsStateWithLifecycle()
    val frame by GlyphEngine.monitor.collectAsStateWithLifecycle()

    // The service starts the engine a moment after this screen may open, so give it a grace period before closing.
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(ringing) {
        if (ringing != null) seen = true
        else if (seen) onDone()                 // stopped from the notification: leave at once
        else { delay(2500); if (AlarmEngine.ringing.value == null) onDone() }
    }

    var blink by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { while (true) { delay(500); blink = !blink } }
    var clock by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); clock = LocalTime.now() } }

    // keep showing the last alarm while the screen closes, so the text does not flip on the way out
    var shown by remember { mutableStateOf(ringing?.alarm) }
    ringing?.alarm?.let { if (it != shown) shown = it }
    val alarm = shown
    val isTimer = (alarm?.id ?: 0) >= TimerRepo.ID_BASE
    val (txt, suffix) = formatClock(clock.hour, clock.minute, is24)
    val label = alarm?.label?.trim()?.takeIf { it.isNotEmpty() && !(isTimer && (it == "Таймер" || it == "Timer")) }

    AmbientBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(if (blink) n.accent else n.bg, CircleShape))
                Spacer(Modifier.width(12.dp))
                NCaps(if (isTimer) tr("Таймер · время вышло", "Timer · time is up") else tr("Будильник", "Alarm"), color = n.primary)
            }

            Spacer(Modifier.height(28.dp))
            Title(label ?: if (isTimer) tr("Время вышло", "Time is up") else tr("Пора вставать", "Wake up"), style = NType.title, color = n.display)

            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                DotText(txt, Modifier.weight(1f, fill = false))
                if (suffix.isNotEmpty()) { Spacer(Modifier.width(10.dp)); NText(suffix, style = NType.heading, color = n.secondary) }
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                GlyphPhone(frame, Modifier.widthIn(max = 250.dp).fillMaxWidth())
            }

            NButton(
                if (isTimer) tr("+1 мин", "+1 min") else tr("Отложить · ${alarm?.snoozeMin ?: 10} мин", "Snooze · ${alarm?.snoozeMin ?: 10} min"),
                { ctx.startService(AlarmService.actionIntent(ctx, AlarmService.ACTION_SNOOZE)); onDone() },
                Modifier.fillMaxWidth(), height = 58.dp,
            )
            Spacer(Modifier.height(12.dp))
            NButton(
                tr("Стоп", "Stop"),
                { ctx.startService(AlarmService.actionIntent(ctx, AlarmService.ACTION_DISMISS)); onDone() },
                Modifier.fillMaxWidth(), filled = true, height = 68.dp,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
