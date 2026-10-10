package com.rossomak.flashcards.core.ui.composables.dialogs

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.composables.FlashcardsIconTile
import com.rossomak.flashcards.core.ui.composables.common.disabledAlpha
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.cornerRadius
import com.rossomak.flashcards.core.ui.theme.spacing

/**
 * A multi-select row: a leading icon tile, a label, and a trailing checkbox. Flat — no container,
 * no selection tint — because several of these are meant to be scanned as a list of independent
 * toggles rather than compared as alternatives.
 *
 * The row's own padding lives *inside* the toggleable node, so the press/hover indication covers
 * the full row band rather than hugging the icon tile; [FlashcardsMultiSelectGroup] therefore
 * stacks the rows with no gap between them. The horizontal padding is exactly the width
 * [FlashcardsMultiSelectGroup] takes past the dialog's prose margin with [dialogContentBleed], so
 * the band extends past the icon and checkbox while those stay aligned with the title and
 * supporting text above them.
 *
 * The whole row is one toggleable node with `Role.Checkbox`, so the inner [Checkbox] takes
 * `onCheckedChange = null`. Place inside a [FlashcardsMultiSelectGroup].
 */
@Composable
fun FlashcardsCheckableRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .disabledAlpha(enabled)
            .clip(RoundedCornerShape(MaterialTheme.cornerRadius.card))
            .toggleable(value = checked, enabled = enabled, onValueChange = onCheckedChange, role = Role.Checkbox)
            .padding(
                horizontal = FlashcardsDialogDefaults.contentBleed,
                vertical = MaterialTheme.spacing.xsmall,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlashcardsIconTile(
            icon = icon,
            contentDescription = null,
            contentColor = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = MaterialTheme.spacing.small),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@ShowkaseComposable(name = "Checkable row", group = "Dialogs")
@Composable
fun FlashcardsCheckableRowShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsMultiSelectGroup(modifier = Modifier.padding(MaterialTheme.spacing.small)) {
                FlashcardsCheckableRow(
                    icon = Icons.Default.ArrowUpward,
                    label = "Raise the difficulty",
                    checked = false,
                    onCheckedChange = {},
                )
                FlashcardsCheckableRow(
                    icon = Icons.Default.ArrowDownward,
                    label = "Lower the difficulty",
                    checked = true,
                    onCheckedChange = {},
                )
                FlashcardsCheckableRow(
                    icon = Icons.Default.Tag,
                    label = "Wrong tags",
                    checked = false,
                    onCheckedChange = {},
                )
                FlashcardsCheckableRow(
                    icon = Icons.Default.DataObject,
                    label = "Needs a code example",
                    checked = true,
                    onCheckedChange = {},
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsCheckableRowPreview() {
    FlashcardsCheckableRowShowcase()
}
