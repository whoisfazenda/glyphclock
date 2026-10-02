package dev.glyphalarm.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId

private const val PREFS = "glyph_clock"

private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

// ---- timers ------------------------------------------------------------------------------------

enum class TimerState { PAUSED, RUNNING, DONE }

data class TimerItem(
    val id: Int,
    val totalMs: Long,
    val label: String = "",
    val state: TimerState = TimerState.PAUSED,
    /** Wall-clock end while running. */
    val endAt: Long = 0,
    /** Time left while paused. */
    val remainingMs: Long = totalMs,
) {
    fun remaining(now: Long = System.currentTimeMillis()): Long = when (state) {
        TimerState.RUNNING -> (endAt - now).coerceAtLeast(0)
        TimerState.PAUSED -> remainingMs
        TimerState.DONE -> 0
    }

    fun toJson() = JSONObject().apply {
        put("id", id); put("total", totalMs); put("label", label); put("state", state.name); put("endAt", endAt); put("rem", remainingMs)
    }

    companion object {
        fun fromJson(o: JSONObject) = TimerItem(
            o.getInt("id"), o.getLong("total"), o.optString("label", ""),
            runCatching { TimerState.valueOf(o.getString("state")) }.getOrDefault(TimerState.PAUSED),
            o.optLong("endAt"), o.optLong("rem"),
        )
    }
}

object TimerRepo {
    /** Ids of timers are offset so a ringing timer can never collide with an alarm id. */
    const val ID_BASE = 1_000_000

    private val _timers = MutableStateFlow<List<TimerItem>>(emptyList())
    val timers: StateFlow<List<TimerItem>> = _timers.asStateFlow()
    private var loaded = false

    @Synchronized
    private fun ensure(ctx: Context) {
        if (loaded) return
        _timers.value = runCatching {
            val a = JSONArray(prefs(ctx).getString("timers", "[]"))
            List(a.length()) { TimerItem.fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        loaded = true
    }

    fun init(ctx: Context) = ensure(ctx)
    fun all(ctx: Context): List<TimerItem> { ensure(ctx); return _timers.value }
    fun get(ctx: Context, id: Int) = all(ctx).firstOrNull { it.id == id }

    @Synchronized
    fun upsert(ctx: Context, t: TimerItem) {
        ensure(ctx)
        val l = _timers.value
        _timers.value = if (l.any { it.id == t.id }) l.map { if (it.id == t.id) t else it } else l + t
        persist(ctx)
    }

    @Synchronized
    fun delete(ctx: Context, id: Int) {
        ensure(ctx)
        _timers.value = _timers.value.filterNot { it.id == id }
        persist(ctx)
    }

    @Synchronized
    fun newId(ctx: Context): Int { ensure(ctx); return (_timers.value.maxOfOrNull { it.id } ?: 0) + 1 }

    private fun persist(ctx: Context) {
        val a = JSONArray(); _timers.value.forEach { a.put(it.toJson()) }
        prefs(ctx).edit().putString("timers", a.toString()).apply()
    }
}

// ---- world clock -------------------------------------------------------------------------------

object WorldRepo {
    private val _zones = MutableStateFlow<List<String>>(emptyList())
    val zones: StateFlow<List<String>> = _zones.asStateFlow()
    private var loaded = false

    @Synchronized
    private fun ensure(ctx: Context) {
        if (loaded) return
        val raw = prefs(ctx).getString("zones", null)
        _zones.value = if (raw == null) listOf("Europe/London", "America/New_York", "Asia/Tokyo")
        else runCatching { JSONArray(raw).let { a -> List(a.length()) { a.getString(it) } } }.getOrDefault(emptyList())
        loaded = true
    }

    fun init(ctx: Context) = ensure(ctx)

    @Synchronized
    fun add(ctx: Context, id: String) {
        ensure(ctx)
        if (id !in _zones.value) { _zones.value = _zones.value + id; persist(ctx) }
    }

    @Synchronized
    fun remove(ctx: Context, id: String) {
        ensure(ctx)
        _zones.value = _zones.value - id
        persist(ctx)
    }

    private fun persist(ctx: Context) {
        val a = JSONArray(); _zones.value.forEach { a.put(it) }
        prefs(ctx).edit().putString("zones", a.toString()).apply()
    }

    /** "America/New_York" -> "New York". */
    fun cityName(zoneId: String): String {
        val fallback = zoneId.substringAfterLast('/').replace('_', ' ')
        return runCatching {
            android.icu.text.TimeZoneNames.getInstance(java.util.Locale.getDefault()).getExemplarLocationName(zoneId)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallback
    }

    private val REGIONS = mapOf(
        "Africa" to ("Африка" to "Africa"), "America" to ("Америка" to "America"), "Antarctica" to ("Антарктида" to "Antarctica"),
        "Arctic" to ("Арктика" to "Arctic"), "Asia" to ("Азия" to "Asia"), "Atlantic" to ("Атлантика" to "Atlantic"),
        "Australia" to ("Австралия" to "Australia"), "Europe" to ("Европа" to "Europe"),
        "Indian" to ("Индийский океан" to "Indian Ocean"), "Pacific" to ("Тихий океан" to "Pacific"),
    )

    fun regionName(zoneId: String): String = zoneId.substringBefore('/', "").let { r -> REGIONS[r]?.let { tr(it.first, it.second) } ?: r.replace('_', ' ') }

    /** All selectable cities: Area/City ids only (no Etc/, no legacy aliases). */
    val allZones: List<String> by lazy {
        ZoneId.getAvailableZoneIds()
            .filter { it.contains('/') && !it.startsWith("Etc/") && !it.startsWith("SystemV/") && !it.startsWith("US/") && !it.startsWith("Brazil/") && !it.startsWith("Canada/") && !it.startsWith("Chile/") && !it.startsWith("Mexico/") }
            .sortedBy { cityName(it) }
    }
}

// ---- stopwatch ---------------------------------------------------------------------------------

data class Lap(val number: Int, val lapMs: Long, val totalMs: Long)

data class StopwatchState(
    val running: Boolean = false,
    /** Wall-clock moment the current run segment began. */
    val startedAt: Long = 0,
    /** Time banked from earlier run segments. */
    val bankedMs: Long = 0,
    val laps: List<Lap> = emptyList(),
) {
    fun elapsed(now: Long = System.currentTimeMillis()): Long = bankedMs + if (running) (now - startedAt).coerceAtLeast(0) else 0
}

object StopwatchRepo {
    private val _state = MutableStateFlow(StopwatchState())
    val state: StateFlow<StopwatchState> = _state.asStateFlow()
    private var loaded = false

    @Synchronized
    private fun ensure(ctx: Context) {
        if (loaded) return
        runCatching {
            val o = JSONObject(prefs(ctx).getString("stopwatch", "{}"))
            val laps = o.optJSONArray("laps")
            _state.value = StopwatchState(
                o.optBoolean("running"), o.optLong("startedAt"), o.optLong("banked"),
                if (laps == null) emptyList() else List(laps.length()) { i -> laps.getJSONObject(i).let { Lap(it.getInt("n"), it.getLong("lap"), it.getLong("total")) } },
            )
        }
        loaded = true
    }

    fun init(ctx: Context) = ensure(ctx)

    @Synchronized
    fun toggle(ctx: Context) {
        ensure(ctx)
        val s = _state.value
        val now = System.currentTimeMillis()
        _state.value = if (s.running) s.copy(running = false, bankedMs = s.elapsed(now)) else s.copy(running = true, startedAt = now)
        persist(ctx)
    }

    @Synchronized
    fun lap(ctx: Context) {
        ensure(ctx)
        val s = _state.value
        if (!s.running) return
        val total = s.elapsed()
        val prev = s.laps.firstOrNull()?.totalMs ?: 0
        _state.value = s.copy(laps = listOf(Lap(s.laps.size + 1, total - prev, total)) + s.laps)
        persist(ctx)
    }

    @Synchronized
    fun reset(ctx: Context) {
        ensure(ctx)
        _state.value = StopwatchState()
        persist(ctx)
    }

    private fun persist(ctx: Context) {
        val s = _state.value
        val laps = JSONArray()
        s.laps.forEach { laps.put(JSONObject().put("n", it.number).put("lap", it.lapMs).put("total", it.totalMs)) }
        prefs(ctx).edit().putString(
            "stopwatch",
            JSONObject().put("running", s.running).put("startedAt", s.startedAt).put("banked", s.bankedMs).put("laps", laps).toString(),
        ).apply()
    }
}

// ---- app settings ------------------------------------------------------------------------------

data class TimerSound(val path: String?, val name: String, val uri: String? = null) {
    fun title(): String = if (path == null || name.isBlank()) tr("Стандартный сигнал", "Default alarm") else name
}

object AppSettings {
    private val _timerSound = MutableStateFlow(TimerSound(null, ""))
    val timerSound: StateFlow<TimerSound> = _timerSound.asStateFlow()
    private val _offset = MutableStateFlow(0)
    val syncOffsetMs: StateFlow<Int> = _offset.asStateFlow()
    private val _alarmSound = MutableStateFlow(TimerSound(null, ""))
    /** Melody given to every new alarm; null path = the phone's own alarm tone. */
    val alarmSound: StateFlow<TimerSound> = _alarmSound.asStateFlow()
    /** Night mode: dims the glyphs between [nightFromMin] and [nightToMin] (minutes after midnight). */
    data class Night(val on: Boolean = false, val fromMin: Int = 22 * 60, val toMin: Int = 7 * 60, val levelPct: Int = 25) {
        fun factorAt(minuteOfDay: Int): Float {
            if (!on) return 1f
            val inside = if (fromMin <= toMin) minuteOfDay in fromMin until toMin else minuteOfDay >= fromMin || minuteOfDay < toMin
            return if (inside) levelPct / 100f else 1f
        }
    }
    private val _night = MutableStateFlow(Night())
    val night: StateFlow<Night> = _night.asStateFlow()

    /** Brightness factor for the glyphs right now (1 = full). */
    fun glyphFactor(): Float = _night.value.let { it.factorAt(java.time.LocalTime.now().let { t -> t.hour * 60 + t.minute }) }

    fun setNight(ctx: Context, n: Night) {
        ensure(ctx)
        _night.value = n.copy(levelPct = n.levelPct.coerceIn(5, 100))
        prefs(ctx).edit().putBoolean("night_on", n.on).putInt("night_from", n.fromMin).putInt("night_to", n.toMin).putInt("night_level", _night.value.levelPct).apply()
    }

    private val _riseSec = MutableStateFlow(30)
    /** How long the volume (and the glyph intensity) takes to climb to the top. */
    val riseSec: StateFlow<Int> = _riseSec.asStateFlow()
    private var loaded = false

    @Synchronized
    private fun ensure(ctx: Context) {
        if (loaded) return
        val p = prefs(ctx)
        _offset.value = p.getInt("sync_offset_ms", 0)
        _timerSound.value = TimerSound(
            p.getString("timer_path", null),
            (p.getString("timer_name", "") ?: "").let { if (it == "Стандартный сигнал") "" else it },
            p.getString("timer_uri", null),
        )
        _alarmSound.value = TimerSound(p.getString("alarm_path", null), p.getString("alarm_name", "") ?: "", p.getString("alarm_uri", null))
        _riseSec.value = p.getInt("rise_sec", 30)
        _night.value = Night(p.getBoolean("night_on", false), p.getInt("night_from", 22 * 60), p.getInt("night_to", 7 * 60), p.getInt("night_level", 25))
        loaded = true
    }

    fun init(ctx: Context) = ensure(ctx)
    fun timerSoundNow(ctx: Context): TimerSound { ensure(ctx); return _timerSound.value }

    fun setSyncOffset(ctx: Context, ms: Int) {
        ensure(ctx)
        _offset.value = ms.coerceIn(-300, 300)
        prefs(ctx).edit().putInt("sync_offset_ms", _offset.value).apply()
    }

    fun setRiseSec(ctx: Context, sec: Int) {
        ensure(ctx)
        _riseSec.value = sec.coerceIn(5, 120)
        prefs(ctx).edit().putInt("rise_sec", _riseSec.value).apply()
    }

    fun alarmSoundNow(ctx: Context): TimerSound { ensure(ctx); return _alarmSound.value }

    fun setAlarmSound(ctx: Context, s: TimerSound) {
        ensure(ctx)
        _alarmSound.value = s
        prefs(ctx).edit().putString("alarm_path", s.path).putString("alarm_name", s.name).putString("alarm_uri", s.uri).apply()
    }

    fun setTimerSound(ctx: Context, s: TimerSound) {
        ensure(ctx)
        _timerSound.value = s
        prefs(ctx).edit().putString("timer_path", s.path).putString("timer_name", s.name).putString("timer_uri", s.uri).apply()
    }
}
