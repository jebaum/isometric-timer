package dev.jebaum.isometric.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * The panel every dialog in the app is presented in: a raised rounded surface
 * inset from the screen edge, holding a padded content column.
 *
 * [modifier] applies to that content column, ahead of its padding, so whether
 * the column scrolls stays the caller's decision — only some dialogs can
 * outgrow a short window.
 */
@Composable
fun AppDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        // The platform default width would clamp the dialog narrower than the
        // cap below, which is the width this content is laid out for.
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
            Column(modifier.padding(22.dp), content = content)
        }
    }
}

/**
 * The line every dialog opens with: what section you are in, what this dialog
 * is, and the way out of it.
 */
@Composable
fun DialogHeader(
    eyebrow: String,
    title: String,
    closeDescription: String,
    onClose: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(eyebrow, color = Palette.Accent, style = MetaLabelStyle)
            Spacer(Modifier.height(4.dp))
            Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        }
        TextButton(
            onClick = onClose,
            modifier = Modifier.semantics { contentDescription = closeDescription },
        ) {
            CloseIcon(tint = Palette.Muted, modifier = Modifier.size(22.dp))
        }
    }
}
