package dev.glyphalarm.glyph

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphManager
import dev.glyphalarm.alarm.AlarmEngine
import dev.glyphalarm.data.AppSettings
import dev.glyphalarm.data.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Human-readable state of the link to Nothing's Glyph service, shown in Settings. */
data class GlyphStatus(val ok: Boolean, val text: String)

/**
 * Owns the connection to Nothing's Glyph service and pushes ~60 frames per second to it.
 *
 * The Glyph Developer Kit only lets a *foreground* app drive the LEDs, so every visible activity
 * calls [attach] in onStart and [detach] in onStop; the session is open while at least one is visible.
 */
object GlyphEngine {
    private const val TAG = "GlyphEngine"
    private const val N = GlyphLayout.CHANNELS

    private var refs = 0
    private var gm: GlyphManager? = null
    private var scope: CoroutineScope? = null
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var session = false
    @Volatile private var frameColorsBroken = false
    @Volatile private var frames = 0L

    private val _status = MutableStateFlow(GlyphStatus(false, tr("Не запущено", "Not started")))
    val status: StateFlow<GlyphStatus> = _status.asStateFlow()

    private val _monitor = MutableStateFlow(IntArray(N))
    /** Latest frame, for the on-screen glyph monitor. */
    val monitor: StateFlow<IntArray> = _monitor.asStateFlow()

    val deviceInfo: String get() = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"

    private val callback = object : GlyphManager.Callback {
        override fun onServiceConnected(componentName: ComponentName?) {
            main.removeCallbacks(bindTimeout)
            try {
                val registered = gm?.register(Glyph.DEVICE_24111) ?: false
                if (!registered) {
                    _status.value = GlyphStatus(false, tr("Служба найдена, но регистрация для Phone (3a) отклонена ($deviceInfo). Включите Glyph-интерфейс и режим отладки (см. ниже).", "Service found, but registration for Phone (3a) was refused ($deviceInfo). Turn on the Glyph interface and debug mode (see below)."))
                    return
                }
                gm?.openSession()
                session = true
                _status.value = GlyphStatus(true, tr("Подключено к Glyph-интерфейсу", "Connected to the Glyph interface"))
            } catch (t: Throwable) {
                Log.w(TAG, "openSession failed", t)
                session = false
                _status.value = GlyphStatus(false, tr("Ошибка сеанса: ", "Session error: ") + "${t.message ?: t.javaClass.simpleName}")
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName?) {
            session = false
            _status.value = GlyphStatus(false, tr("Glyph-служба отключилась", "The Glyph service disconnected"))
        }
    }

    private val bindTimeout = Runnable {
        if (!session) {
            _status.value = GlyphStatus(
                false,
                tr("Glyph-служба не отвечает. Убедитесь, что это телефон Nothing и Glyph-интерфейс включён; если не помогает — включите режим отладки по USB (см. ниже).", "The Glyph service is not answering. Make sure this is a Nothing phone with the Glyph interface on; if that does not help, enable USB debug mode (see below)."),
            )
        }
    }

    @Synchronized
    fun attach(ctx: Context) {
        if (refs++ > 0) return
        frameColorsBroken = false
        _status.value = GlyphStatus(false, tr("Подключение…", "Connecting…"))
        try {
            gm = GlyphManager.getInstance(ctx.applicationContext)
            gm?.init(callback)
            main.postDelayed(bindTimeout, 4000)
        } catch (t: Throwable) {
            Log.w(TAG, "Glyph service not available", t)
            _status.value = GlyphStatus(false, tr("Ошибка Glyph SDK: ", "Glyph SDK error: ") + "${t.message ?: t.javaClass.simpleName}")
        }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { it.launch { loop() } }
    }

    @Synchronized
    fun detach() {
        if (refs == 0 || --refs > 0) return
        main.removeCallbacks(bindTimeout)
        scope?.cancel()
        scope = null
        try {
            if (session) gm?.turnOff()
            gm?.closeSession()
            gm?.unInit()
        } catch (t: Throwable) {
            Log.w(TAG, "release failed", t)
        }
        session = false
        _status.value = GlyphStatus(false, tr("Не запущено", "Not started"))
        _monitor.value = IntArray(N)
    }

    /** One breathing flash of every glyph through the plain, documented kit API, to prove the link works. */
    fun selfTest(): String {
        val m = gm ?: return tr("Glyph SDK не запущен", "Glyph SDK is not running")
        if (!session) return tr("Нет сеанса: ", "No session: ") + _status.value.text
        return try {
            val frame = m.getGlyphFrameBuilder().buildChannelA().buildChannelB().buildChannelC()
                .buildPeriod(1600).buildCycles(2).buildInterval(10).build()
            m.animate(frame)
            tr("Отправлено. Глифы должны дважды плавно загореться.", "Sent. The glyphs should glow softly twice.")
        } catch (t: Throwable) {
            tr("Проверка не удалась: ", "Test failed: ") + "${t.message ?: t.javaClass.simpleName}"
        }
    }

    private suspend fun loop() {
        val buf = IntArray(N)
        var dark = true
        while (scope?.isActive == true) {
            if (AlarmEngine.fillFrame(buf)) {
                dark = false
                push(buf)
                _monitor.value = buf.copyOf()
            } else if (!dark) {
                dark = true
                buf.fill(0)
                push(buf)
                _monitor.value = buf.copyOf()
            }
            delay(16)
        }
    }

    private fun push(raw: IntArray) {
        if (!session) return
        // night mode: the same show, only dimmer
        val k = AppSettings.glyphFactor()
        val frame = if (k >= 0.999f) raw else IntArray(raw.size) { (raw[it] * k).toInt() }
        val m = gm ?: return
        try {
            if (!frameColorsBroken) {
                m.setFrameColors(frame)
            } else {
                // Fallback: describe the frame with the documented per-channel builder
                val b = m.getGlyphFrameBuilder()
                var any = false
                for (i in frame.indices) if (frame[i] > 0) { b.buildChannel(i, frame[i]); any = true }
                if (any) m.toggle(b.buildPeriod(40).buildCycles(1).buildInterval(0).build()) else m.turnOff()
            }
            frames++
        } catch (t: Throwable) {
            Log.w(TAG, "frame push failed", t)
            if (!frameColorsBroken) frameColorsBroken = true
        }
    }
}
