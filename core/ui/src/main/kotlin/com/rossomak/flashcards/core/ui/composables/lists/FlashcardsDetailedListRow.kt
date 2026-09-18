package com.rossomak.flashcards.core.ui.composables.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.composables.FlashcardsIconTile
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.spacing

/** Opacity applied to the whole row when `enabled = false`, matching [FlashcardsListRow]. */
private const val DISABLED_ALPHA = 0.6f

/**
 * The generic design-system 3-line list row: an optional [leading] slot, a title, a wrapping
 * [subtitle] description, a [secondaryText] label/count below it, and an optional [trailing]
 * slot. Category rows are this row; a row that only needs one extra line is [FlashcardsListRow]
 * instead — both [subtitle] and [secondaryText] are mandatory here, so the two row shapes can't
 * be conflated at a call site.
 *
 * The whole row is one merged, clickable node with the given [role]; decorative trailing
 * chevrons should pass `contentDescription = null` (see [FlashcardsChevron]). When [enabled] is
 * `false`, the whole row dims rather than only disabling the click.
 */
@Composable
fun FlashcardsDetailedListRow(
    title: String,
    subtitle: String,
    secondaryText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isFavorited: Boolean = false,
    role: Role = Role.Button,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .heightIn(min = MaterialTheme.sizes.listRowMinHeight)
            .clickable(enabled = enabled, role = role, onClick = onClick)
            .padding(
                horizontal = MaterialTheme.spacing.normal,
                vertical = MaterialTheme.spacing.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        if (leading != null) {
            leading()
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (leading != null) MaterialTheme.spacing.xxsmall else MaterialTheme.spacing.none),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.none),
        ) {
            FlashcardsRowTitleText(title = title, isFavorited = isFavorited)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = secondaryText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@ShowkaseComposable(name = "Detailed list row — category", group = "Lists")
@Composable
fun FlashcardsDetailedListRowShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsDetailedListRow(
                title = "Android",
                subtitle = "Compose · Coroutines · Compose Navigation",
                secondaryText = "13 topics",
                onClick = {},
                trailing = { FlashcardsChevron() },
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsDetailedListRowPreview() {
    FlashcardsTheme {
        Surface {
            Column(
                modifier = Modifier
                    .padding(MaterialTheme.spacing.normal)
                    .flashcardsListGroupContainer()
            ) {
                FlashcardsDetailedListRow(
                    modifier = Modifier.flashcardsListItemShape(FlashcardsListItemPosition.Top),
                    title = "Android",
                    subtitle = "Compose · Coroutines · Compose Navigation",
                    secondaryText = "13 topics",
                    onClick = {},
                    leading = {
                        FlashcardsIconTile(
                            icon = Icons.Default.Android,
                            contentDescription = null,
                        )
                    },
                    trailing = { FlashcardsChevron() },
                )
                FlashcardsDetailedListRow(
                    modifier = Modifier.flashcardsListItemShape(FlashcardsListItemPosition.Bottom),
                    title = "Python",
                    subtitle = "Async · Typing · Decorators",
                    secondaryText = "6 topics",
                    onClick = {},
                    leading = {
                        FlashcardsIconTile(
                            icon = Icons.Default.Terminal,
                            contentDescription = null,
                        )
                    },
                    trailing = { FlashcardsChevron() },
                )
            }
        }
    }
}
