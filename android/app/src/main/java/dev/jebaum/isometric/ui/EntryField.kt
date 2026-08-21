package dev.jebaum.isometric.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * The app's one text-field style, shared by the settings and weight dialogs.
 * Callers filter [onValueChange] themselves; this only styles and wires focus.
 */
@Composable
internal fun EntryField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    last: Boolean = true,
) {
    // Read here rather than passed in: threading a CompositionLocal through a
    // parameter defeats its purpose and makes the composable unskippable.
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        // The label slot associates the text with the field for accessibility,
        // which a sibling Text does not.
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
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
