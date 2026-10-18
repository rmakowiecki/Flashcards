package com.rossomak.flashcards.core.ui.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.designsystem.theme.cornerRadius
import com.rossomak.flashcards.core.designsystem.theme.sizes
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme

private val TAG_CHIP_HEIGHT = 40.dp
private val TAG_CHIP_ICON_SIZE = 18.dp

/**
 * The checkable tag chip — a Material 3 [FilterChip] restyled to the design system: 40dp tall,
 * 12dp corners, a heavier-than-hairline outline when unselected, and a solid secondary-container
 * fill with a leading check when selected.
 *
 * This is the interactive chip used to pick study tags (filter dialogs, tag pickers). For the
 * smaller read-only stat pill see [FlashcardsMetadataBadge].
 */
@Composable
fun FlashcardsTagChip(
    label: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = { onSelectedChange(!selected) },
        label = { Text(text = label) },
        modifier = modifier.height(TAG_CHIP_HEIGHT),
        enabled = enabled,
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(TAG_CHIP_ICON_SIZE),
                )
            }
        } else {
            null
        },
        shape = RoundedCornerShape(MaterialTheme.cornerRadius.normal),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = Color.Transparent,
            borderWidth = MaterialTheme.sizes.hairline,
            selectedBorderWidth = MaterialTheme.spacing.none,
        ),
    )
}

@Composable
private fun TagChipSampleRow(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(MaterialTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall)) {
            FlashcardsTagChip(label = "Composables", selected = false, onSelectedChange = {})
            FlashcardsTagChip(label = "Modifiers", selected = false, onSelectedChange = {})
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall)) {
            FlashcardsTagChip(label = "Composables", selected = true, onSelectedChange = {})
            FlashcardsTagChip(label = "private", selected = true, onSelectedChange = {})
        }
    }
}

@ShowkaseComposable(name = "Tag chip — unselected", group = "Chips & Badges")
@Composable
fun FlashcardsTagChipUnselectedShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsTagChip(
                label = "State Management",
                selected = false,
                onSelectedChange = {},
                modifier = Modifier.padding(MaterialTheme.spacing.small),
            )
        }
    }
}

@ShowkaseComposable(name = "Tag chip — selected", group = "Chips & Badges")
@Composable
fun FlashcardsTagChipSelectedShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsTagChip(
                label = "State Management",
                selected = true,
                onSelectedChange = {},
                modifier = Modifier.padding(MaterialTheme.spacing.small),
            )
        }
    }
}

@ShowkaseComposable(name = "Tag chip — group", group = "Chips & Badges")
@Composable
fun FlashcardsTagChipGroupShowcase() {
    FlashcardsTheme {
        Surface {
            TagChipSampleRow()
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsTagChipPreview() {
    FlashcardsTheme {
        Surface {
            TagChipSampleRow()
        }
    }
}
