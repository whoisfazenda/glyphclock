package dev.glyphalarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.glyphalarm.alarm.AlarmEngine
import dev.glyphalarm.data.AlarmRepo
import dev.glyphalarm.data.AppSettings
import dev.glyphalarm.data.StopwatchRepo
import dev.glyphalarm.data.TimerRepo
import dev.glyphalarm.data.WorldRepo
import dev.glyphalarm.glyph.GlyphEngine
import dev.glyphalarm.ui.AlarmEditorScreen
import dev.glyphalarm.ui.AlarmsScreen
import dev.glyphalarm.ui.AmbientBackground
import dev.glyphalarm.ui.GlyphTheme
import dev.glyphalarm.ui.NavBar
import dev.glyphalarm.ui.RingingScreen
import dev.glyphalarm.ui.SettingsScreen
import dev.glyphalarm.ui.StopwatchScreen
import dev.glyphalarm.ui.TimerScreen
import dev.glyphalarm.ui.WorldClockScreen
import dev.glyphalarm.ui.rememberSetupItems

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AlarmRepo.init(this)
        TimerRepo.init(this)
        WorldRepo.init(this)
        StopwatchRepo.init(this)
        AppSettings.init(this)
        setContent { GlyphTheme { App() } }
    }

    // The Glyph kit only drives the LEDs for a visible app
    override fun onStart() { super.onStart(); GlyphEngine.attach(this) }

    override fun onStop() {
        if (AlarmEngine.ringing.value?.preview == true) AlarmEngine.stop()
        AlarmEngine.progress = null
        GlyphEngine.detach()
        super.onStop()
    }
}

class RingingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        AlarmRepo.init(this)
        TimerRepo.init(this)
        AppSettings.init(this)
        setContent { GlyphTheme { RingingScreen(onDone = { finishAndRemoveTask() }) } }
    }

    override fun onStart() { super.onStart(); GlyphEngine.attach(this) }
    override fun onStop() { GlyphEngine.detach(); super.onStop() }
}

private const val NONE = 0
private const val ALARM_EDIT = 1
private const val SETTINGS = 2

@Composable
private fun App() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var overlay by rememberSaveable { mutableIntStateOf(NONE) }
    var editId by rememberSaveable { mutableIntStateOf(-1) }
    val setup = rememberSetupItems()

    BackHandler(enabled = overlay == SETTINGS) { overlay = NONE }

    AmbientBackground {
        when (overlay) {
            ALARM_EDIT -> AlarmEditorScreen(editId) { overlay = NONE }
            SETTINGS -> SettingsScreen(setup) { overlay = NONE }
            else -> Box(Modifier.fillMaxSize()) {
                Crossfade(tab, animationSpec = tween(200), label = "tab") { t ->
                    when (t) {
                        0 -> AlarmsScreen(onEdit = { editId = it; overlay = ALARM_EDIT }, onSettings = { overlay = SETTINGS })
                        1 -> WorldClockScreen(onSettings = { overlay = SETTINGS })
                        2 -> TimerScreen(onSettings = { overlay = SETTINGS })
                        else -> StopwatchScreen(onSettings = { overlay = SETTINGS })
                    }
                }
                NavBar(
                    tab, { tab = it },
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                )
            }
        }
    }
}
