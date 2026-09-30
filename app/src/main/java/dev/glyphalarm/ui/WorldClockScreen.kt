package dev.glyphalarm.ui

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glyphalarm.data.WorldRepo
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

private fun dayWord(then: ZonedDateTime, local: ZonedDateTime): String {
    val d = then.toLocalDate().toEpochDay() - local.toLocalDate().toEpochDay()
    return when {
        d == 0L -> "Сегодня"
        d == 1L -> "Завтра"
        d == -1L -> "Вчера"
        else -> then.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).replaceFirstChar { it.uppercase() }
    }
}

private fun offsetWord(then: ZonedDateTime, local: ZonedDateTime): String {
    val diff = (then.offset.totalSeconds - local.offset.totalSeconds) / 3600f
    if (diff == 0f) return "то же время"
    val sign = if (diff > 0) "+" else "−"
    val v = abs(diff)
    val txt = if (v % 1f == 0f) "%d".format(v.toInt()) else "%.1f".format(v)
    return "$sign$txt ч"
}

@Composable
fun WorldClockScreen(onSettings: () -> Unit) {
    val n = LocalN.current
    val ctx = LocalContext.current
    val is24 = rememberIs24h()
    val zones by WorldRepo.zones.collectAsStateWithLifecycle()
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick = System.currentTimeMillis() } }
    var editing by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }

    if (adding) {
        CityPicker(onPick = { WorldRepo.add(ctx, it); adding = false }, onBack = { adding = false })
        return
    }

    val local = ZonedDateTime.now().also { tick }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenTitle("Мировое время") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NIconButton(Ic.PLUS, { adding = true })
                NIconButton(Ic.GEAR, onSettings)
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth().nCard(28.dp).padding(24.dp)) {
                    val (txt, suffix) = formatClock(local.hour, local.minute, is24)
                    Row(verticalAlignment = Alignment.Bottom) {
                        DotText(txt, Modifier.weight(1f, fill = false), maxPitch = 12.dp)
                        if (suffix.isNotEmpty()) { Spacer(Modifier.width(10.dp)); NText(suffix, style = NType.heading, color = n.secondary) }
                    }
                    Spacer(Modifier.height(18.dp))
                    NMeta(local.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)) + " · " + WorldRepo.cityName(local.zone.id))
                }
            }
            if (zones.isNotEmpty()) item {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NLabel("Города", Modifier.weight(1f))
                    Box(Modifier.clickable { editing = !editing }.padding(8.dp)) { NLabel(if (editing) "Готово" else "Изменить", color = n.display) }
                }
            }
            items(zones, key = { it }) { id ->
                val t = ZonedDateTime.now(ZoneId.of(id)).also { tick }
                val (txt, suffix) = formatClock(t.hour, t.minute, is24)
                Row(
                    Modifier.fillMaxWidth().nCard(24.dp).padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        NText(WorldRepo.cityName(id), style = NType.heading)
                        Spacer(Modifier.height(2.dp))
                        NMeta("${dayWord(t, local)} · ${offsetWord(t, local)}")
                    }
                    if (editing) {
                        NIconButton(Ic.TRASH, { WorldRepo.remove(ctx, id) }, tint = n.accent)
                    } else {
                        Row(verticalAlignment = Alignment.Bottom) {
                            DotText(txt, maxPitch = 6.dp)
                            if (suffix.isNotEmpty()) { Spacer(Modifier.width(6.dp)); Text(suffix, style = NType.meta, color = n.secondary) }
                        }
                    }
                }
            }
            if (zones.isEmpty()) item { NMeta("Городов пока нет. Нажмите «+», чтобы добавить.", Modifier.padding(24.dp), color = n.disabled) }
        }
    }
}

@Composable
private fun CityPicker(onPick: (String) -> Unit, onBack: () -> Unit) {
    val n = LocalN.current
    val is24 = rememberIs24h()
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val results = remember(q) {
        if (q.isEmpty()) WorldRepo.allZones
        else WorldRepo.allZones.filter {
            WorldRepo.cityName(it).lowercase().contains(q) || WorldRepo.regionName(it).lowercase().contains(q) ||
                it.substringAfterLast('/').replace('_', ' ').lowercase().contains(q)
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        TopBar(onBack)
        Spacer(Modifier.height(12.dp))
        DotTitle("Добавить город", Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(24.dp))
        BasicTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            textStyle = NType.body.copy(color = n.display), cursorBrush = SolidColor(n.display),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().nCard(999.dp).padding(horizontal = 22.dp, vertical = 16.dp)) {
                    if (query.isEmpty()) Text("Поиск города или региона", style = NType.body, color = n.disabled)
                    inner()
                }
            },
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results, key = { it }) { id ->
                val t = ZonedDateTime.now(ZoneId.of(id))
                val (txt, suffix) = formatClock(t.hour, t.minute, is24)
                Row(
                    Modifier.fillMaxWidth().nCard(20.dp).clickable { onPick(id) }.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        NText(WorldRepo.cityName(id), style = NType.bodyMedium)
                        NMeta(WorldRepo.regionName(id))
                    }
                    NText("$txt $suffix".trim(), style = NType.meta, color = n.secondary)
                }
            }
        }
    }
}
