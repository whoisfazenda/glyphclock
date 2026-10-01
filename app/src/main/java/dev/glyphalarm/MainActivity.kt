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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween as tweenSpec
import androidx.compose.foundation.layout.navigationBarsPadding as navPad
import androidx.compose.ui.platform.LocalContext
import dev.glyphalarm.alarm.AlarmScheduler
import dev.glyphalarm.data.Alarm
import dev.glyphalarm.ui.UndoBar
import dev.glyphalarm.ui.NavBarSpace
import kotlinx.coroutines.delay
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
import dev.glyphalarm.data.SoundImporter
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
        Thread {
            val keep = AlarmRepo.all(this).mapNotNull { it.soundPath }.toMutableSet()
            AppSettings.timerSoundNow(this).path?.let { keep += it }
            SoundImporter.sweep(this, keep)
        }.start()
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
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var overlay by rememberSaveable { mutableIntStateOf(NONE) }
    var editId by rememberSaveable { mutableIntStateOf(-1) }
    var addingCity by rememberSaveable { mutableStateOf(false) }
    var creatingTimer by rememberSaveable { mutableStateOf(false) }
    var deleted by remember { mutableStateOf<Alarm?>(null) }
    val timers by TimerRepo.timers.collectAsState()
    val setup = rememberSetupItems()

    // "Deleted · Undo": the sound file of the removed alarm is only dropped once the offer has run out
    LaunchedEffect(deleted) {
        val d = deleted ?: return@LaunchedEffect
        delay(6000)
        if (deleted == d) { SoundImporter.discard(d.soundPath); deleted = null }
    }

    BackHandler(enabled = overlay == SETTINGS) { overlay = NONE }

    AmbientBackground {
        when (overlay) {
            ALARM_EDIT -> AlarmEditorScreen(editId, onClose = { overlay = NONE }, onDeleted = {
                deleted?.let { old -> SoundImporter.discard(old.soundPath) }
                deleted = it
            })
            SETTINGS -> SettingsScreen(setup) { overlay = NONE }
            else -> Box(Modifier.fillMaxSize()) {
                Crossfade(tab, animationSpec = tween(220), label = "tab") { t ->
                    when (t) {
                        0 -> AlarmsScreen(onEdit = { editId = it; overlay = ALARM_EDIT }, onSettings = { overlay = SETTINGS })
                        1 -> WorldClockScreen(addingCity, { addingCity = it }, onSettings = { overlay = SETTINGS })
                        2 -> TimerScreen(creatingTimer, { creatingTimer = it }, onSettings = { overlay = SETTINGS })
                        else -> StopwatchScreen(onSettings = { overlay = SETTINGS })
                    }
                }
                val hideBar = tab == 1 && addingCity
                if (!hideBar) {
                    val plus: (() -> Unit)? = when (tab) {
                        0 -> ({ editId = -1; overlay = ALARM_EDIT })
                        1 -> ({ addingCity = true })
                        2 -> if (timers.isNotEmpty() && !creatingTimer) ({ creatingTimer = true }) else null
                        else -> null
                    }
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                        NavBar(tab, { tab = it }, Modifier.fillMaxWidth(), onPlus = plus)
                    }
                    AnimatedVisibility(
                        deleted != null,
                        Modifier.align(Alignment.BottomCenter).navPad().padding(bottom = NavBarSpace + 4.dp),
                        enter = fadeIn(tweenSpec(180)) + slideInVertically(tweenSpec(180)) { it / 2 },
                        exit = fadeOut(tweenSpec(180)) + slideOutVertically(tweenSpec(180)) { it / 2 },
                    ) {
                        val d = deleted
                        UndoBar("Будильник удалён", "Вернуть", {
                            if (d != null) {
                                AlarmRepo.upsert(ctx, d)
                                if (d.enabled) AlarmScheduler.schedule(ctx, d)
                                deleted = null
                            }
                        })
                    }
                }
            }
        }
    }
}
