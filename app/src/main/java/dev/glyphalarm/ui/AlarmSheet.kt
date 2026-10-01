package dev.glyphalarm.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import dev.glyphalarm.data.AppSettings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glyphalarm.alarm.AlarmEngine
import dev.glyphalarm.alarm.AlarmScheduler
import dev.glyphalarm.data.Alarm
import dev.glyphalarm.data.AlarmRepo
import dev.glyphalarm.data.SoundImporter
import dev.glyphalarm.data.tr
import dev.glyphalarm.glyph.GlyphEngine
import dev.glyphalarm.glyph.GlyphtoneParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZonedDateTime

private sealed interface SoundInfo {
    data object Default : SoundInfo
    data object NoGlyph : SoundInfo
    data class Track(val columns: Int, val seconds: Double, val mapped: Boolean, val pack: String?) : SoundInfo
}

/**
 * The alarm editor as a bottom sheet over the alarm list: time, days, name, melody, vibration.
 * "More" unfolds the Glyph preview, volume rise and snooze.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmSheet(alarmId: Int, onClose: () -> Unit, onDeleted: (Alarm) -> Unit) {
    val n = LocalN.current
    val ctx = LocalContext.current
    val is24 = rememberIs24h()
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val original = remember { AlarmRepo.get(ctx, alarmId) }
    var draft by remember { mutableStateOf(original ?: Alarm(id = AlarmRepo.newId(ctx), hour = 7, minute = 0)) }
    var soundError by remember { mutableStateOf<String?>(null) }
    var more by remember { mutableStateOf(false) }
    // a new alarm starts with the time picker, like the stock Clock; -1 = closed, 0 = hours, 1 = minutes
    var picker by remember { mutableIntStateOf(if (original == null) 0 else -1) }
    var timeConfirmed by remember { mutableStateOf(original != null) }
    val pickedFiles = remember { mutableStateListOf<String>() }

    // a new alarm starts with the melody chosen in Settings
    LaunchedEffect(Unit) {
        if (original != null) return@LaunchedEffect
        val snd = withContext(Dispatchers.IO) { SoundImporter.duplicate(ctx, AppSettings.alarmSoundNow(ctx)) } ?: return@LaunchedEffect
        pickedFiles.add(snd.path)
        draft = draft.copy(soundPath = snd.path, soundName = snd.name, soundUri = snd.uri.ifBlank { null })
    }

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
    fun close() { scope.launch { runCatching { sheet.hide() }; onClose() } }

    fun cancelEdit() {
        stopPreview()
        pickedFiles.forEach { SoundImporter.discard(it) }
        close()
    }

    fun save() {
        stopPreview()
        val toSave = draft.copy(enabled = true, label = draft.label.trim())
        AlarmRepo.upsert(ctx, toSave)
        AlarmScheduler.cancel(ctx, toSave.id) // also drops a snooze still pending for the old time
        AlarmScheduler.schedule(ctx, toSave)
        if (original?.soundPath != null && original.soundPath != toSave.soundPath) SoundImporter.discard(original.soundPath)
        pickedFiles.filter { it != toSave.soundPath }.forEach { SoundImporter.discard(it) }
        close()
    }

    fun delete() {
        stopPreview()
        AlarmScheduler.cancel(ctx, draft.id)
        AlarmRepo.delete(ctx, draft.id)
        pickedFiles.forEach { SoundImporter.discard(it) }
        original?.let(onDeleted) // its sound file stays until the "undo" offer runs out
        close()
    }

    fun dropPicked() {
        draft.soundPath?.takeIf { it in pickedFiles }?.let { SoundImporter.discard(it); pickedFiles.remove(it) }
    }

    fun adopt(uri: android.net.Uri) {
        stopPreview()
        scope.launch {
            val snd = withContext(Dispatchers.IO) { SoundImporter.import(ctx, uri) }
            if (snd == null) { soundError = tr("Не удалось открыть эту мелодию", "Could not open this melody"); return@launch }
            soundError = null
            dropPicked()
            pickedFiles.add(snd.path)
            draft = draft.copy(soundPath = snd.path, soundName = snd.name, soundUri = snd.uri)
        }
    }
    val openSystemPicker = rememberSystemRingtonePicker(draft.soundUri) { adopt(it) }

    ModalBottomSheet(
        onDismissRequest = { cancelEdit() },
        sheetState = sheet,
        containerColor = Color(0xFF111113),
        contentColor = n.display,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                // time + "Edit"
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    val (hh, suffix) = formatClock(draft.hour, draft.minute, is24).let { (t, s) -> t.substringBefore(':') to s }
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                        Box(Modifier.clip(RoundedCornerShape(12.dp)).clickable { picker = 0 }) { DotText(hh, maxPitch = 8.dp, showUnlit = false) }
                        Box(Modifier.padding(horizontal = 4.dp)) { DotText(":", maxPitch = 8.dp, showUnlit = false) }
                        Box(Modifier.clip(RoundedCornerShape(12.dp)).clickable { picker = 1 }) { DotText("%02d".format(draft.minute), maxPitch = 8.dp, showUnlit = false) }
                        if (suffix.isNotEmpty()) { Spacer(Modifier.width(8.dp)); NText(suffix, style = NType.label, color = n.secondary) }
                    }
                    NButton(tr("Изменить", "Edit"), { picker = 0 }, height = 44.dp)
                }

                // days
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    dayLetters().forEachIndexed { i, d ->
                        DayChip(d, draft.repeatsOn(i)) { draft = draft.copy(days = draft.days xor (1 shl i)) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                val at = draft.nextTrigger(ZonedDateTime.now())
                NText(
                    "${daysLabel(draft)} · " + tr("сработает ", "rings ") + countdown(at, ZonedDateTime.now()),
                    Modifier.padding(horizontal = 8.dp), style = NType.label, color = n.secondary,
                )
                Spacer(Modifier.height(18.dp))

                NGroup {
                    Row(Modifier.nRow().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        NIcon(Ic.LABEL, tint = n.secondary, size = 22.dp)
                        Spacer(Modifier.width(14.dp))
                        NText(tr("Название", "Label"), style = NType.body)
                        BasicTextField(
                            value = draft.label,
                            onValueChange = { draft = draft.copy(label = it.take(40)) },
                            singleLine = true,
                            textStyle = NType.body.copy(color = n.display, textAlign = TextAlign.End),
                            cursorBrush = SolidColor(n.display),
                            modifier = Modifier.weight(1f).padding(start = 16.dp),
                            decorationBox = { inner ->
                                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.CenterEnd) {
                                    if (draft.label.isEmpty()) Text(tr("Будильник", "Alarm"), style = NType.body.copy(textAlign = TextAlign.End), color = n.disabled)
                                    inner()
                                }
                            },
                        )
                    }
                    val infoLine = when (val i = info) {
                        SoundInfo.Default -> tr("Системный сигнал · без подсветки Glyph", "System tone · no Glyph light")
                        SoundInfo.NoGlyph -> tr("Без подсветки Glyph", "No Glyph light")
                        is SoundInfo.Track -> listOfNotNull(
                            i.pack,
                            if (i.mapped) tr("подсветка для другого телефона, по ритму", "light made for another phone, follows the beat")
                            else tr("подсветка Glyph · %.0f с", "Glyph light · %.0f s").format(i.seconds),
                        ).joinToString(" · ")
                    }
                    NRow(tr("Сигнал будильника", "Alarm sound"), leading = Ic.MUSIC, onClick = openSystemPicker, subtitle = soundError ?: "${draft.soundTitle()}\n$infoLine") {
                        NIcon(Ic.CHEVRON, tint = n.disabled, size = 18.dp)
                    }
                    ToggleRow(tr("Вибрация", "Vibration"), draft.vibrate) { draft = draft.copy(vibrate = it) }
                }

                // unfold: the rest
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(CircleShape).clickable { more = !more }.padding(horizontal = 8.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NCaps(if (more) tr("Свернуть", "Less") else tr("Подсветка Glyph и другое", "Glyph light and more"), Modifier.weight(1f), color = n.primary)
                    NIcon(Ic.CHEVRON, Modifier.rotate(if (more) 270f else 90f), tint = n.secondary, size = 18.dp)
                }
                Column(Modifier.animateContentSize()) {
                    if (more) {
                        NGroup {
                            if (draft.soundPath != null) NRow(tr("Вернуть стандартный сигнал", "Back to the default alarm sound"), titleColor = n.secondary, onClick = {
                                stopPreview(); dropPicked(); soundError = null
                                draft = draft.copy(soundPath = null, soundName = "", soundUri = null)
                            })
                            ToggleRow(tr("Нарастание громкости", "Gradual volume"), draft.rise, subtitle = tr("Время задаётся в настройках", "Duration is set in Settings")) { draft = draft.copy(rise = it) }
                            Column(Modifier.nRow().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 18.dp)) {
                                NText(tr("Отложить на", "Snooze for"), style = NType.body)
                                Spacer(Modifier.height(12.dp))
                                val opts = listOf(5, 10, 15, 20)
                                NSegmented(opts.map { tr("$it мин", "$it min") }, opts.indexOf(draft.snoozeMin).coerceAtLeast(0), { draft = draft.copy(snoozeMin = opts[it]) })
                            }
                        }
                        NSection(tr("Подсветка Glyph", "Glyph light")) {
                            Box(Modifier.size(7.dp).background(if (status.ok) n.success else n.disabled, CircleShape))
                            Spacer(Modifier.width(8.dp))
                            NCaps(if (status.ok) tr("подключено", "connected") else tr("не подключено", "not connected"), color = if (status.ok) n.success else n.disabled)
                        }
                        Column(Modifier.fillMaxWidth().nCard(24.dp).padding(18.dp)) {
                            Box(Modifier.fillMaxWidth().nCard(18.dp).background(Color.Black).padding(16.dp), contentAlignment = Alignment.Center) {
                                GlyphPhone(frame, Modifier.fillMaxWidth(0.7f))
                            }
                            Spacer(Modifier.height(16.dp))
                            NButton(
                                if (previewing) tr("Остановить", "Stop") else tr("Прослушать", "Preview"),
                                { if (previewing) AlarmEngine.stop() else AlarmEngine.start(ctx, draft, preview = true) },
                                Modifier.fillMaxWidth(), filled = !previewing, icon = if (previewing) Ic.PAUSE else Ic.PLAY, height = 52.dp,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // actions
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (original != null) NButton(tr("Удалить", "Delete"), ::delete, danger = true, height = 56.dp)
                NButton(tr("Сохранить", "Save"), ::save, Modifier.weight(1f), filled = true, height = 56.dp)
            }
        }
    }

    if (picker >= 0) {
        TimeDialog(
            hour = draft.hour, minute = draft.minute, is24 = is24, startMode = picker,
            onDismiss = {
                picker = -1
                if (!timeConfirmed) cancelEdit() // a new alarm without a time is no alarm
            },
            onOk = { h, m -> draft = draft.copy(hour = h, minute = m); timeConfirmed = true; picker = -1 },
        )
    }
}

/** The round dial in a small dialog, like the stock Clock's "Select time". */
@Composable
private fun TimeDialog(hour: Int, minute: Int, is24: Boolean, startMode: Int, onDismiss: () -> Unit, onOk: (Int, Int) -> Unit) {
    val n = LocalN.current
    var h by remember { mutableIntStateOf(hour) }
    var m by remember { mutableIntStateOf(minute) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().nCard(32.dp, raised = false).background(n.surface).padding(top = 24.dp, start = 20.dp, end = 20.dp, bottom = 12.dp)) {
            NCaps(tr("Выбор времени", "Select time"))
            Spacer(Modifier.height(16.dp))
            TimeDial(h, m, is24, { nh, nm -> h = nh; m = nm }, startMode = startMode)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(CircleShape).clickable(onClick = onDismiss).padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Text(tr("Отмена", "Cancel"), style = NType.bodyMedium, color = n.secondary)
                }
                Box(Modifier.clip(CircleShape).clickable { onOk(h, m) }.padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Text("OK", style = NType.bodyMedium, color = n.display)
                }
            }
        }
    }
}
