package dev.glyphalarm.ui

import dev.glyphalarm.data.tr
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.heightIn
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
        d == 0L -> tr("Сегодня", "Today")
        d == 1L -> tr("Завтра", "Tomorrow")
        d == -1L -> tr("Вчера", "Yesterday")
        else -> then.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).replaceFirstChar { it.uppercase() }
    }
}

private fun offsetWord(then: ZonedDateTime, local: ZonedDateTime): String {
    val diff = (then.offset.totalSeconds - local.offset.totalSeconds) / 3600f
    if (diff == 0f) return tr("то же время", "same time")
    val sign = if (diff > 0) "+" else "−"
    val v = abs(diff)
    val txt = if (v % 1f == 0f) "%d".format(v.toInt()) else "%.1f".format(v)
    return "$sign$txt " + tr("ч", "h")
}

@Composable
fun WorldClockScreen(adding: Boolean, onAdding: (Boolean) -> Unit, onSettings: () -> Unit) {
    val n = LocalN.current
    val ctx = LocalContext.current
    val is24 = rememberIs24h()
    val zones by WorldRepo.zones.collectAsStateWithLifecycle()
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick = System.currentTimeMillis() } }
    var editing by remember { mutableStateOf(false) }

    if (adding) {
        CityPicker(taken = zones, onPick = { WorldRepo.add(ctx, it); onAdding(false) }, onBack = { onAdding(false) })
        return
    }
    if (zones.isEmpty() && editing) editing = false

    val local = ZonedDateTime.now().also { tick }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenTitle(tr("Мировое время", "World clock")) { NIconButton(Ic.GEAR, onSettings) }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = navBarClearance() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp)) {
                    NCaps(tr("Местное время · ", "Local time · ") + WorldRepo.cityName(local.zone.id))
                    Spacer(Modifier.height(18.dp))
                    val (txt, suffix) = formatClock(local.hour, local.minute, is24)
                    Row(verticalAlignment = Alignment.Bottom) {
                        DotText(txt, Modifier.weight(1f, fill = false), maxPitch = 14.dp)
                        if (suffix.isNotEmpty()) { Spacer(Modifier.width(10.dp)); NText(suffix, style = NType.heading, color = n.secondary) }
                    }
                    Spacer(Modifier.height(16.dp))
                    NText(local.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)).replaceFirstChar { it.uppercase() }, style = NType.label, color = n.primary)
                }
            }
            item {
                NSection(tr("Города", "Cities")) {
                    if (zones.isNotEmpty()) Box(Modifier.clip(CircleShape).clickable { editing = !editing }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        NCaps(if (editing) tr("Готово", "Done") else tr("Изменить", "Edit"), color = n.display)
                    }
                }
            }
            itemsIndexed(zones, key = { _, id -> id }) { i, id ->
                val t = ZonedDateTime.now(ZoneId.of(id)).also { tick }
                val (txt, suffix) = formatClock(t.hour, t.minute, is24)
                Row(
                    Modifier.animateItem().nRow(groupShape(i, zones.size)).heightIn(min = 76.dp).padding(start = 20.dp, end = if (editing) 8.dp else 20.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        NText(WorldRepo.cityName(id), style = NType.body.copy(fontSize = 18.sp), maxLines = 1)
                        Spacer(Modifier.height(2.dp))
                        NMeta("${dayWord(t, local)} · ${offsetWord(t, local)}")
                    }
                    Spacer(Modifier.width(12.dp))
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
            if (zones.isEmpty()) item {
                NText(tr("Городов пока нет. Нажмите «+», чтобы добавить.", "No cities yet. Tap “+” to add one."), Modifier.nRow(groupShape(0, 1)).padding(20.dp), style = NType.label, color = n.secondary)
            }
        }
    }
}

@Composable
private fun CityPicker(taken: List<String>, onPick: (String) -> Unit, onBack: () -> Unit) {
    val n = LocalN.current
    val is24 = rememberIs24h()
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val results = remember(q, taken) {
        val free = WorldRepo.allZones.filter { it !in taken }
        if (q.isEmpty()) free
        else free.filter {
            WorldRepo.cityName(it).lowercase().contains(q) || WorldRepo.regionName(it).lowercase().contains(q) ||
                it.substringAfterLast('/').replace('_', ' ').lowercase().contains(q)
        }
    }
    BackHandler { onBack() }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        TopBar(onBack)
        Spacer(Modifier.height(8.dp))
        Title(tr("Добавить город", "Add a city"), Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(20.dp))
        BasicTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            textStyle = NType.body.copy(color = n.display), cursorBrush = SolidColor(n.display),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().nCard(999.dp).padding(horizontal = 22.dp, vertical = 16.dp)) {
                    if (query.isEmpty()) Text(tr("Поиск города или региона", "Search a city or region"), style = NType.body, color = n.disabled)
                    inner()
                }
            },
        )
        Spacer(Modifier.height(16.dp))
        if (results.isEmpty()) NText(tr("Ничего не найдено", "Nothing found"), Modifier.padding(horizontal = 24.dp), style = NType.label, color = n.secondary)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(results, key = { _, id -> id }) { i, id ->
                val t = ZonedDateTime.now(ZoneId.of(id))
                val (txt, suffix) = formatClock(t.hour, t.minute, is24)
                Row(
                    Modifier.nRow(groupShape(i, results.size)).clickable { onPick(id) }.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        NText(WorldRepo.cityName(id), style = NType.body, maxLines = 1)
                        NMeta(WorldRepo.regionName(id))
                    }
                    NText("$txt $suffix".trim(), style = NType.label, color = n.secondary)
                }
            }
        }
    }
}
