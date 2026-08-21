package dev.jebaum.isometric

import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.jebaum.isometric.cues.AndroidCuePlayer
import dev.jebaum.isometric.ui.IsometricTheme
import dev.jebaum.isometric.ui.TimerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is dark regardless of the system setting, so the system bars
        // have to be told explicitly. The default auto() style reads uiMode and
        // would draw dark icons over this app's near-black background whenever
        // the phone is in light mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        val factory = viewModelFactory {
            initializer {
                RoutineViewModel(
                    // Application context: the view model outlives this activity.
                    store = PreferencesSettingsStore(applicationContext),
                    player = AndroidCuePlayer(applicationContext, failures = LogcatFailureReporter),
                    now = { SystemClock.elapsedRealtime() / 1000.0 },
                    history = SQLiteCompletionHistoryStore(applicationContext),
                    wallNow = System::currentTimeMillis,
                    // The one place Logcat is wired in, so nothing underneath
                    // has to know that is where its swallowed failures land.
                    failures = LogcatFailureReporter,
                )
            }
        }

        setContent {
            IsometricTheme {
                TimerScreen(viewModel = viewModel<RoutineViewModel>(factory = factory))
            }
        }
    }
}
