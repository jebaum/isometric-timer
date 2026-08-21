package dev.jebaum.isometric.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalView
import dev.jebaum.isometric.RoutineViewModel
import dev.jebaum.isometric.timer.RoutineStatus
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay

/**
 * Owns the effects; all rendering lives in [TimerContent] so every routine state
 * can be previewed without waiting nine minutes to reach it.
 */
@Composable
fun TimerScreen(viewModel: RoutineViewModel) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    var weightOpen by rememberSaveable { mutableStateOf(false) }
    var spacingWarningAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var wallTime by rememberSaveable { mutableLongStateOf(viewModel.currentWallTimeMillis()) }
    var currentZone by remember { mutableStateOf(ZoneId.systemDefault()) }
    val active = viewModel.active
    val lastCompletionAt = viewModel.lastCompletionAt
    val historyVersion = viewModel.historyVersion

    // Unlike the routine clock, the readiness message needs civil time. Update
    // once a minute so an open READY screen crosses the eight-hour mark and
    // midnight without requiring a tap or a restart.
    LaunchedEffect(lastCompletionAt) {
        while (true) {
            wallTime = viewModel.currentWallTimeMillis()
            currentZone = ZoneId.systemDefault()
            delay(60_000L)
        }
    }

    val zone = currentZone
    val locale = LocalLocale.current.platformLocale
    val today = Instant.ofEpochMilli(wallTime).atZone(zone).toLocalDate()
    val todayRange = dayRange(today, zone)
    val completionsToday = remember(today, historyVersion, zone) {
        viewModel.completionsBetween(todayRange.startInclusive, todayRange.endExclusive).size
    }
    val historyMessage = historyMessage(
        completionsToday = completionsToday,
        lastCompletionAt = lastCompletionAt,
        nowMillis = wallTime,
        zone = zone,
        locale = locale,
    )

    // Keeping the screen awake for the length of a routine is the whole
    // feature, natively.
    val view = LocalView.current
    DisposableEffect(view, active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }

    // Frames stop arriving when the app is backgrounded, which parks this loop
    // without any visibility bookkeeping. Elapsed time comes from the monotonic
    // clock, so the routine is still correct on the way back.
    LaunchedEffect(viewModel.running) {
        while (viewModel.running) {
            withFrameNanos { }
            viewModel.tick()
        }
    }

    // Remembered: TimerScreen recomposes every second while running, but the
    // weight only changes when its dialog saves.
    val weight = remember(viewModel.weightLb) { formatWeightLb(viewModel.weightLb) }

    TimerContent(
        snapshot = viewModel.snapshot,
        progress = { viewModel.progress },
        idle = !active,
        weight = weight,
        onWeight = { weightOpen = true },
        historyMessage = if (active) null else historyMessage,
        onToggle = {
            currentZone = ZoneId.systemDefault()
            // Only the tap that actually starts a routine is worth warning
            // about; Pause, Resume and Again are not.
            val warningAt = if (viewModel.snapshot.status == RoutineStatus.READY) {
                viewModel.spacingWarningAt()
            } else {
                null
            }
            if (warningAt == null) viewModel.toggle() else spacingWarningAt = warningAt
        },
        onSkip = viewModel::skip,
        onReset = viewModel::reset,
        onHistory = {
            currentZone = ZoneId.systemDefault()
            historyOpen = true
        },
        onSettings = { settingsOpen = true },
    )

    if (historyOpen) {
        HistoryDialog(
            historyVersion = historyVersion,
            nowMillis = wallTime,
            zone = zone,
            locale = locale,
            completionsBetween = viewModel::completionsBetween,
            weightHistory = viewModel::weightHistory,
            onDismiss = { historyOpen = false },
        )
    }

    if (weightOpen) {
        WeightDialog(
            initialLb = viewModel.weightLb,
            onDismiss = { weightOpen = false },
            onSave = { value ->
                viewModel.updateWeight(value)
                weightOpen = false
            },
        )
    }

    if (settingsOpen) {
        SettingsDialog(
            initial = viewModel.settings,
            initialCuesEnabled = viewModel.cuesEnabled,
            onDismiss = { settingsOpen = false },
            onSave = { settings, cuesEnabled ->
                viewModel.updatePreferences(settings, cuesEnabled)
                settingsOpen = false
            },
        )
    }

    spacingWarningAt?.let { recommendedAt ->
        AlertDialog(
            onDismissRequest = { spacingWarningAt = null },
            title = { Text("Less than 8 hours") },
            text = {
                Text(
                    "Eight hours have not passed since your last routine. " +
                        "The recommended gap ends " +
                            "${formatRecommendedAt(recommendedAt, wallTime, zone, locale)}.",
                )
            },
            dismissButton = {
                TextButton(onClick = { spacingWarningAt = null }) { Text("Wait") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        spacingWarningAt = null
                        viewModel.toggle()
                    },
                ) {
                    Text("Start anyway")
                }
            },
        )
    }
}
