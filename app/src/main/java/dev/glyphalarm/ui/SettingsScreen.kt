package dev.glyphalarm.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glyphalarm.data.AppSettings
import dev.glyphalarm.data.SoundImporter
import dev.glyphalarm.data.TimerSound
import dev.glyphalarm.glyph.GlyphEngine

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
        SetupItem("Уведомления", "Нужны, чтобы показать сработавший будильник или таймер.", granted(Manifest.permission.POST_NOTIFICATIONS)) {
            notif.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        SetupItem("Полноэкранный будильник", "Позволяет будильнику открыться на заблокированном экране.", nm.canUseFullScreenIntent()) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg))
        },
        SetupItem("Поверх других приложений", "Позволяет будильнику открыться поверх любого приложения. Glyph работает только для приложения на экране.", Settings.canDrawOverlays(ctx)) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg))
        },
        SetupItem("Батарея: без ограничений", "Не даёт системе «усыпить» будильник.", pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
            ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg))
        },
        SetupItem("Доступ к аудио", "Чтобы в списке мелодий появились ваши композиции из Glyph-составителя.", granted(Manifest.permission.READ_MEDIA_AUDIO)) {
            audio.launch(Manifest.permission.READ_MEDIA_AUDIO)
        },
    )
}

@Composable
fun SettingsScreen(items: List<SetupItem>, onClose: () -> Unit) {
    val n = LocalN.current
    val ctx = LocalContext.current
    val status by GlyphEngine.status.collectAsStateWithLifecycle()
    val offset by AppSettings.syncOffsetMs.collectAsStateWithLifecycle()
    val timerSound by AppSettings.timerSound.collectAsStateWithLifecycle()
    var testResult by remember { mutableStateOf<String?>(null) }
    var helpOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var soundError by remember { mutableStateOf<String?>(null) }
    val openSystemPicker = rememberSystemRingtonePicker { uri ->
        scope.launch {
            val snd = withContext(Dispatchers.IO) { SoundImporter.import(ctx, uri) }
            if (snd == null) { soundError = "Не удалось открыть эту мелодию"; return@launch }
            soundError = null
            timerSound.path?.let { SoundImporter.discard(it) }
            AppSettings.setTimerSound(ctx, TimerSound(snd.path, snd.name))
        }
    }

    BackHandler { onClose() }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        TopBar(onClose)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            Title("Настройки", Modifier.padding(horizontal = 8.dp))

            NSection("Glyph")
            NGroup {
                NRow("Glyph-интерфейс", subtitle = status.text) {
                    Box(Modifier.size(7.dp).background(if (status.ok) n.success else n.accent, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    NCaps(if (status.ok) "подключено" else "нет связи", color = if (status.ok) n.success else n.accent)
                }
                NRow("Проверить глифы", subtitle = testResult ?: "Зажигает все полоски по очереди", onClick = { testResult = GlyphEngine.selfTest() }) {
                    NIcon(Ic.PLAY, tint = n.secondary, size = 18.dp)
                }
                NRow("Сдвиг света", subtitle = "Если свет опережает звук или отстаёт") {
                    CircleButton({ AppSettings.setSyncOffset(ctx, offset - 20) }, size = 40.dp) { Text("−", style = NType.heading, color = n.display) }
                    Text("%+d мс".format(offset), Modifier.width(76.dp), style = NType.bodyMedium, color = n.display, textAlign = TextAlign.Center)
                    CircleButton({ AppSettings.setSyncOffset(ctx, offset + 20) }, size = 40.dp) { Text("+", style = NType.heading, color = n.display) }
                }
                if (!status.ok) {
                    NRow("Если не подключается", onClick = { helpOpen = !helpOpen }) {
                        NIcon(Ic.CHEVRON, Modifier.rotate(if (helpOpen) 270f else 90f), tint = n.disabled, size = 18.dp)
                    }
                    if (helpOpen) Column(Modifier.nRow().padding(20.dp)) {
                        NMeta("1. В настройках телефона включите «Glyph-интерфейс».")
                        Spacer(Modifier.height(6.dp))
                        NMeta("2. На Nothing OS старше Android 16 один раз нужен доступ разработчика. Подключите телефон по USB и выполните:")
                        Spacer(Modifier.height(10.dp))
                        NText("adb shell settings put global nt_glyph_interface_debug_enable 1", style = NType.meta, color = n.primary)
                        Spacer(Modifier.height(10.dp))
                        NMeta("Действует 48 часов. После этого перезапустите приложение.")
                    }
                }
            }
            NMeta(GlyphEngine.deviceInfo, Modifier.padding(start = 8.dp, top = 10.dp), color = n.disabled)

            NSection("Таймер")
            NGroup {
                NRow("Мелодия таймера", leading = Ic.MUSIC, subtitle = soundError ?: timerSound.name, onClick = openSystemPicker) {
                    NIcon(Ic.CHEVRON, tint = n.disabled, size = 18.dp)
                }
                if (timerSound.path != null) NRow("Вернуть стандартный сигнал", titleColor = n.secondary, onClick = {
                    SoundImporter.discard(timerSound.path)
                    soundError = null
                    AppSettings.setTimerSound(ctx, TimerSound(null, "Стандартный сигнал"))
                })
            }

            NSection("Разрешения")
            NGroup {
                items.forEach { item ->
                    NRow(item.title, subtitle = item.hint, onClick = if (!item.ok && item.action != null) item.action else null) {
                        if (item.ok) NIcon(Ic.CHECK, tint = n.success, size = 22.dp)
                        else Box(Modifier.clip(CircleShape).background(n.display, CircleShape).padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Text("Разрешить", color = n.bg, style = NType.bodyMedium.copy(fontSize = 13.sp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}
