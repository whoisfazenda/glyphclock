package dev.glyphalarm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glyphalarm.alarm.AlarmEngine
import dev.glyphalarm.alarm.AlarmScheduler
import dev.glyphalarm.data.Alarm
import dev.glyphalarm.data.Lang
import dev.glyphalarm.data.tr
import dev.glyphalarm.data.AlarmRepo
import dev.glyphalarm.data.SoundImporter
import dev.glyphalarm.glyph.GlyphEngine
import dev.glyphalarm.glyph.GlyphtoneParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.time.ZonedDateTime
import java.util.Locale

private fun dayNames() = if (Lang.isRu) listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс") else listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** One-letter day names for the repeat chips. */
fun dayLetters() = if (Lang.isRu) listOf("П", "В", "С", "Ч", "П", "С", "В") else listOf("M", "T", "W", "T", "F", "S", "S")

fun daysLabel(a: Alarm): String = when (a.days) {
    0 -> tr("Один раз", "Once")
    127 -> tr("Каждый день", "Every day")
    31 -> tr("По будням", "Weekdays")
    96 -> tr("По выходным", "Weekends")
    else -> dayNames().filterIndexed { i, _ -> a.repeatsOn(i) }.joinToString(", ")
}

fun countdown(to: ZonedDateTime, now: ZonedDateTime): String {
    val m = Duration.between(now, to).toMinutes().coerceAtLeast(0) + 1
    val d = m / (24 * 60)
    val h = (m / 60) % 24
    val mm = m % 60
    return when {
        d > 0 -> tr("через $d д $h ч", "in $d d $h h")
        h > 0 -> tr("через $h ч $mm мин", "in $h h $mm min")
        else -> tr("через $mm мин", "in $mm min")
    }
}

/** Skips (or takes back) the nearest ring of a repeating alarm and re-plans it. */
private fun skipNext(ctx: android.content.Context, a: Alarm, skip: Boolean) {
    val updated = a.withSkip(skip)
    AlarmRepo.upsert(ctx, updated)
    AlarmScheduler.schedule(ctx, updated)
}

/** Bottom padding that keeps the end of a list clear of the floating bar. */
@Composable
fun navBarClearance() = NavBarSpace + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

@Composable
fun AlarmsScreen(onEdit: (Int) -> Unit, onSettings: () -> Unit) {
    val n = LocalN.current
    val ctx = LocalContext.current
    val is24 = rememberIs24h()
    val alarms by AlarmRepo.alarms.collectAsStateWithLifecycle()
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); tick = System.currentTimeMillis() } }

    val now = ZonedDateTime.now().also { tick } // reading tick refreshes the countdown
    val next = alarms.filter { it.enabled }.minByOrNull { it.nextTrigger(now).toEpochSecond() }
    val nextAt = next?.nextTrigger(now)
    val sorted = remember(alarms) { alarms.sortedWith(compareBy({ it.hour }, { it.minute })) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenTitle(tr("Будильник", "Alarm")) { NIconButton(Ic.GEAR, onSettings) }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = navBarClearance() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                // Hero: the next alarm as big dot-matrix digits straight on the black, no box around it
                Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (nextAt != null) { Box(Modifier.size(7.dp).background(n.accent, CircleShape)); Spacer(Modifier.width(10.dp)) }
                        NCaps(if (nextAt != null) tr("Следующий будильник", "Next alarm") else tr("Нет включённых будильников", "No alarms are on"), color = if (nextAt != null) n.secondary else n.disabled)
                    }
                    Spacer(Modifier.height(18.dp))
                    val (txt, suffix) = if (next != null) formatClock(next.hour, next.minute, is24) else "--:--" to ""
                    Row(verticalAlignment = Alignment.Bottom) {
                        DotText(txt, Modifier.weight(1f, fill = false), color = if (next != null) n.display else n.disabled, maxPitch = 14.dp)
                        if (suffix.isNotEmpty()) { Spacer(Modifier.width(10.dp)); NText(suffix, style = NType.heading, color = n.secondary) }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (nextAt != null) {
                        val day = nextAt.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()).replaceFirstChar { it.uppercase() }
                        NText("$day · ${countdown(nextAt, now)}", style = NType.label, color = n.primary)
                        if (next != null && next.days != 0) {
                            Spacer(Modifier.height(14.dp))
                            Box(
                                Modifier.clip(CircleShape).border(1.dp, n.borderVisible, CircleShape)
                                    .clickable { skipNext(ctx, next, true) }.padding(horizontal = 16.dp, vertical = 9.dp),
                            ) { NText(tr("Пропустить этот раз", "Skip this one"), style = NType.meta.copy(fontSize = 13.sp), color = n.primary) }
                        }
                    } else {
                        NText(
                            if (alarms.isEmpty()) tr("Нажмите «+», чтобы создать первый будильник.", "Tap “+” to create your first alarm.") else tr("Включите будильник или создайте новый.", "Turn an alarm on or create a new one."),
                            style = NType.label, color = n.secondary,
                        )
                    }
                }
            }
            items(sorted, key = { it.id }) { a ->
                AlarmCard(a, is24, Modifier.animateItem(), onClick = { onEdit(a.id) }, onUnskip = { skipNext(ctx, a, false) }, onToggle = { on ->
                    val updated = a.copy(enabled = on)
                    AlarmRepo.upsert(ctx, updated)
                    if (on) AlarmScheduler.schedule(ctx, updated) else AlarmScheduler.cancel(ctx, a.id)
                })
            }
        }
    }
}

@Composable
private fun AlarmCard(a: Alarm, is24: Boolean, modifier: Modifier, onClick: () -> Unit, onUnskip: () -> Unit, onToggle: (Boolean) -> Unit) {
    val n = LocalN.current
    val (txt, suffix) = formatClock(a.hour, a.minute, is24)
    Row(
        modifier.fillMaxWidth().nCard(24.dp).clickable(onClick = onClick).padding(start = 22.dp, end = 20.dp, top = 20.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                DotText(txt, Modifier.weight(1f, fill = false), color = if (a.enabled) n.display else n.disabled, maxPitch = 7.dp)
                if (suffix.isNotEmpty()) { Spacer(Modifier.width(8.dp)); Text(suffix, style = NType.meta, color = n.secondary) }
            }
            Spacer(Modifier.height(12.dp))
            NText(
                listOfNotNull(a.label.ifBlank { null }, daysLabel(a)).joinToString(" · "),
                style = NType.label, color = if (a.enabled) n.primary else n.disabled, maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            NText(a.soundTitle(), style = NType.meta, color = n.disabled, maxLines = 1)
            if (a.enabled && a.isSkipped()) {
                val again = a.nextTrigger().dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()).replaceFirstChar { it.uppercase() }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NCaps(tr("Пропущен · снова $again", "Skipped · back $again"), color = n.accent)
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.clip(CircleShape).clickable(onClick = onUnskip).padding(horizontal = 8.dp, vertical = 4.dp)) { NCaps(tr("Вернуть", "Undo"), color = n.display) }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        NSwitch(a.enabled, onToggle)
    }
}
