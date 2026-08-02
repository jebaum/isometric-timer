package dev.jebaum.isometric.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.jebaum.isometric.RoutineViewModel
import dev.jebaum.isometric.timer.Kind
import dev.jebaum.isometric.timer.Phase
import dev.jebaum.isometric.timer.Settings
import dev.jebaum.isometric.timer.Snapshot
import dev.jebaum.isometric.timer.clock

/**
 * Owns the effects; all rendering lives in [TimerContent] so every routine state
 * can be previewed without waiting nine minutes to reach it.
 */
@Composable
fun TimerScreen(viewModel: RoutineViewModel) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val active = viewModel.active

    // The web app had to juggle the Wake Lock API and reacquire on visibility
    // change; natively this is the whole feature.
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

    TimerContent(
        snapshot = viewModel.snapshot,
        progress = { viewModel.progress },
        settingsEnabled = !active,
        onToggle = viewModel::toggle,
        onSkip = viewModel::skip,
        onReset = viewModel::reset,
        onSettings = { settingsOpen = true },
    )

    if (settingsOpen) {
        SettingsDialog(
            initial = viewModel.settings,
            initialCuesEnabled = viewModel.cuesEnabled,
            onDismiss = { settingsOpen = false },
            onSave = { settings, cuesEnabled ->
                viewModel.updateCuesEnabled(cuesEnabled)
                viewModel.updateSettings(settings)
                settingsOpen = false
            },
        )
    }
}

@Composable
private fun TimerContent(
    snapshot: Snapshot,
    /** A lambda so the per-frame value is read during draw, not composition. */
    progress: () -> Float,
    settingsEnabled: Boolean,
    onToggle: () -> Unit,
    onSkip: () -> Unit,
    onReset: () -> Unit,
    onSettings: () -> Unit,
) {
    val resting = snapshot.phase.kind == Kind.REST
    val accent = if (resting) Palette.Rest else Palette.Accent
    val accentStrong = if (resting) Palette.RestStrong else Palette.AccentStrong

    Box(
        Modifier
            .fillMaxSize()
            // Flat colour first so the translucent gradient composites over it
            // exactly as it did when it was the card's fill.
            .background(Palette.Background)
            .background(
                Brush.verticalGradient(listOf(Palette.BackdropTop, Palette.BackdropBottom)),
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .widthIn(max = 736.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 24.dp),
        ) {
            TopBar(
                accent = accent,
                settingsEnabled = settingsEnabled,
                onSettings = onSettings,
            )
            BoxWithConstraints(Modifier.weight(1f)) {
                val available = maxHeight
                // Scrolls only when the card genuinely does not fit — landscape,
                // a large display size, or a big system font.
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = available)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    TimerBody(
                        snapshot = snapshot,
                        progress = progress,
                        accent = accent,
                        accentStrong = accentStrong,
                        countdownColor = if (snapshot.warning) Palette.Warning else accent,
                        availableHeight = available,
                        onToggle = onToggle,
                        onSkip = onSkip,
                        onReset = onReset,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(accent: Color, settingsEnabled: Boolean, onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "ISOMETRIC",
            color = accent,
            style = MetaLabelStyle,
        )
        TextButton(
            onClick = onSettings,
            enabled = settingsEnabled,
            modifier = Modifier.semantics { contentDescription = "Routine settings" },
        ) {
            SlidersIcon(
                tint = if (settingsEnabled) Palette.Muted else Palette.Muted.copy(alpha = 0.38f),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun TimerBody(
    snapshot: Snapshot,
    progress: () -> Float,
    accent: Color,
    accentStrong: Color,
    countdownColor: Color,
    availableHeight: Dp,
    onToggle: () -> Unit,
    onSkip: () -> Unit,
    onReset: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stateLabel(snapshot),
            color = accent,
            style = StatusLabelStyle,
            // Mirrors aria-live="polite" on the web status line. Deliberately
            // not on the countdown, which changes every second.
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (snapshot.done) "DONE" else snapshot.phase.label,
            color = Palette.Text,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.06.em,
            textAlign = TextAlign.Center,
        )

        Countdown(
            seconds = snapshot.secondsLeft,
            color = countdownColor,
            dimmed = !snapshot.started || snapshot.paused,
            availableHeight = availableHeight,
        )

        ProgressTrack(progress = progress, accent = accent, accentStrong = accentStrong)

        // Wider than the gaps below it: the block is vertically centred, so
        // space added here lifts the three headline lines by half of it while
        // the composition as a whole stays balanced.
        Spacer(Modifier.height(44.dp))
        MetaRow(
            total = clock(snapshot.totalLeft),
            cycle = "${snapshot.cycle} / ${snapshot.cycles}",
            accent = accent,
        )
        Spacer(Modifier.height(22.dp))
        NextPhase(next = snapshot.next, accent = accent)
        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth()) {
            Button(
                onClick = onToggle,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Palette.Accent,
                    contentColor = Palette.OnAccent,
                ),
            ) {
                Text(startLabel(snapshot), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onSkip,
                enabled = snapshot.started && !snapshot.done,
                modifier = Modifier
                    .width(116.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Palette.SurfaceSubtle,
                    contentColor = Palette.Text,
                    disabledContainerColor = Palette.SurfaceSubtle.copy(alpha = 0.04f),
                    disabledContentColor = Palette.Text.copy(alpha = 0.38f),
                ),
            ) {
                Text("Skip", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        }

        val resettable = snapshot.started || snapshot.done
        TextButton(onClick = onReset, enabled = resettable) {
            Text(
                "End routine",
                color = if (resettable) Palette.Muted else Palette.Muted.copy(alpha = 0.38f),
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun Countdown(seconds: Int, color: Color, dimmed: Boolean, availableHeight: Dp) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Was 35vw, matching the stylesheet; widened once the card chrome went
        // away and the countdown became the screen's focal point. Still bounded
        // by the short-viewport rule so a big system font cannot overflow.
        val short = availableHeight < 680.dp
        val size = minOf(maxWidth * 0.42f, availableHeight * 0.25f)
            .coerceIn(if (short) 88.dp else 112.dp, if (short) 144.dp else 224.dp)
        // Converted through density rather than used as a raw sp value: this is
        // a "fill the box" measure and must not move with the system font scale.
        val fontSize = with(LocalDensity.current) { size.toSp() }

        Text(
            text = seconds.toString().padStart(2, '0'),
            color = color,
            fontSize = fontSize,
            lineHeight = fontSize * 0.9f,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.06).em,
            // Four digits (a hold or rest of 1000s+) on the narrowest reachable
            // window leaves only a few dp of margin; without this it would wrap
            // to two lines rather than simply running close to the edge.
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .alpha(if (dimmed) 0.7f else 1f)
                // Mirrors role="timer" + aria-label from the web app.
                .semantics { contentDescription = "$seconds seconds remaining" },
        )
    }
}

@Composable
private fun ProgressTrack(progress: () -> Float, accent: Color, accentStrong: Color) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(9.dp)
            // Mirrors aria-hidden="true"; the countdown already announces this.
            .clearAndSetSemantics { },
    ) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(color = Palette.TrackFill, cornerRadius = radius)
        val filled = size.width * progress().coerceIn(0f, 1f)
        if (filled > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(accentStrong, accent),
                    startX = 0f,
                    endX = size.width,
                ),
                topLeft = Offset.Zero,
                size = Size(filled, size.height),
                cornerRadius = radius,
            )
        }
    }
}

@Composable
private fun MetaRow(total: String, cycle: String, accent: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MetaCell("TOTAL", total, accent, Modifier.weight(1f))
        Box(
            Modifier
                .width(1.dp)
                .height(38.dp)
                .background(Palette.Border),
        )
        MetaCell("CYCLE", cycle, accent, Modifier.weight(1f))
    }
}

@Composable
private fun MetaCell(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = accent, style = MetaLabelStyle)
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            color = Palette.Text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            // Merged explicitly: passing TabularStyle as `style` alone would
            // discard the ambient text style rather than adding to it.
            style = LocalTextStyle.current.merge(TabularStyle),
        )
    }
}

@Composable
private fun NextPhase(next: String, accent: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 53.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.NextBackground)
            .border(1.dp, Palette.Border, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = "NEXT", color = accent, style = MetaLabelStyle)
        Text(
            text = next,
            color = Palette.Text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.05.em,
        )
    }
}

private fun stateLabel(snapshot: Snapshot) = when {
    snapshot.done -> "COMPLETE"
    !snapshot.started -> "READY"
    snapshot.paused -> "PAUSED"
    else -> "IN PROGRESS"
}

private fun startLabel(snapshot: Snapshot) = when {
    snapshot.done -> "Again"
    !snapshot.started -> "Start"
    snapshot.paused -> "Resume"
    else -> "Pause"
}

// ---- previews ---------------------------------------------------------------

private fun previewSnapshot(
    label: String = "RIGHT SIDE",
    kind: Kind = Kind.HOLD,
    secondsLeft: Int = 35,
    next: String = "SWITCH",
    totalLeft: Int = 570,
    cycle: Int = 1,
    paused: Boolean = false,
    started: Boolean = true,
    done: Boolean = false,
) = Snapshot(
    phase = Phase(label, 35, kind, cycle),
    index = 0,
    next = next,
    secondsLeft = secondsLeft,
    totalLeft = totalLeft,
    cycle = cycle,
    cycles = 4,
    paused = paused,
    started = started,
    done = done,
)

@Composable
private fun PreviewFrame(snapshot: Snapshot, progress: Float) {
    IsometricTheme {
        TimerContent(
            snapshot = snapshot,
            progress = { progress },
            settingsEnabled = !snapshot.started || snapshot.done,
            onToggle = {}, onSkip = {}, onReset = {}, onSettings = {},
        )
    }
}

@Preview(name = "Ready", showBackground = true)
@Composable
private fun ReadyPreview() =
    PreviewFrame(previewSnapshot(started = false), 0f)

@Preview(name = "In progress", showBackground = true)
@Composable
private fun RunningPreview() =
    PreviewFrame(previewSnapshot(secondsLeft = 21), 0.4f)

@Preview(name = "Hold warning", showBackground = true)
@Composable
private fun WarningPreview() =
    PreviewFrame(previewSnapshot(secondsLeft = 2), 0.94f)

@Preview(name = "Rest", showBackground = true)
@Composable
private fun RestPreview() =
    PreviewFrame(
        previewSnapshot(label = "REST", kind = Kind.REST, secondsLeft = 62, next = "RIGHT SIDE"),
        0.31f,
    )

@Preview(name = "Complete", showBackground = true)
@Composable
private fun CompletePreview() =
    PreviewFrame(
        previewSnapshot(secondsLeft = 0, next = "DONE", totalLeft = 0, cycle = 4, done = true),
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
