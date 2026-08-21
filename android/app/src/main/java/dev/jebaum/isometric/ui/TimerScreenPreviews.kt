package dev.jebaum.isometric.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.jebaum.isometric.timer.Phase
import dev.jebaum.isometric.timer.PhaseId
import dev.jebaum.isometric.timer.RoutineStatus
import dev.jebaum.isometric.timer.Settings
import dev.jebaum.isometric.timer.Snapshot
import dev.jebaum.isometric.timer.underway

private fun previewSnapshot(
    id: PhaseId = PhaseId.RIGHT_HOLD,
    secondsLeft: Int = 35,
    next: PhaseId? = PhaseId.SWITCH,
    totalLeft: Int = 570,
    cycle: Int = 1,
    status: RoutineStatus = RoutineStatus.RUNNING,
) = Snapshot(
    phase = Phase(id, 35, cycle),
    index = 0,
    // The schedule starts the next cycle right after each REST, so a preview
    // whose next phase follows a REST must number it into the next cycle.
    next = next?.let { Phase(it, 35, if (id == PhaseId.REST) cycle + 1 else cycle) },
    secondsLeft = secondsLeft,
    totalLeft = totalLeft,
    cycle = cycle,
    cycles = 4,
    status = status,
)

/**
 * The frame every routine-state preview below renders [TimerContent] in. The
 * settings and weight dialogs are previewed separately at the end of the file.
 */
@Composable
private fun PreviewFrame(snapshot: Snapshot, progress: Float) {
    IsometricTheme {
        TimerContent(
            snapshot = snapshot,
            progress = { progress },
            idle = !snapshot.status.underway,
            weight = "12.5 lb",
            onWeight = {},
            historyMessage = if (snapshot.status.underway) {
                null
            } else {
                "1 routine today · Next after 4:35 PM"
            },
            onToggle = {}, onSkip = {}, onReset = {}, onHistory = {}, onSettings = {},
        )
    }
}

@Preview(name = "Ready", showBackground = true)
@Composable
private fun ReadyPreview() =
    PreviewFrame(previewSnapshot(status = RoutineStatus.READY), 0f)

@Preview(name = "In progress", showBackground = true)
@Composable
private fun RunningPreview() =
    PreviewFrame(previewSnapshot(secondsLeft = 21), 0.4f)

@Preview(name = "Paused", showBackground = true)
@Composable
private fun PausedPreview() =
    PreviewFrame(previewSnapshot(secondsLeft = 21, status = RoutineStatus.PAUSED), 0.4f)

@Preview(name = "Hold warning", showBackground = true)
@Composable
private fun WarningPreview() =
    PreviewFrame(previewSnapshot(secondsLeft = 2), 0.94f)

@Preview(name = "Rest", showBackground = true)
@Composable
private fun RestPreview() =
    PreviewFrame(
        previewSnapshot(id = PhaseId.REST, secondsLeft = 62, next = PhaseId.RIGHT_HOLD),
        0.31f,
    )

@Preview(name = "Complete", showBackground = true)
@Composable
private fun CompletePreview() =
    PreviewFrame(
        previewSnapshot(
            secondsLeft = 0,
            next = null,
            totalLeft = 0,
            cycle = 4,
            status = RoutineStatus.COMPLETE,
        ),
        1f,
    )

/** The activity is portrait-locked, so this stands in for a large system font
 *  or display-size setting — the case the scroll fallback still exists for. */
@Preview(name = "Short window", widthDp = 411, heightDp = 560)
@Composable
private fun ShortWindowPreview() =
    PreviewFrame(previewSnapshot(secondsLeft = 21), 0.4f)

@Preview(name = "Settings dialog", showBackground = true)
@Composable
private fun SettingsPreview() {
    IsometricTheme {
        SettingsDialog(
            initial = Settings(),
            initialCuesEnabled = true,
            onDismiss = {},
            onSave = { _, _ -> },
        )
    }
}

@Preview(name = "Weight dialog", showBackground = true)
@Composable
private fun WeightPreview() {
    IsometricTheme {
        WeightDialog(initialLb = 12.5, onDismiss = {}, onSave = {})
    }
}
