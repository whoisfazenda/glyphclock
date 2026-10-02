package dev.glyphalarm.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glyphalarm.data.AppSettings
import dev.glyphalarm.data.Lang
import dev.glyphalarm.data.SoundImporter
import dev.glyphalarm.data.TimerSound
import dev.glyphalarm.data.tr
import dev.glyphalarm.glyph.GlyphEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SetupItem(val title: String, val hint: String, val ok: Boolean, val action: (() -> Unit)?)

@Composable
fun rememberSetupItems(): List<SetupItem> {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val audio = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    tick.let { }

    val nm = ctx.getSystemService(NotificationManager::class.java)
    val pm = ctx.getSystemService(PowerManager::class.java)
    val pkg = Uri.parse("package:${ctx.packageName}")
    fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    return listOf(
        SetupItem(tr("Уведомления", "Notifications"), tr("Нужны, чтобы показать сработавший будильник или таймер.", "Needed to show a ringing alarm or timer."), granted(Manifest.permission.POST_NOTIFICATIONS)) {
            notif.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        SetupItem(tr("Полноэкранный будильник", "Full-screen alarm"), tr("Позволяет будильнику открыться на заблокированном экране.", "Lets the alarm open on the lock screen."), nm.canUseFullScreenIntent()) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg))
        },
        SetupItem(tr("Поверх других приложений", "Display over other apps"), tr("Позволяет будильнику открыться поверх любого приложения. Glyph работает только для приложения на экране.", "Lets the alarm open over any app. Glyph only works for the app on screen."), Settings.canDrawOverlays(ctx)) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg))
        },
        SetupItem(tr("Батарея: без ограничений", "Battery: unrestricted"), tr("Не даёт системе «усыпить» будильник.", "Keeps the system from putting the alarm to sleep."), pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
            ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg))
        },
        SetupItem(tr("Доступ к аудио", "Audio access"), tr("Чтобы в списке мелодий появились ваши композиции из Glyph-составителя.", "So your Glyph Composer creations show up in the melody list."), granted(Manifest.permission.READ_MEDIA_AUDIO)) {
            audio.launch(Manifest.permission.READ_MEDIA_AUDIO)
        },
    )
}

@Composable
private fun NSlider(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit, onDone: () -> Unit = {}) {
    val n = LocalN.current
    Slider(
        value, onChange, valueRange = range, steps = steps, onValueChangeFinished = onDone,
        colors = SliderDefaults.colors(
            thumbColor = n.display, activeTrackColor = n.display, inactiveTrackColor = n.surfaceRaised,
            activeTickColor = n.bg, inactiveTickColor = n.disabled,
        ),
    )
}

@Composable
fun SettingsScreen(items: List<SetupItem>, onClose: () -> Unit) {
    val n = LocalN.current
    val ctx = LocalContext.current
    val status by GlyphEngine.status.collectAsStateWithLifecycle()
    val offset by AppSettings.syncOffsetMs.collectAsStateWithLifecycle()
    val riseSec by AppSettings.riseSec.collectAsStateWithLifecycle()
    val timerSound by AppSettings.timerSound.collectAsStateWithLifecycle()
    var testResult by remember { mutableStateOf<String?>(null) }
    var helpOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var soundError by remember { mutableStateOf<String?>(null) }
    val alarmSound by AppSettings.alarmSound.collectAsStateWithLifecycle()
    var alarmError by remember { mutableStateOf<String?>(null) }
    val openAlarmPicker = rememberSystemRingtonePicker(alarmSound.uri) { uri ->
        scope.launch {
            val snd = withContext(Dispatchers.IO) { SoundImporter.import(ctx, uri) }
            if (snd == null) { alarmError = tr("Не удалось открыть эту мелодию", "Could not open this melody"); return@launch }
            alarmError = null
            alarmSound.path?.let { SoundImporter.discard(it) }
            AppSettings.setAlarmSound(ctx, TimerSound(snd.path, snd.name, snd.uri))
        }
    }
    val openSystemPicker = rememberSystemRingtonePicker(timerSound.uri) { uri ->
        scope.launch {
            val snd = withContext(Dispatchers.IO) { SoundImporter.import(ctx, uri) }
            if (snd == null) { soundError = tr("Не удалось открыть эту мелодию", "Could not open this melody"); return@launch }
            soundError = null
            timerSound.path?.let { SoundImporter.discard(it) }
            AppSettings.setTimerSound(ctx, TimerSound(snd.path, snd.name, snd.uri))
        }
    }

    // the alarm volume is the phone's own alarm stream, the same slider as in the system sound settings
    val audio = remember { ctx.getSystemService(AudioManager::class.java) }
    val maxVol = remember { audio.getStreamMaxVolume(AudioManager.STREAM_ALARM).coerceAtLeast(1) }
    var vol by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_ALARM).toFloat().coerceAtLeast(1f)) }
    val lang = remember { Lang.current(ctx) }
    val night by AppSettings.night.collectAsStateWithLifecycle()
    val is24 = rememberIs24h()
    var nightPicker by remember { mutableIntStateOf(-1) } // 0 = start, 1 = end

    BackHandler { onClose() }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        TopBar(onClose)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            Title(tr("Настройки", "Settings"), Modifier.padding(horizontal = 8.dp))

            NSection(tr("Звук", "Sound"))
            NGroup {
                Column(Modifier.nRow().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NText(tr("Громкость будильника", "Alarm volume"), Modifier.weight(1f), style = NType.body)
                        NCaps("${vol.toInt()} / $maxVol", Modifier.padding(end = 8.dp), color = n.primary)
                    }
                    NSlider(vol, 1f..maxVol.toFloat(), (maxVol - 2).coerceAtLeast(0), {
                        vol = it
                        audio.setStreamVolume(AudioManager.STREAM_ALARM, it.toInt().coerceIn(1, maxVol), 0)
                    })
                }
                Column(Modifier.nRow().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            NText(tr("Постепенное увеличение громкости", "Gradual volume increase"), style = NType.body)
                            NMeta(tr("За сколько секунд звук дойдёт до полной громкости", "Seconds until the sound reaches full volume"))
                        }
                        Spacer(Modifier.width(8.dp))
                        NCaps(tr("$riseSec с", "$riseSec s"), Modifier.padding(end = 8.dp), color = n.primary)
                    }
                    NSlider(riseSec.toFloat(), 5f..120f, 22, { AppSettings.setRiseSec(ctx, ((it / 5f).toInt() * 5).coerceAtLeast(5)) })
                }
                NRow(tr("Мелодия будильника по умолчанию", "Default alarm melody"), leading = Ic.ALARM, subtitle = alarmError ?: alarmSound.title(), onClick = openAlarmPicker) {
                    NIcon(Ic.CHEVRON, tint = n.disabled, size = 18.dp)
                }
                if (alarmSound.path != null) NRow(tr("Вернуть стандартный сигнал", "Back to the default alarm sound"), titleColor = n.secondary, onClick = {
                    SoundImporter.discard(alarmSound.path)
                    alarmError = null
                    AppSettings.setAlarmSound(ctx, TimerSound(null, "", null))
                })
                NRow(tr("Мелодия таймера", "Timer sound"), leading = Ic.MUSIC, subtitle = soundError ?: timerSound.title(), onClick = openSystemPicker) {
                    NIcon(Ic.CHEVRON, tint = n.disabled, size = 18.dp)
                }
                if (timerSound.path != null) NRow(tr("Вернуть стандартный сигнал", "Back to the default alarm sound"), titleColor = n.secondary, onClick = {
                    SoundImporter.discard(timerSound.path)
                    soundError = null
                    AppSettings.setTimerSound(ctx, TimerSound(null, "", null))
                })
            }

            NSection(tr("Язык", "Language"))
            NGroup {
                Column(Modifier.nRow().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 18.dp)) {
                    val opts = listOf("" to tr("Как в системе", "System"), "ru" to "Русский", "en" to "English")
                    NSegmented(opts.map { it.second }, opts.indexOfFirst { it.first == lang }.coerceAtLeast(0), { Lang.set(ctx, opts[it].first) })
                }
            }

            NSection("Glyph")
            NGroup {
                NRow(tr("Glyph-интерфейс", "Glyph interface"), subtitle = status.text) {
                    Box(Modifier.size(7.dp).background(if (status.ok) n.success else n.accent, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    NCaps(if (status.ok) tr("подключено", "connected") else tr("нет связи", "no link"), color = if (status.ok) n.success else n.accent)
                }
                NRow(tr("Проверить глифы", "Test the glyphs"), subtitle = testResult ?: tr("Зажигает все полоски по очереди", "Lights up every strip in turn"), onClick = { testResult = GlyphEngine.selfTest() }) {
                    NIcon(Ic.PLAY, tint = n.secondary, size = 18.dp)
                }
                NRow(tr("Сдвиг света", "Light offset"), subtitle = tr("Если свет опережает звук или отстаёт", "If the light runs ahead of or behind the sound")) {
                    CircleButton({ AppSettings.setSyncOffset(ctx, offset - 20) }, size = 40.dp) { Text("−", style = NType.heading, color = n.display) }
                    Text(tr("%+d мс", "%+d ms").format(offset), Modifier.width(76.dp), style = NType.bodyMedium, color = n.display, textAlign = TextAlign.Center)
                    CircleButton({ AppSettings.setSyncOffset(ctx, offset + 20) }, size = 40.dp) { Text("+", style = NType.heading, color = n.display) }
                }
                if (!status.ok) {
                    NRow(tr("Если не подключается", "If it does not connect"), onClick = { helpOpen = !helpOpen }) {
                        NIcon(Ic.CHEVRON, Modifier.rotate(if (helpOpen) 270f else 90f), tint = n.disabled, size = 18.dp)
                    }
                    if (helpOpen) Column(Modifier.nRow().padding(20.dp)) {
                        NMeta(tr("1. В настройках телефона включите «Glyph-интерфейс».", "1. In the phone settings, turn on “Glyph Interface”."))
                        Spacer(Modifier.height(6.dp))
                        NMeta(tr("2. На Nothing OS старше Android 16 один раз нужен доступ разработчика. Подключите телефон по USB и выполните:", "2. On Nothing OS older than Android 16 a developer permission is needed once. Connect the phone by USB and run:"))
                        Spacer(Modifier.height(10.dp))
                        NText("adb shell settings put global nt_glyph_interface_debug_enable 1", style = NType.meta, color = n.primary)
                        Spacer(Modifier.height(10.dp))
                        NMeta(tr("Действует 48 часов. После этого перезапустите приложение.", "It lasts 48 hours. Then restart the app."))
                    }
                }
            }
            NMeta(GlyphEngine.deviceInfo, Modifier.padding(start = 8.dp, top = 10.dp), color = n.disabled)

            NSection(tr("Ночной режим", "Night mode"))
            NGroup {
                ToggleRow(tr("Приглушать глифы ночью", "Dim the glyphs at night"), night.on, subtitle = tr("Тот же свет, но тусклее", "The same show, only dimmer")) {
                    AppSettings.setNight(ctx, night.copy(on = it))
                }
                if (night.on) {
                    fun clock(min: Int) = formatClock(min / 60, min % 60, is24).let { (a, b) -> "$a $b".trim() }
                    NRow(tr("С", "From"), onClick = { nightPicker = 0 }) { NCaps(clock(night.fromMin), color = n.primary) }
                    NRow(tr("До", "Until"), onClick = { nightPicker = 1 }) { NCaps(clock(night.toMin), color = n.primary) }
                    Column(Modifier.nRow().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NText(tr("Яркость ночью", "Brightness at night"), Modifier.weight(1f), style = NType.body)
                            NCaps("${night.levelPct}%", Modifier.padding(end = 8.dp), color = n.primary)
                        }
                        NSlider(night.levelPct.toFloat(), 5f..100f, 18, { AppSettings.setNight(ctx, night.copy(levelPct = ((it / 5f).toInt() * 5).coerceAtLeast(5))) })
                    }
                }
            }

            NSection(tr("Разрешения", "Permissions"))
            NGroup {
                items.forEach { item ->
                    NRow(item.title, subtitle = item.hint, onClick = if (!item.ok && item.action != null) item.action else null) {
                        if (item.ok) NIcon(Ic.CHECK, tint = n.success, size = 22.dp)
                        else Box(Modifier.clip(CircleShape).background(n.display, CircleShape).padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Text(tr("Разрешить", "Allow"), color = n.bg, style = NType.bodyMedium.copy(fontSize = 13.sp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    if (nightPicker >= 0) {
        val start = if (nightPicker == 0) night.fromMin else night.toMin
        TimeDialog(
            hour = start / 60, minute = start % 60, is24 = is24, startMode = 0,
            onDismiss = { nightPicker = -1 },
            onOk = { h, m ->
                val v = h * 60 + m
                AppSettings.setNight(ctx, if (nightPicker == 0) night.copy(fromMin = v) else night.copy(toMin = v))
                nightPicker = -1
            },
        )
    }
}
