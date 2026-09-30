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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
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

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenTitle("Будильник") { NIconButton(Ic.GEAR, onSettings) }

            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 180.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    // Hero: the next alarm as big dot-matrix digits straight on the black, no box around it
                    Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (nextAt != null) { Box(Modifier.size(7.dp).background(n.accent, CircleShape)); Spacer(Modifier.width(10.dp)) }
                            NLabel(if (nextAt != null) "Следующий будильник" else "Будильники не заданы", color = if (nextAt != null) n.secondary else n.disabled)
                        }
                        Spacer(Modifier.height(18.dp))
                        val (txt, suffix) = if (next != null) formatClock(next.hour, next.minute, is24) else "--:--" to ""
                        Row(verticalAlignment = Alignment.Bottom) {
                            DotText(txt, Modifier.weight(1f, fill = false), color = if (next != null) n.display else n.disabled, maxPitch = 14.dp)
                            if (suffix.isNotEmpty()) { Spacer(Modifier.width(10.dp)); NText(suffix, style = NType.heading, color = n.secondary) }
                        }
                        if (nextAt != null) {
                            Spacer(Modifier.height(16.dp))
                            NMeta(
                                "${nextAt.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()).replaceFirstChar { it.uppercase() }} · ${countdown(nextAt, now)}",
                                color = n.primary,
                            )
                        }
                    }
                }
                items(alarms.sortedWith(compareBy({ it.hour }, { it.minute })), key = { it.id }) { a ->
                    AlarmCard(a, is24, onClick = { onEdit(a.id) }, onToggle = { on ->
                        val updated = a.copy(enabled = on)
                        AlarmRepo.upsert(ctx, updated)
                        if (on) AlarmScheduler.schedule(ctx, updated) else AlarmScheduler.cancel(ctx, a.id)
                    })
                }
            }
        }

        // Primary action: solid white circle
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 24.dp, bottom = 100.dp)
                .size(64.dp)
                .background(n.display, CircleShape)
                .clickable { onEdit(-1) },
            contentAlignment = Alignment.Center,
        ) { NIcon(Ic.PLUS, tint = n.bg, size = 28.dp) }
    }
}

@Composable
private fun AlarmCard(a: Alarm, is24: Boolean, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val n = LocalN.current
    val (txt, suffix) = formatClock(a.hour, a.minute, is24)
    Row(
        Modifier.fillMaxWidth().nCard(24.dp).clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                DotText(txt, Modifier.weight(1f, fill = false), color = if (a.enabled) n.display else n.disabled, maxPitch = 7.dp)
                if (suffix.isNotEmpty()) { Spacer(Modifier.width(8.dp)); Text(suffix, style = NType.meta, color = n.secondary) }
            }
            Spacer(Modifier.height(10.dp))
            NMeta(listOf(daysLabel(a), a.label.ifBlank { null }).filterNotNull().joinToString(" · "))
            Spacer(Modifier.height(2.dp))
            NMeta(a.soundName, color = n.disabled)
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
    data class Track(val columns: Int, val seconds: Double, val mapped: Boolean) : SoundInfo
}

@Composable
fun AlarmEditorScreen(alarmId: Int, onClose: () -> Unit) {
    val n = LocalN.current
    val ctx = LocalContext.current
    val is24 = rememberIs24h()

    val original = remember { AlarmRepo.get(ctx, alarmId) }
    var draft by remember { mutableStateOf(original ?: Alarm(id = AlarmRepo.newId(ctx), hour = 7, minute = 0)) }
    var soundError by remember { mutableStateOf<String?>(null) }
    val pickedFiles = remember { mutableStateListOf<String>() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val ringing by AlarmEngine.ringing.collectAsStateWithLifecycle()
    val previewing = ringing?.preview == true
    val frame by GlyphEngine.monitor.collectAsStateWithLifecycle()
    val status by GlyphEngine.status.collectAsStateWithLifecycle()

    DisposableEffect(Unit) { onDispose { if (AlarmEngine.ringing.value?.preview == true) AlarmEngine.stop() } }

    val info by produceState<SoundInfo>(SoundInfo.Default, draft.soundPath) {
        val p = draft.soundPath
        value = if (p == null) SoundInfo.Default else withContext(Dispatchers.IO) {
            GlyphtoneParser.parseFile(File(p))?.let { SoundInfo.Track(it.sourceColumns, it.durationMs / 1000.0, it.mapped) } ?: SoundInfo.NoGlyph
        }
    }

    fun cancelEdit() {
        if (previewing) AlarmEngine.stop()
        pickedFiles.forEach { SoundImporter.discard(it) }
        onClose()
    }

    fun save() {
        if (previewing) AlarmEngine.stop()
        val toSave = draft.copy(enabled = true)
        AlarmRepo.upsert(ctx, toSave)
        AlarmScheduler.schedule(ctx, toSave)
        if (original?.soundPath != null && original.soundPath != toSave.soundPath) SoundImporter.discard(original.soundPath)
        pickedFiles.filter { it != toSave.soundPath }.forEach { SoundImporter.discard(it) }
        onClose()
    }

    fun delete() {
        if (previewing) AlarmEngine.stop()
        AlarmScheduler.cancel(ctx, draft.id)
        AlarmRepo.delete(ctx, draft.id)
        original?.soundPath?.let { SoundImporter.discard(it) }
        pickedFiles.forEach { SoundImporter.discard(it) }
        onClose()
    }

    fun adopt(uri: android.net.Uri) {
        if (previewing) AlarmEngine.stop()
        scope.launch {
            val snd = withContext(Dispatchers.IO) { SoundImporter.import(ctx, uri) }
            if (snd == null) { soundError = "Не удалось открыть эту мелодию"; return@launch }
            soundError = null
            draft.soundPath?.takeIf { it in pickedFiles }?.let { SoundImporter.discard(it); pickedFiles.remove(it) }
            pickedFiles.add(snd.path)
            draft = draft.copy(soundPath = snd.path, soundName = snd.name)
        }
    }
    val openSystemPicker = rememberSystemRingtonePicker { adopt(it) }

    BackHandler { cancelEdit() }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        TopBar(onBack = ::cancelEdit) { NButton("Сохранить", ::save, filled = true, height = 42.dp) }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(8.dp))
            DotTitle(if (original == null) "Новый будильник" else "Будильник", Modifier.padding(horizontal = 4.dp))
            Spacer(Modifier.height(24.dp))

            // 1. the time
            Column(Modifier.fillMaxWidth().nCard(28.dp).padding(top = 22.dp, bottom = 26.dp, start = 16.dp, end = 16.dp)) {
                TimeDial(draft.hour, draft.minute, is24, { h, m -> draft = draft.copy(hour = h, minute = m) })
            }

            // 2. repeat + name
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().nCard(28.dp)) {
                Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NLabel("Повтор", Modifier.weight(1f))
                        NMeta(daysLabel(draft), color = n.primary)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("П", "В", "С", "Ч", "П", "С", "В").forEachIndexed { i, d ->
                            DayChip(d, draft.repeatsOn(i)) { draft = draft.copy(days = draft.days xor (1 shl i)) }
                        }
                    }
                }
                Hairline()
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    NLabel("Название")
                    BasicTextField(
                        value = draft.label,
                        onValueChange = { draft = draft.copy(label = it.take(40)) },
                        singleLine = true,
                        textStyle = NType.body.copy(color = n.display, textAlign = androidx.compose.ui.text.style.TextAlign.End),
                        cursorBrush = SolidColor(n.display),
                        modifier = Modifier.weight(1f).padding(start = 16.dp),
                        decorationBox = { inner ->
                            Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.CenterEnd) {
                                if (draft.label.isEmpty()) Text("Необязательно", style = NType.body.copy(textAlign = androidx.compose.ui.text.style.TextAlign.End), color = n.disabled)
                                inner()
                            }
                        },
                    )
                }
            }

            // 3. sound
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().nCard(28.dp).padding(22.dp)) {
                NLabel("Мелодия")
                Spacer(Modifier.height(10.dp))
                NText(draft.soundName, style = NType.heading)
                Spacer(Modifier.height(4.dp))
                when (val i = info) {
                    SoundInfo.Default -> NMeta("Системный сигнал · без подсветки Glyph")
                    SoundInfo.NoGlyph -> NMeta("В этой мелодии нет подсветки Glyph")
                    is SoundInfo.Track -> if (i.mapped) NMeta("Подсветка для другого телефона (${i.columns} зон) · по ритму")
                    else NMeta("Подсветка Glyph · %.0f с".format(i.seconds), color = n.success)
                }
                soundError?.let { Spacer(Modifier.height(8.dp)); NMeta(it, color = n.accent) }
                Spacer(Modifier.height(18.dp))
                NButton("Выбрать мелодию", openSystemPicker, Modifier.fillMaxWidth(), filled = true, height = 50.dp)
            }

            // 4. glyph preview: only the washer and the three strips
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().nCard(28.dp).padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NLabel("Подсветка Glyph", Modifier.weight(1f))
                    Box(Modifier.size(7.dp).background(if (status.ok) n.success else n.disabled, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    NMeta(if (status.ok) "подключено" else "не подключено", color = if (status.ok) n.success else n.disabled)
                }
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().nCard(24.dp, outlined = true).background(androidx.compose.ui.graphics.Color.Black).padding(20.dp), contentAlignment = Alignment.Center) {
                    GlyphPhone(frame, Modifier.fillMaxWidth(0.8f))
                }
                Spacer(Modifier.height(18.dp))
                NButton(
                    if (previewing) "Остановить" else "Прослушать с подсветкой",
                    { if (previewing) AlarmEngine.stop() else AlarmEngine.start(ctx, draft, preview = true) },
                    Modifier.fillMaxWidth(), filled = false, icon = if (previewing) Ic.PAUSE else Ic.PLAY, height = 50.dp,
                )
            }

            // 5. options
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().nCard(28.dp)) {
                Column(Modifier.padding(horizontal = 22.dp)) {
                    ToggleRow("Вибрация", draft.vibrate) { draft = draft.copy(vibrate = it) }
                    Hairline()
                    ToggleRow("Плавное нарастание громкости", draft.rise) { draft = draft.copy(rise = it) }
                }
                Hairline()
                Column(Modifier.padding(22.dp)) {
                    NLabel("Повтор сигнала")
                    Spacer(Modifier.height(12.dp))
                    val opts = listOf(5, 10, 15, 20)
                    NSegmented(opts.map { "$it мин" }, opts.indexOf(draft.snoozeMin).coerceAtLeast(0), { draft = draft.copy(snoozeMin = opts[it]) })
                }
            }

            if (original != null) {
                Spacer(Modifier.height(24.dp))
                NButton("Удалить будильник", ::delete, Modifier.fillMaxWidth(), danger = true)
            }
            Spacer(Modifier.height(64.dp))
        }
    }
}

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(32.dp))
    NLabel(title, Modifier.padding(start = 4.dp))
    Spacer(Modifier.height(12.dp))
    content()
}

@Composable
fun ToggleRow(text: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = NType.body, color = LocalN.current.primary, modifier = Modifier.weight(1f))
        NSwitch(on, onChange)
    }
}
