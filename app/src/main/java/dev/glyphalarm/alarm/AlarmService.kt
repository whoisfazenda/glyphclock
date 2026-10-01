package dev.glyphalarm.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.glyphalarm.R
import dev.glyphalarm.RingingActivity
import dev.glyphalarm.data.Alarm
import dev.glyphalarm.data.AlarmRepo
import dev.glyphalarm.data.AppSettings
import dev.glyphalarm.data.TimerItem
import dev.glyphalarm.data.TimerRepo
import dev.glyphalarm.data.TimerState
import dev.glyphalarm.data.tr

/**
 * Foreground service that keeps an alarm or a finished timer alive and audible while the ringing screen is shown.
 * Ids at or above [TimerRepo.ID_BASE] mean "timer".
 */
class AlarmService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private val giveUp = Runnable { finishRinging(snooze = false) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> { finishRinging(snooze = false); return START_NOT_STICKY }
            ACTION_SNOOZE -> { finishRinging(snooze = true); return START_NOT_STICKY }
        }

        val id = intent?.getIntExtra(AlarmScheduler.EXTRA_ID, -1) ?: -1
        val alarm: Alarm? = if (id >= TimerRepo.ID_BASE) timerAsAlarm(id) else AlarmRepo.get(this, id)
        if (alarm == null) { stopSelf(); return START_NOT_STICKY }
        val isTimer = id >= TimerRepo.ID_BASE

        ensureChannel()
        val openScreen = PendingIntent.getActivity(
            this, 1, Intent(this, RingingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(alarm.label.ifBlank { if (isTimer) tr("Таймер", "Timer") else tr("Будильник", "Alarm") })
            .setContentText(if (isTimer) tr("Время вышло", "Time is up") else "%02d:%02d".format(alarm.hour, alarm.minute))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(openScreen)
            .setFullScreenIntent(openScreen, true)
            .addAction(0, if (isTimer) tr("+1 МИН", "+1 MIN") else tr("ОТЛОЖИТЬ", "SNOOZE"), actionPi(ACTION_SNOOZE, 2))
            .addAction(0, tr("СТОП", "STOP"), actionPi(ACTION_DISMISS, 3))
            .build()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)

        wakeLock?.release()
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "glyphalarm:ring").apply { acquire(11 * 60_000L) }

        AlarmEngine.start(applicationContext, alarm)

        // With "display over other apps" granted the ringing screen can open itself — needed for the Glyphs,
        // because the Glyph kit only works for a visible app.
        if (Settings.canDrawOverlays(this)) {
            startActivity(Intent(this, RingingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        handler.removeCallbacks(giveUp)
        handler.postDelayed(giveUp, 10 * 60_000L)
        return START_NOT_STICKY
    }

    private fun timerAsAlarm(id: Int): Alarm? {
        val t = TimerRepo.get(this, id - TimerRepo.ID_BASE) ?: return null
        val s = AppSettings.timerSoundNow(this)
        return Alarm(
            id = id, hour = 0, minute = 0, label = t.label.ifBlank { tr("Таймер", "Timer") },
            soundPath = s.path, soundName = s.title(), vibrate = true, rise = false, snoozeMin = 1,
        )
    }

    private fun actionPi(action: String, code: Int) = PendingIntent.getService(
        this, code, Intent(this, AlarmService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** snooze = true: alarms ring again later, finished timers run another minute. */
    private fun finishRinging(snooze: Boolean) {
        val current = AlarmEngine.current
        if (current != null) {
            if (current.id >= TimerRepo.ID_BASE) {
                val tid = current.id - TimerRepo.ID_BASE
                val t: TimerItem? = TimerRepo.get(this, tid)
                if (t != null) {
                    if (snooze) {
                        val end = System.currentTimeMillis() + 60_000
                        TimerRepo.upsert(this, t.copy(state = TimerState.RUNNING, endAt = end, remainingMs = 60_000))
                        AlarmScheduler.scheduleTimer(this, tid, end)
                    } else {
                        TimerRepo.delete(this, tid)
                    }
                }
            } else if (snooze) {
                AlarmScheduler.scheduleSnooze(this, current.id, current.snoozeMin)
            }
        }
        handler.removeCallbacks(giveUp)
        AlarmEngine.stop()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(giveUp)
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, tr("Сработавший будильник", "Ringing alarm"), NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            )
        }
    }

    companion object {
        private const val CHANNEL = "ringing"
        private const val NOTIFICATION_ID = 42
        const val ACTION_SNOOZE = "dev.glyphalarm.SNOOZE"
        const val ACTION_DISMISS = "dev.glyphalarm.DISMISS"

        fun ringIntent(ctx: Context, id: Int) =
            Intent(ctx, AlarmService::class.java).putExtra(AlarmScheduler.EXTRA_ID, id)

        fun actionIntent(ctx: Context, action: String) =
            Intent(ctx, AlarmService::class.java).setAction(action)
    }
}
