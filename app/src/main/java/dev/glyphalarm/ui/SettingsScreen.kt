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
import androidx.compose.foundation.clickable
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
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var soundError by remember { mutableStateOf<String?>(null) }
    val openSystemPicker = rememberSystemRingtonePicker { uri ->
        scope.launch {
            val snd = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { SoundImporter.import(ctx, uri) }
            if (snd == null) { soundError = "Не удалось открыть эту мелодию"; return@launch }
            soundError = null
            timerSound.path?.let { SoundImporter.discard(it) }
            AppSettings.setTimerSound(ctx, TimerSound(snd.path, snd.name))
        }
    }

    BackHandler { onClose() }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        TopBar(onClose)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            DotTitle("Настройки", Modifier.padding(horizontal = 4.dp))

            Section("Glyph-интерфейс") {
                NCard {
                    Column {
                        NText(if (status.ok) "Подключено" else "Не подключено", style = NType.heading, color = if (status.ok) n.success else n.display)
                        Spacer(Modifier.height(8.dp))
                        NMeta(status.text)
                        Spacer(Modifier.height(4.dp))
                        NMeta(GlyphEngine.deviceInfo, color = n.disabled)
                        Spacer(Modifier.height(16.dp))
                        NButton("Проверить глифы", { testResult = GlyphEngine.selfTest() }, height = 46.dp)
                        testResult?.let { Spacer(Modifier.height(10.dp)); NMeta(it) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                NCard(padding = 20.dp) {
                    Column {
                        NText("Если не подключается", style = NType.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        NMeta("1. В настройках телефона включите «Glyph-интерфейс».")
                        Spacer(Modifier.height(4.dp))
                        NMeta("2. На Nothing OS старше Android 16 один раз нужен доступ разработчика. Подключите телефон по USB и выполните:")
                        Spacer(Modifier.height(8.dp))
                        NText("adb shell settings put global nt_glyph_interface_debug_enable 1", style = NType.meta, color = n.primary)
                        Spacer(Modifier.height(8.dp))
                        NMeta("Действует 48 часов. После этого перезапустите приложение.")
                    }
                }
                Spacer(Modifier.height(20.dp))
                NLabel("Синхронизация света", Modifier.padding(start = 4.dp))
                Spacer(Modifier.height(6.dp))
                NMeta("Если свет опережает звук или отстаёт, сдвиньте его.", Modifier.padding(start = 4.dp))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NButton("−20", { AppSettings.setSyncOffset(ctx, offset - 20) }, height = 44.dp)
                    Spacer(Modifier.width(16.dp))
                    Text("%+d мс".format(offset), style = NType.heading, color = n.display)
                    Spacer(Modifier.width(16.dp))
                    NButton("+20", { AppSettings.setSyncOffset(ctx, offset + 20) }, height = 44.dp)
                }
            }

            Section("Мелодия таймера") {
                Row(
                    Modifier.fillMaxWidth().nCard(24.dp).padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NText(timerSound.name, style = NType.heading, modifier = Modifier.weight(1f))
                }
                soundError?.let { Spacer(Modifier.height(8.dp)); NMeta(it, Modifier.padding(start = 4.dp), color = n.accent) }
                Spacer(Modifier.height(12.dp))
                NButton("Выбрать мелодию", openSystemPicker, Modifier.fillMaxWidth(), filled = true, height = 48.dp)
            }

            Section("Разрешения") {
                Column(Modifier.fillMaxWidth().nCard(24.dp)) {
                    items.forEachIndexed { i, item ->
                        if (i > 0) Hairline()
                        Row(
                            Modifier.fillMaxWidth()
                                .then(if (!item.ok && item.action != null) Modifier.clickable { item.action.invoke() } else Modifier)
                                .padding(horizontal = 22.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(Modifier.weight(1f)) {
                                NText(item.title, style = NType.bodyMedium)
                                Spacer(Modifier.height(2.dp))
                                NMeta(item.hint)
                            }
                            Spacer(Modifier.width(12.dp))
                            if (item.ok) NIcon(Ic.CHECK, tint = n.success) else NLabel("Разрешить", color = n.display)
                        }
                    }
                }
            }
            Spacer(Modifier.height(64.dp))
        }
    }
}
