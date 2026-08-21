package dev.jebaum.isometric.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jebaum.isometric.timer.DEFAULT_SETTINGS
import dev.jebaum.isometric.timer.Settings
import dev.jebaum.isometric.timer.formatDuration
import dev.jebaum.isometric.timer.isValid
import dev.jebaum.isometric.timer.totalSeconds

/**
 * Every field is staged and committed together on Save, including the cue
 * switch — dismissing the dialog discards the lot, so there is one mental model
 * rather than two.
 */
@Composable
fun SettingsDialog(
    initial: Settings,
    initialCuesEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (Settings, Boolean) -> Unit,
) {
    // rememberSaveable: a rotation while the dialog is open must not silently
    // discard what has been typed.
    var cycles by rememberSaveable(initial) { mutableStateOf(initial.cycles.toString()) }
    var holdSeconds by rememberSaveable(initial) {
        mutableStateOf(initial.holdSeconds.toString())
    }
    var switchSeconds by rememberSaveable(initial) {
        mutableStateOf(initial.switchSeconds.toString())
    }
    var restSeconds by rememberSaveable(initial) {
        mutableStateOf(initial.restSeconds.toString())
    }
    var cuesEnabled by rememberSaveable(initialCuesEnabled) { mutableStateOf(initialCuesEnabled) }

    val candidate = remember(cycles, holdSeconds, switchSeconds, restSeconds) {
        parse(cycles, holdSeconds, switchSeconds, restSeconds)
    }

    AppDialog(
        onDismiss = onDismiss,
        // Four labelled fields, a preview line, a switch and two buttons can
        // outrun a short window, e.g. landscape or a large system font.
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        DialogHeader(
            eyebrow = "ROUTINE",
            title = "Timer settings",
            closeDescription = "Close settings",
            onClose = onDismiss,
        )

        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField("Cycles", cycles, { cycles = it }, Modifier.weight(1f))
            NumberField("Hold (s)", holdSeconds, { holdSeconds = it }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField("Switch (s)", switchSeconds, { switchSeconds = it }, Modifier.weight(1f))
            NumberField("Rest (s)", restSeconds, { restSeconds = it }, Modifier.weight(1f), last = true)
        }

        Spacer(Modifier.height(16.dp))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = preview(candidate),
                color = Palette.Muted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Sound and vibration", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Cues each phase change", color = Palette.Muted, fontSize = 12.sp)
            }
            Switch(
                checked = cuesEnabled,
                onCheckedChange = { cuesEnabled = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Palette.OnAccent,
                    checkedTrackColor = Palette.Accent,
                ),
            )
        }

        Spacer(Modifier.height(20.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        ) {
            Button(
                onClick = {
                    cycles = DEFAULT_SETTINGS.cycles.toString()
                    holdSeconds = DEFAULT_SETTINGS.holdSeconds.toString()
                    switchSeconds = DEFAULT_SETTINGS.switchSeconds.toString()
                    restSeconds = DEFAULT_SETTINGS.restSeconds.toString()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Palette.SurfaceSubtle,
                    contentColor = Palette.Text,
                ),
            ) {
                Text("Defaults", fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { candidate?.let { onSave(it, cuesEnabled) } },
                enabled = candidate != null,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Palette.Accent,
                    contentColor = Palette.OnAccent,
                ),
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    last: Boolean = false,
) = EntryField(
    label = label,
    value = value,
    onValueChange = { entry -> onValueChange(entry.filter { it.isDigit() }.take(4)) },
    keyboardType = KeyboardType.Number,
    modifier = modifier,
    last = last,
)

/** Returns null when the entries do not form a routine the timer will accept. */
private fun parse(
    cycles: String,
    holdSeconds: String,
    switchSeconds: String,
    restSeconds: String,
): Settings? {
    val candidate = Settings(
        cycles = cycles.toIntOrNull() ?: return null,
        holdSeconds = holdSeconds.toIntOrNull() ?: return null,
        switchSeconds = switchSeconds.toIntOrNull() ?: return null,
        restSeconds = restSeconds.toIntOrNull() ?: return null,
    )
    return candidate.takeIf { it.isValid() }
}

private fun preview(candidate: Settings?): String {
    if (candidate == null) return "Enter valid whole-second durations"
    val noun = if (candidate.cycles == 1) "cycle" else "cycles"
    return "${candidate.cycles} $noun · ${formatDuration(candidate.totalSeconds())} total"
}
