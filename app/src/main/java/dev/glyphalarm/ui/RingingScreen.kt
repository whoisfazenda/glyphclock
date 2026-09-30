package dev.glyphalarm.ui

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

    val alarm = ringing?.alarm
    val isTimer = (alarm?.id ?: 0) >= TimerRepo.ID_BASE
    val (txt, suffix) = formatClock(clock.hour, clock.minute, is24)

    AmbientBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(if (blink) n.accent else n.bg, CircleShape))
                Spacer(Modifier.width(12.dp))
                NText(alarm?.label?.ifBlank { null } ?: if (isTimer) "Таймер" else "Будильник", style = NType.bodyMedium, color = n.primary)
            }

            Spacer(Modifier.height(56.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                DotText(txt, Modifier.weight(1f, fill = false))
                if (suffix.isNotEmpty()) { Spacer(Modifier.width(10.dp)); NText(suffix, style = NType.heading, color = n.secondary) }
            }
            if (isTimer) { Spacer(Modifier.height(14.dp)); NMeta("Время вышло") }

            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                GlyphPhone(frame, Modifier.width(260.dp))
            }
            Spacer(Modifier.weight(1f))

            NButton(
                if (isTimer) "+1 мин" else "Отложить · ${alarm?.snoozeMin ?: 10} мин",
                { ctx.startService(AlarmService.actionIntent(ctx, AlarmService.ACTION_SNOOZE)); onDone() },
                Modifier.fillMaxWidth(), height = 58.dp,
            )
            Spacer(Modifier.height(12.dp))
            NButton(
                "Стоп",
                { ctx.startService(AlarmService.actionIntent(ctx, AlarmService.ACTION_DISMISS)); onDone() },
                Modifier.fillMaxWidth(), filled = true, height = 66.dp,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
