package dev.glyphalarm.ui

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import dev.glyphalarm.data.tr

/**
 * Opens the phone's own "Select ringtone" page (the Nothing sound library: Nothing Signals, Glyph Composer, My sounds…)
 * and hands the chosen sound back. [current] is the sound chosen before, so the page ticks it instead of "silent".
 * Returns the function that launches it.
 */
@Composable
fun rememberSystemRingtonePicker(current: String?, onUri: (Uri) -> Unit): () -> Unit {
    val cb = rememberUpdatedState(onUri)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)?.let(cb.value)
    }
    val existing = rememberUpdatedState(current)
    return {
        launcher.launch(
            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, tr("Выбрать мелодию", "Choose a melody"))
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing.value?.let(Uri::parse))
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false),
        )
    }
}
