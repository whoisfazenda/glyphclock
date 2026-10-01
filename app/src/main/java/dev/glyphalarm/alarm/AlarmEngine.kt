package dev.glyphalarm.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import dev.glyphalarm.data.Alarm
import dev.glyphalarm.data.AppSettings
import dev.glyphalarm.glyph.FrameSource
import dev.glyphalarm.glyph.GlyphTrack
import dev.glyphalarm.glyph.GlyphtoneParser
import dev.glyphalarm.glyph.TrackSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** Plays the alarm sound, vibrates and exposes the matching glyph frames. Used both for real alarms and previews. */
object AlarmEngine {
    private const val TAG = "AlarmEngine"

    data class Ringing(val alarm: Alarm, val preview: Boolean)

    private val _ringing = MutableStateFlow<Ringing?>(null)
    val ringing: StateFlow<Ringing?> = _ringing.asStateFlow()

    /** Info about the glyph data found in the current sound, for the UI. */
    private val _track = MutableStateFlow<GlyphTrack?>(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var riseJob: Job? = null
    private var source: FrameSource? = null
    private var startedAt = 0L
    private var offsetMs = 0
    private var audioClock = false
    private val lock = Any()

    val current: Alarm? get() = _ringing.value?.alarm

    /** Timer progress (0..1 of time left) to display on the glyphs while nothing is ringing; null = off. */
    @Volatile var progress: Float? = null

    fun start(ctx: Context, alarm: Alarm, preview: Boolean = false) {
        stop()
        val app = ctx.applicationContext
        offsetMs = AppSettings.syncOffsetMs.value

        val track = alarm.soundPath?.let { GlyphtoneParser.parseFile(File(it)) }
        _track.value = track
        // The lights follow the recording inside the chosen sound (Glyph Composer); without one the glyphs stay dark
        val src: FrameSource? = track?.let { TrackSource(it) }
        audioClock = src is TrackSource

        startedAt = SystemClock.elapsedRealtime()
        startPlayer(app, alarm)
        if (alarm.vibrate && !preview) vibrate(app)
        synchronized(lock) { source = src }
        _ringing.value = Ringing(alarm, preview)
    }

    fun stop() {
        synchronized(lock) { source = null }
        riseJob?.cancel(); riseJob = null
        // stopping/releasing a MediaPlayer can block for a while: never do it on the UI thread
        val old = player
        player = null
        if (old != null) Thread {
            runCatching { old.stop() }
            runCatching { old.release() }
        }.start()
        focus?.let { f -> runCatching { audioManagerOrNull?.abandonAudioFocusRequest(f) } }
        focus = null
        runCatching { vibrator?.cancel() }
        _ringing.value = null
    }

    /** Fills [out] with the frame for "now". Returns false when nothing should be lit. */
    fun fillFrame(out: IntArray): Boolean {
        val s = synchronized(lock) { source }
        if (s == null) {
            // No sound playing: optionally show a running timer as a growing bar on the long C glyph
            val p = progress ?: return false
            val lit = p.coerceIn(0f, 1f) * 20f
            for (i in out.indices) out[i] = 0
            for (i in 0 until 20) out[i] = (((lit - i).coerceIn(0f, 1f)) * 2600).toInt()
            return true
        }
        val pos = if (audioClock) {
            try { player?.currentPosition?.toLong() ?: return false } catch (e: IllegalStateException) { return false }
        } else {
            SystemClock.elapsedRealtime() - startedAt
        }
        // Lead the audio slightly so light and sound feel simultaneous; tunable in the app
        s.fill(pos + offsetMs, out)
        return true
    }

    // ---- audio -------------------------------------------------------------------------------

    private var audioManagerOrNull: AudioManager? = null
    private var vibrator: android.os.Vibrator? = null

    private fun startPlayer(app: Context, alarm: Alarm) {
        val am = app.getSystemService(AudioManager::class.java).also { audioManagerOrNull = it }
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attrs).build().also { am.requestAudioFocus(it) }

        fun build(setSource: (MediaPlayer) -> Unit): MediaPlayer? = try {
            MediaPlayer().apply {
                setAudioAttributes(attrs)
                setSource(this)
                isLooping = true
                prepare()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "player setup failed", t)
            null
        }

        var mp: MediaPlayer? = null
        if (alarm.soundPath != null && File(alarm.soundPath).exists()) {
            mp = build { it.setDataSource(alarm.soundPath) }
        }
        if (mp == null) {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(app, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            mp = build { it.setDataSource(app, uri) }
            audioClock = false
        }
        val p = mp ?: return
        player = p

        if (alarm.rise) {
            val riseMs = AppSettings.riseSec.value * 1000L
            p.setVolume(0.15f, 0.15f)
            riseJob = scope.launch {
                val t0 = SystemClock.elapsedRealtime()
                while (isActive) {
                    val f = ((SystemClock.elapsedRealtime() - t0) / riseMs.toFloat()).coerceAtMost(1f)
                    val v = 0.15f + 0.85f * f
                    runCatching { p.setVolume(v, v) }
                    if (f >= 1f) break
                    delay(250)
                }
            }
        }
        p.start()
    }

    private fun vibrate(app: Context) {
        val v = app.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
        vibrator = v
        v.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 700, 500, 300, 900), 0),
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
        )
    }
}
