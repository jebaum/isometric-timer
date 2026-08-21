package dev.jebaum.isometric.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.jebaum.isometric.timer.PhaseId
import dev.jebaum.isometric.timer.PhaseKind
import dev.jebaum.isometric.timer.RoutineStatus
import dev.jebaum.isometric.timer.Snapshot
import dev.jebaum.isometric.timer.formatDuration
import dev.jebaum.isometric.timer.underway

/** Stateless rendering for the timer screen; see [TimerScreen] for the effects. */
@Composable
internal fun TimerContent(
    snapshot: Snapshot,
    /** A lambda so the per-frame value is read during draw, not composition. */
    progress: () -> Float,
    /** No routine in progress; gates both the settings button and the weight cell. */
    idle: Boolean,
    weight: String,
    onWeight: () -> Unit,
    historyMessage: String?,
    onToggle: () -> Unit,
    onSkip: () -> Unit,
    onReset: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    val resting = snapshot.phase.kind == PhaseKind.REST
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
                settingsEnabled = idle,
                onHistory = onHistory,
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
                        weight = weight,
                        weightEnabled = idle,
                        onWeight = onWeight,
                        historyMessage = historyMessage,
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
private fun TopBar(
    accent: Color,
    settingsEnabled: Boolean,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
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
        Row {
            TextButton(
                onClick = onHistory,
                modifier = Modifier.semantics { contentDescription = "Routine history" },
            ) {
                CalendarIcon(tint = Palette.Muted, modifier = Modifier.size(24.dp))
            }
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
}

@Composable
private fun TimerBody(
    snapshot: Snapshot,
    progress: () -> Float,
    accent: Color,
    accentStrong: Color,
    countdownColor: Color,
    availableHeight: Dp,
    weight: String,
    weightEnabled: Boolean,
    onWeight: () -> Unit,
    historyMessage: String?,
    onToggle: () -> Unit,
    onSkip: () -> Unit,
    onReset: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stateLabel(snapshot.status),
            color = accent,
            style = StatusLabelStyle,
            // A phase change is worth announcing. Deliberately not on the
            // countdown, which changes every second.
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (snapshot.status == RoutineStatus.COMPLETE) {
                NOTHING_LEFT
            } else {
                snapshot.phase.id.label
            },
            color = Palette.Text,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.06.em,
            textAlign = TextAlign.Center,
        )

        Countdown(
            seconds = snapshot.secondsLeft,
            color = countdownColor,
            // Dimmed while the clock is not moving *towards* anything: a
            // routine waiting to start, or one held mid-phase.
            dimmed = when (snapshot.status) {
                RoutineStatus.READY, RoutineStatus.PAUSED -> true
                RoutineStatus.RUNNING, RoutineStatus.COMPLETE -> false
            },
            availableHeight = availableHeight,
        )

        ProgressTrack(progress = progress, accent = accent, accentStrong = accentStrong)

        // Wider than the gaps below it: the block is vertically centred, so
        // space added here lifts the three headline lines by half of it while
        // the composition as a whole stays balanced.
        Spacer(Modifier.height(44.dp))
        MetaRow(
            total = formatDuration(snapshot.totalLeft),
            cycle = "${snapshot.cycle} / ${snapshot.cycles}",
            weight = weight,
            weightEnabled = weightEnabled,
            onWeight = onWeight,
            accent = accent,
        )
        Spacer(Modifier.height(22.dp))
        NextPhase(next = snapshot.next?.id?.label ?: NOTHING_LEFT, accent = accent)
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
                Text(startLabel(snapshot.status), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onSkip,
                enabled = snapshot.status.underway,
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

        // Everything but a routine that has not begun: there is nothing to end.
        val resettable = when (snapshot.status) {
            RoutineStatus.READY -> false
            RoutineStatus.RUNNING, RoutineStatus.PAUSED, RoutineStatus.COMPLETE -> true
        }
        TextButton(onClick = onReset, enabled = resettable) {
            Text(
                "End routine",
                color = if (resettable) Palette.Muted else Palette.Muted.copy(alpha = 0.38f),
                fontSize = 13.sp,
            )
        }
        if (historyMessage != null) {
            Text(
                text = historyMessage,
                color = Palette.Muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
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
        // The countdown is the screen's focal point, so it takes a wide share
        // of the width. Still bounded by the short-viewport rule so a big
        // system font cannot overflow.
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
                // The digits alone read as a bare number to a screen reader.
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
            // Purely decorative; the countdown already announces this.
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
private fun MetaRow(
    total: String,
    cycle: String,
    weight: String,
    weightEnabled: Boolean,
    onWeight: () -> Unit,
    accent: Color,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MetaCell("TOTAL", total, accent, Modifier.weight(1f))
        MetaDivider()
        MetaCell("CYCLE", cycle, accent, Modifier.weight(1f))
        MetaDivider()
        WeightCell(weight, weightEnabled, onWeight, accent, Modifier.weight(1f))
    }
}

private val WeightCellShape = RoundedCornerShape(10.dp)

/**
 * Its own composable so the second-by-second recomposition of [MetaRow] skips
 * it — none of its inputs change while a routine runs.
 */
@Composable
private fun WeightCell(
    weight: String,
    enabled: Boolean,
    onWeight: () -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    MetaCell(
        "WEIGHT",
        weight,
        accent,
        modifier
            .clip(WeightCellShape)
            // Enabled only while idle, mirroring the settings button, so the
            // recorded weight cannot change mid-session.
            .clickable(enabled = enabled, onClick = onWeight)
            .semantics { contentDescription = "Hold weight $weight" },
    )
}

@Composable
private fun MetaDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(38.dp)
            .background(Palette.Border),
    )
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

private fun stateLabel(status: RoutineStatus) = when (status) {
    RoutineStatus.READY -> "READY"
    RoutineStatus.RUNNING -> "IN PROGRESS"
    RoutineStatus.PAUSED -> "PAUSED"
    RoutineStatus.COMPLETE -> "COMPLETE"
}

/**
 * What the screen calls each phase. The timer core knows only which phase it
 * is standing on; the words for it are presentation, and live only here.
 * Internal so a JVM test can pin the exact strings the user sees.
 */
internal val PhaseId.label: String
    get() = when (this) {
        PhaseId.RIGHT_HOLD -> "RIGHT SIDE"
        PhaseId.SWITCH -> "SWITCH"
        PhaseId.LEFT_HOLD -> "LEFT SIDE"
        PhaseId.REST -> "REST"
    }

/** Shown where a phase name would be once there is no phase left to name. */
internal const val NOTHING_LEFT = "DONE"

/** What the primary button does next, which is one thing per status. */
private fun startLabel(status: RoutineStatus) = when (status) {
    RoutineStatus.READY -> "Start"
    RoutineStatus.RUNNING -> "Pause"
    RoutineStatus.PAUSED -> "Resume"
    RoutineStatus.COMPLETE -> "Again"
}
