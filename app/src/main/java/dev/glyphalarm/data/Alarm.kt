package dev.glyphalarm.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZonedDateTime

data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    /** bit 0 = Monday … bit 6 = Sunday. 0 means "once". */
    val days: Int = 0,
    val enabled: Boolean = true,
    val label: String = "",
    /** Private copy of the picked sound. null = the phone's default alarm sound. */
    val soundPath: String? = null,
    val soundName: String = "Стандартный сигнал",
    val vibrate: Boolean = true,
    /** Volume and glyph intensity climb gradually. */
    val rise: Boolean = true,
    val snoozeMin: Int = 10,
) {
    fun repeatsOn(dayIndex: Int) = days and (1 shl dayIndex) != 0

    fun nextTrigger(now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime {
        var c = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!c.isAfter(now)) c = c.plusDays(1)
        var guard = 0
        while (days != 0 && !repeatsOn(c.dayOfWeek.value - 1) && guard++ < 8) c = c.plusDays(1)
        return c
    }

    fun toJson() = JSONObject().apply {
        put("id", id); put("hour", hour); put("minute", minute); put("days", days)
        put("enabled", enabled); put("label", label)
        put("soundPath", soundPath ?: JSONObject.NULL); put("soundName", soundName)
        put("vibrate", vibrate); put("rise", rise); put("snoozeMin", snoozeMin)
    }

    companion object {
        fun fromJson(o: JSONObject) = Alarm(
            id = o.getInt("id"),
            hour = o.getInt("hour"),
            minute = o.getInt("minute"),
            days = o.optInt("days", 0),
            enabled = o.optBoolean("enabled", true),
            label = o.optString("label", ""),
            soundPath = if (o.isNull("soundPath")) null else o.getString("soundPath"),
            soundName = o.optString("soundName", "Стандартный сигнал"),
            vibrate = o.optBoolean("vibrate", true),
            rise = o.optBoolean("rise", true),
            snoozeMin = o.optInt("snoozeMin", 10),
        )
    }
}

/** Tiny JSON-in-SharedPreferences store. Everything runs in one process, so a singleton is enough. */
object AlarmRepo {
    private const val PREFS = "glyph_alarm"
    private const val KEY_ALARMS = "alarms"

    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()
    private var loaded = false

    @Synchronized
    private fun ensureLoaded(ctx: Context) {
        if (loaded) return
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _alarms.value = runCatching {
            val arr = JSONArray(p.getString(KEY_ALARMS, "[]"))
            List(arr.length()) { Alarm.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        loaded = true
    }

    fun init(ctx: Context) = ensureLoaded(ctx)

    fun all(ctx: Context): List<Alarm> { ensureLoaded(ctx); return _alarms.value }

    fun get(ctx: Context, id: Int): Alarm? = all(ctx).firstOrNull { it.id == id }

    @Synchronized
    fun upsert(ctx: Context, alarm: Alarm) {
        ensureLoaded(ctx)
        val list = _alarms.value
        _alarms.value = if (list.any { it.id == alarm.id }) list.map { if (it.id == alarm.id) alarm else it } else list + alarm
        persist(ctx)
    }

    @Synchronized
    fun delete(ctx: Context, id: Int) {
        ensureLoaded(ctx)
        _alarms.value = _alarms.value.filterNot { it.id == id }
        persist(ctx)
    }

    @Synchronized
    fun newId(ctx: Context): Int {
        ensureLoaded(ctx)
        return (_alarms.value.maxOfOrNull { it.id } ?: 0) + 1
    }

    private fun persist(ctx: Context) {
        val arr = JSONArray()
        _alarms.value.forEach { arr.put(it.toJson()) }
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ALARMS, arr.toString()).apply()
    }
}
