package dev.jebaum.isometric.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

    // One field and a button always fit, so this dialog does not scroll.
    AppDialog(onDismiss = onDismiss) {
        DialogHeader(
            eyebrow = "WEIGHT",
            title = "Hold weight",
            closeDescription = "Close weight",
            onClose = onDismiss,
        )

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
