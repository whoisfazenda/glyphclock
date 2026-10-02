package dev.glyphalarm.alarm

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.glyphalarm.MainActivity
import dev.glyphalarm.data.AlarmRepo
import dev.glyphalarm.data.tr
import dev.glyphalarm.ui.formatClock
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

/** Quick Settings tile: shows the next alarm and opens the app. */
class AlarmTileService : TileService() {
    override fun onStartListening() = refresh()

    override fun onClick() {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        startActivityAndCollapse(open)
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val now = ZonedDateTime.now()
        val next = AlarmRepo.all(this).filter { it.enabled }.minByOrNull { it.nextTrigger(now).toEpochSecond() }
        tile.label = tr("Будильник", "Alarm")
        if (next == null) {
            tile.state = Tile.STATE_INACTIVE
            tile.subtitle = tr("Нет будильников", "No alarms")
        } else {
            val at = next.nextTrigger(now)
            val is24 = android.text.format.DateFormat.is24HourFormat(this)
            val (t, suffix) = formatClock(at.hour, at.minute, is24)
            val day = at.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).replaceFirstChar { it.uppercase() }
            tile.state = Tile.STATE_ACTIVE
            tile.subtitle = "$day $t $suffix".trim()
        }
        tile.updateTile()
    }
}
