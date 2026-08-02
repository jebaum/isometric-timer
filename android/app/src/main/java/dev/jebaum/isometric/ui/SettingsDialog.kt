package dev.jebaum.isometric.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.jebaum.isometric.timer.DEFAULT_SETTINGS
import dev.jebaum.isometric.timer.Settings
import dev.jebaum.isometric.timer.clock
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
    var hold by rememberSaveable(initial) { mutableStateOf(initial.hold.toString()) }
    var switch by rememberSaveable(initial) { mutableStateOf(initial.switch.toString()) }
    var rest by rememberSaveable(initial) { mutableStateOf(initial.rest.toString()) }
    var cuesEnabled by rememberSaveable(initialCuesEnabled) { mutableStateOf(initialCuesEnabled) }

    val candidate = remember(cycles, hold, switch, rest) { parse(cycles, hold, switch, rest) }

    Dialog(
        onDismissRequest = onDismiss,
        // The stylesheet sized this min(100% - 2rem, 32rem); the platform
        // default width would clamp it narrower.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Palette.SurfaceRaised,
            contentColor = Palette.Text,
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 512.dp),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("ROUTINE", color = Palette.Accent, style = MetaLabelStyle)
                        Spacer(Modifier.height(4.dp))
                        Text("Timer settings", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.semantics { contentDescription = "Close settings" },
                    ) {
                        CloseIcon(tint = Palette.Muted, modifier = Modifier.size(22.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField("Cycles", cycles, { cycles = it }, Modifier.weight(1f))
                    NumberField("Hold (s)", hold, { hold = it }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField("Switch (s)", switch, { switch = it }, Modifier.weight(1f))
                    NumberField("Rest (s)", rest, { rest = it }, Modifier.weight(1f), last = true)
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
                            hold = DEFAULT_SETTINGS.hold.toString()
                            switch = DEFAULT_SETTINGS.switch.toString()
                            rest = DEFAULT_SETTINGS.rest.toString()
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
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    last: Boolean = false,
) {
    // Read here rather than passed in: threading a CompositionLocal through a
    // parameter defeats its purpose and makes the composable unskippable.
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = { entry -> onValueChange(entry.filter { it.isDigit() }.take(4)) },
        // The label slot associates the text with the field for accessibility,
        // which a sibling Text does not.
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = if (last) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Next) },
            onDone = { focusManager.clearFocus() },
        ),
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Palette.Surface,
            unfocusedContainerColor = Palette.Surface,
            focusedTextColor = Palette.Text,
            unfocusedTextColor = Palette.Text,
            focusedLabelColor = Palette.Accent,
            unfocusedLabelColor = Palette.Muted,
            focusedIndicatorColor = Palette.Accent,
            unfocusedIndicatorColor = Palette.Border,
            cursorColor = Palette.Accent,
        ),
        modifier = modifier,
    )
}

/** Returns null when the entries do not form a routine the timer will accept. */
private fun parse(cycles: String, hold: String, switch: String, rest: String): Settings? {
    val candidate = Settings(
        cycles = cycles.toIntOrNull() ?: return null,
        hold = hold.toIntOrNull() ?: return null,
        switch = switch.toIntOrNull() ?: return null,
        rest = rest.toIntOrNull() ?: return null,
    )
    return candidate.takeIf { it.isValid() }
}

private fun preview(candidate: Settings?): String {
    if (candidate == null) return "Enter valid whole-second durations"
    val noun = if (candidate.cycles == 1) "cycle" else "cycles"
    return "${candidate.cycles} $noun · ${clock(candidate.totalSeconds())} total"
}
