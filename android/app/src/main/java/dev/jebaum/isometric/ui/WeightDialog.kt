package dev.jebaum.isometric.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * The one-field sibling of [SettingsDialog]: the entry is staged and committed
 * on Save, and dismissing discards it.
 */
@Composable
fun WeightDialog(
    initialLb: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
) {
    // rememberSaveable: a rotation while the dialog is open must not silently
    // discard what has been typed.
    var entry by rememberSaveable(initialLb) { mutableStateOf(weightNumberText(initialLb)) }
    val candidate = parseWeightLb(entry)

    Dialog(
        onDismissRequest = onDismiss,
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
            Column(Modifier.padding(22.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("WEIGHT", color = Palette.Accent, style = MetaLabelStyle)
                        Spacer(Modifier.height(4.dp))
                        Text("Hold weight", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.semantics { contentDescription = "Close weight" },
                    ) {
                        CloseIcon(tint = Palette.Muted, modifier = Modifier.size(22.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))

                EntryField(
                    label = "Weight (lb)",
                    value = entry,
                    onValueChange = { if (isWeightEntry(it)) entry = it },
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    "Held in one hand through each hold · 0 means bodyweight only",
                    color = Palette.Muted,
                    fontSize = 12.sp,
                )

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { candidate?.let(onSave) },
                    enabled = candidate != null,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Palette.Accent,
                        contentColor = Palette.OnAccent,
                    ),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
