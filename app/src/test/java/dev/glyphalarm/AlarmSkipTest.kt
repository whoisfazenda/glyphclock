package dev.glyphalarm

import dev.glyphalarm.data.Alarm
import dev.glyphalarm.data.AppSettings
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmSkipTest {
    private val zone = ZoneId.of("Europe/Berlin")
    // Monday 2026-10-05 06:00
    private val now = ZonedDateTime.of(2026, 10, 5, 6, 0, 0, 0, zone)

    @Test fun skippingMovesToNextMatchingDay() {
        val daily = Alarm(1, 7, 0, days = 127)
        val skipped = daily.withSkip(true, now)
        assertTrue(skipped.isSkipped(now))
        assertEquals(6, skipped.nextTrigger(now).dayOfMonth)   // Tuesday instead of today
        assertFalse(skipped.withSkip(false, now).isSkipped(now))
    }

    @Test fun weekdaysSkipOverTheWeekend() {
        val weekdays = Alarm(2, 7, 0, days = 31)
        val fri = ZonedDateTime.of(2026, 10, 9, 6, 0, 0, 0, zone)
        assertEquals(12, weekdays.withSkip(true, fri).nextTrigger(fri).dayOfMonth) // Monday
    }

    @Test fun oneTimeAlarmCannotBeSkipped() {
        assertFalse(Alarm(3, 7, 0).withSkip(true, now).isSkipped(now))
    }

    @Test fun nightFactorWrapsMidnight() {
        val n = AppSettings.Night(on = true, fromMin = 22 * 60, toMin = 7 * 60, levelPct = 20)
        assertEquals(0.2f, n.factorAt(23 * 60), 0.001f)
        assertEquals(0.2f, n.factorAt(3 * 60), 0.001f)
        assertEquals(1f, n.factorAt(12 * 60), 0.001f)
        assertEquals(1f, n.copy(on = false).factorAt(23 * 60), 0.001f)
    }
}
