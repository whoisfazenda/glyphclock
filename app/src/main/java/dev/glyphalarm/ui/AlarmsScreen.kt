package dev.glyphalarm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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

private val DAY_NAMES = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
private const val DEFAULT_SOUND = "Стандартный сигнал"

fun daysLabel(a: Alarm): String = when (a.days) {
    0 -> "Один раз"
    127 -> "Каждый день"
    31 -> "По будням"
    96 -> "По выходным"
    else -> DAY_NAMES.filterIndexed { i, _ -> a.repeatsOn(i) }.joinToString(", ")
}

private fun countdown(to: ZonedDateTime, now: ZonedDateTime): String {
    val m = Duration.between(now, to).toMinutes().coerceAtLeast(0) + 1
    val d = m / (24 * 60)
    val h = (m / 60) % 24
    val mm = m % 60
    return when {
        d > 0 -> "через $d д $h ч"
        h > 0 -> "через $h ч $mm мин"
        else -> "через $mm мин"
    }
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
        ScreenTitle("Будильник") { NIconButton(Ic.GEAR, onSettings) }

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
                        NCaps(if (nextAt != null) "Следующий будильник" else "Нет включённых будильников", color = if (nextAt != null) n.secondary else n.disabled)
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
                    } else {
                        NText(
                            if (alarms.isEmpty()) "Нажмите «+», чтобы создать первый будильник." else "Включите будильник или создайте новый.",
                            style = NType.label, color = n.secondary,
                        )
                    }
                }
            }
            items(sorted, key = { it.id }) { a ->
                AlarmCard(a, is24, Modifier.animateItem(), onClick = { onEdit(a.id) }, onToggle = { on ->
                    val updated = a.copy(enabled = on)
                    AlarmRepo.upsert(ctx, updated)
                    if (on) AlarmScheduler.schedule(ctx, updated) else AlarmScheduler.cancel(ctx, a.id)
                })
            }
        }
    }
}

@Composable
private fun AlarmCard(a: Alarm, is24: Boolean, modifier: Modifier, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
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
            NText(a.soundName, style = NType.meta, color = n.disabled, maxLines = 1)
        }
        Spacer(Modifier.width(12.dp))
        NSwitch(a.enabled, onToggle)
    }
}

// =================================================================================================
// Editor
// =================================================================================================

private sealed interface SoundInfo {
    data object Default : SoundInfo
    data object NoGlyph : SoundInfo
    data class Track(val columns: Int, val seconds: Double, val mapped: Boolean, val pack: String?) : SoundInfo
}

@Composable
fun AlarmEditorScreen(alarmId: Int, onClose: () -> Unit, onDeleted: (Alarm) -> Unit) {
    val n = LocalN.current
    val ctx = LocalContext.current
    val is24 = rememberIs24h()

    val original = remember { AlarmRepo.get(ctx, alarmId) }
    var draft by remember { mutableStateOf(original ?: Alarm(id = AlarmRepo.newId(ctx), hour = 7, minute = 0)) }
    var soundError by remember { mutableStateOf<String?>(null) }
    val pickedFiles = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()

    val ringing by AlarmEngine.ringing.collectAsStateWithLifecycle()
    val previewing = ringing?.preview == true
    val frame by GlyphEngine.monitor.collectAsStateWithLifecycle()
    val status by GlyphEngine.status.collectAsStateWithLifecycle()

    DisposableEffect(Unit) { onDispose { if (AlarmEngine.ringing.value?.preview == true) AlarmEngine.stop() } }

    val info by produceState<SoundInfo>(SoundInfo.Default, draft.soundPath) {
        val p = draft.soundPath
        value = if (p == null) SoundInfo.Default else withContext(Dispatchers.IO) {
            GlyphtoneParser.parseFile(File(p))
                ?.let { SoundInfo.Track(it.sourceColumns, it.durationMs / 1000.0, it.mapped, it.album?.trim()?.ifBlank { null }) }
                ?: SoundInfo.NoGlyph
        }
    }

    fun stopPreview() { if (AlarmEngine.ringing.value?.preview == true) AlarmEngine.stop() }

    fun cancelEdit() {
        stopPreview()
        pickedFiles.forEach { SoundImporter.discard(it) }
        onClose()
    }

    fun save() {
        stopPreview()
        val toSave = draft.copy(enabled = true, label = draft.label.trim())
        AlarmRepo.upsert(ctx, toSave)
        AlarmScheduler.cancel(ctx, toSave.id) // also drops a snooze still pending for the old time
        AlarmScheduler.schedule(ctx, toSave)
        if (original?.soundPath != null && original.soundPath != toSave.soundPath) SoundImporter.discard(original.soundPath)
        pickedFiles.filter { it != toSave.soundPath }.forEach { SoundImporter.discard(it) }
        onClose()
    }

    fun delete() {
        stopPreview()
        AlarmScheduler.cancel(ctx, draft.id)
        AlarmRepo.delete(ctx, draft.id)
        pickedFiles.forEach { SoundImporter.discard(it) }
        // the alarm's own sound stays on disk until the "undo" offer runs out
        original?.let(onDeleted)
        onClose()
    }

    fun dropPicked() {
        draft.soundPath?.takeIf { it in pickedFiles }?.let { SoundImporter.discard(it); pickedFiles.remove(it) }
    }

    fun adopt(uri: android.net.Uri) {
        stopPreview()
        scope.launch {
            val snd = withContext(Dispatchers.IO) { SoundImporter.import(ctx, uri) }
            if (snd == null) { soundError = "Не удалось открыть эту мелодию"; return@launch }
            soundError = null
            dropPicked()
            pickedFiles.add(snd.path)
            draft = draft.copy(soundPath = snd.path, soundName = snd.name)
        }
    }
    val openSystemPicker = rememberSystemRingtonePicker { adopt(it) }

    BackHandler { cancelEdit() }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        TopBar(onBack = ::cancelEdit) { NButton("Сохранить", ::save, filled = true, height = 42.dp) }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            Title(if (original == null) "Новый будильник" else "Будильник", Modifier.padding(horizontal = 8.dp))
            Spacer(Modifier.height(22.dp))

            // 1. the time
            Column(Modifier.fillMaxWidth().nCard(28.dp).padding(top = 22.dp, bottom = 26.dp, start = 16.dp, end = 16.dp)) {
                TimeDial(draft.hour, draft.minute, is24, { h, m -> draft = draft.copy(hour = h, minute = m) })
            }

            // 2. repeat
            NSection("Повтор") { NCaps(daysLabel(draft), color = n.primary) }
            Row(Modifier.nRow(groupShape(0, 1)).padding(horizontal = 18.dp, vertical = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("П", "В", "С", "Ч", "П", "С", "В").forEachIndexed { i, d ->
                    DayChip(d, draft.repeatsOn(i)) { draft = draft.copy(days = draft.days xor (1 shl i)) }
                }
            }

            // 3. everything else, as one group of rows
            NSection("Параметры")
            NGroup {
                Row(Modifier.nRow().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    NText("Название", style = NType.body)
                    BasicTextField(
                        value = draft.label,
                        onValueChange = { draft = draft.copy(label = it.take(40)) },
                        singleLine = true,
                        textStyle = NType.body.copy(color = n.display, textAlign = TextAlign.End),
                        cursorBrush = SolidColor(n.display),
                        modifier = Modifier.weight(1f).padding(start = 16.dp),
                        decorationBox = { inner ->
                            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.CenterEnd) {
                                if (draft.label.isEmpty()) Text("Необязательно", style = NType.body.copy(textAlign = TextAlign.End), color = n.disabled)
                                inner()
                            }
                        },
                    )
                }
                val infoLine = when (val i = info) {
                    SoundInfo.Default -> "Системный сигнал · без подсветки Glyph"
                    SoundInfo.NoGlyph -> "Без подсветки Glyph"
                    is SoundInfo.Track -> listOfNotNull(
                        i.pack,
                        if (i.mapped) "подсветка для другого телефона, по ритму" else "подсветка Glyph · %.0f с".format(i.seconds),
                    ).joinToString(" · ")
                }
                NRow("Мелодия", leading = Ic.MUSIC, onClick = openSystemPicker, subtitle = soundError ?: "${draft.soundName}\n$infoLine") {
                    NIcon(Ic.CHEVRON, tint = n.disabled, size = 18.dp)
                }
                if (draft.soundPath != null) {
                    NRow("Вернуть стандартный сигнал", titleColor = n.secondary, onClick = {
                        stopPreview()
                        dropPicked()
                        soundError = null
                        draft = draft.copy(soundPath = null, soundName = DEFAULT_SOUND)
                    })
                }
                ToggleRow("Вибрация", draft.vibrate) { draft = draft.copy(vibrate = it) }
                ToggleRow("Нарастание громкости", draft.rise, subtitle = "Звук набирает силу за 30 секунд") { draft = draft.copy(rise = it) }
                Column(Modifier.nRow().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 18.dp)) {
                    NText("Отложить на", style = NType.body)
                    Spacer(Modifier.height(12.dp))
                    val opts = listOf(5, 10, 15, 20)
                    NSegmented(opts.map { "$it мин" }, opts.indexOf(draft.snoozeMin).coerceAtLeast(0), { draft = draft.copy(snoozeMin = opts[it]) })
                }
            }

            // 4. glyph preview: only the washer and the three strips
            NSection("Подсветка Glyph") {
                Box(Modifier.size(7.dp).background(if (status.ok) n.success else n.disabled, CircleShape))
                Spacer(Modifier.width(8.dp))
                NCaps(if (status.ok) "подключено" else "не подключено", color = if (status.ok) n.success else n.disabled)
            }
            Column(Modifier.fillMaxWidth().nCard(24.dp).padding(18.dp)) {
                Box(Modifier.fillMaxWidth().nCard(18.dp).background(Color.Black).padding(16.dp), contentAlignment = Alignment.Center) {
                    GlyphPhone(frame, Modifier.fillMaxWidth(0.78f))
                }
                Spacer(Modifier.height(16.dp))
                NButton(
                    if (previewing) "Остановить" else "Прослушать",
                    { if (previewing) AlarmEngine.stop() else AlarmEngine.start(ctx, draft, preview = true) },
                    Modifier.fillMaxWidth(), filled = !previewing, icon = if (previewing) Ic.PAUSE else Ic.PLAY, height = 52.dp,
                )
            }

            if (original != null) {
                Spacer(Modifier.height(28.dp))
                NButton("Удалить будильник", ::delete, Modifier.fillMaxWidth(), danger = true)
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}
