package dev.glyphalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import dev.glyphalarm.MainActivity
import dev.glyphalarm.data.Alarm
import dev.glyphalarm.data.AlarmRepo
import dev.glyphalarm.data.TimerRepo
import dev.glyphalarm.data.TimerState

object AlarmScheduler {
    const val ACTION_FIRE = "dev.glyphalarm.FIRE"
    const val ACTION_TIMER = "dev.glyphalarm.TIMER"
    const val EXTRA_ID = "alarm_id"
    const val EXTRA_SNOOZE = "snooze"
    private const val SNOOZE_CODE_BASE = 100_000
    private const val TIMER_CODE_BASE = 200_000

    fun schedule(ctx: Context, alarm: Alarm) {
        if (!alarm.enabled) { cancel(ctx, alarm.id); return }
        setAt(ctx, alarm.nextTrigger().toInstant().toEpochMilli(), firePi(ctx, alarm.id, false))
    }

    fun scheduleSnooze(ctx: Context, alarmId: Int, minutes: Int) {
        setAt(ctx, System.currentTimeMillis() + minutes * 60_000L, firePi(ctx, alarmId, true))
    }

    fun cancel(ctx: Context, id: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(firePi(ctx, id, false))
        am.cancel(firePi(ctx, id, true))
    }

    fun scheduleTimer(ctx: Context, timerId: Int, endAtMs: Long) = setAt(ctx, endAtMs, timerPi(ctx, timerId))

    fun cancelTimer(ctx: Context, timerId: Int) = ctx.getSystemService(AlarmManager::class.java).cancel(timerPi(ctx, timerId))

    fun rescheduleAll(ctx: Context) {
        AlarmRepo.all(ctx).forEach { schedule(ctx, it) }
        TimerRepo.all(ctx).filter { it.state == TimerState.RUNNING }.forEach { scheduleTimer(ctx, it.id, it.endAt) }
    }

    private fun setAt(ctx: Context, triggerAtMs: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val show = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        try {
            // setAlarmClock is exempt from Doze and from the exact-alarm permission: it is what real clock apps use
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMs, show), pi)
        } catch (t: SecurityException) {
            Log.w("AlarmScheduler", "cannot schedule", t)
        }
    }

    private fun firePi(ctx: Context, id: Int, snooze: Boolean): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_SNOOZE, snooze)
        return PendingIntent.getBroadcast(
            ctx, if (snooze) SNOOZE_CODE_BASE + id else id, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun timerPi(ctx: Context, id: Int): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_TIMER).putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(ctx, TIMER_CODE_BASE + id, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1)
        when (intent.action) {
            AlarmScheduler.ACTION_FIRE -> {
                val alarm = AlarmRepo.get(ctx, id) ?: return
                if (!intent.getBooleanExtra(AlarmScheduler.EXTRA_SNOOZE, false)) {
                    if (alarm.days == 0) AlarmRepo.upsert(ctx, alarm.copy(enabled = false)) // one-shot alarm is used up
                    else AlarmScheduler.schedule(ctx, alarm)                                 // queue the next repeat
                }
                ContextCompat.startForegroundService(ctx, AlarmService.ringIntent(ctx, id))
            }
            AlarmScheduler.ACTION_TIMER -> {
                val t = TimerRepo.get(ctx, id) ?: return
                if (t.state != TimerState.RUNNING) return // paused or deleted meanwhile: stale alarm
                TimerRepo.upsert(ctx, t.copy(state = TimerState.DONE, remainingMs = 0))
                ContextCompat.startForegroundService(ctx, AlarmService.ringIntent(ctx, TimerRepo.ID_BASE + id))
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        AlarmScheduler.rescheduleAll(ctx)
    }
}
