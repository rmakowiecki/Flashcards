package com.rossomak.flashcards.core.ui.composables.lists

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.composables.FlashcardsIconTile
import com.rossomak.flashcards.core.ui.theme.FlashcardsMotion
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.spacing

/** Opacity of [MaterialTheme.colorScheme.secondaryContainer] used as a selection tint. */
private const val SELECTED_TINT_ALPHA = 0.12f

/** Opacity applied to the whole row when `enabled = false`, matching [FlashcardsListRow]. */
private const val DISABLED_ALPHA = 0.6f

/**
 * A multi-select list row: an optional [leading] slot, a title with optional [subtitle], a
 * trailing checkbox, and a selection tint that animates in when [selected]. The whole row is one
 * toggleable node with `Role.Checkbox`, so the inner [Checkbox] takes `onCheckedChange = null`
 * and TalkBack announces the row as a single checked/unchecked control rather than a row and a
 * checkbox announced separately. There is no second trailing slot — a row that needs to keep
 * showing e.g. a chevron alongside the checkbox isn't this row's shape.
 *
 * [subtitleContent] overrides [subtitle] when given, for a caller that needs more than a
 * single-styled line — see [FlashcardsListRow]'s own `secondaryContent`.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FlashcardsSelectableListRow(
    title: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    leading: @Composable (() -> Unit)? = null,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = SELECTED_TINT_ALPHA)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        animationSpec = tween(FlashcardsMotion.DURATION_SHORT_MS, easing = FlashcardsMotion.StandardEasing),
    )
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .heightIn(min = MaterialTheme.sizes.listRowMinHeight)
            .toggleable(
                value = selected,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onSelectedChange,
            )
            .background(backgroundColor)
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
            Text(
                text = title,
                modifier = Modifier.basicMarquee(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            if (subtitleContent != null) {
                subtitleContent()
            } else if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Checkbox(
            checked = selected,
            onCheckedChange = null,
        )
    }
}

@ShowkaseComposable(name = "List row — selectable, checked", group = "Lists")
@Composable
fun FlashcardsSelectableListRowCheckedShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsSelectableListRow(
                title = "Compose",
                selected = true,
                onSelectedChange = {},
                subtitle = "80 cards",
                leading = {
                    FlashcardsIconTile(
                        icon = Icons.Default.Star,
                        contentDescription = null,
                    )
                },
            )
        }
    }
}

@ShowkaseComposable(name = "List row — selectable, unchecked", group = "Lists")
@Composable
fun FlashcardsSelectableListRowUncheckedShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsSelectableListRow(
                title = "Testing",
                selected = false,
                onSelectedChange = {},
                subtitle = "25 cards",
                leading = {
                    FlashcardsIconTile(
                        icon = Icons.Default.Star,
                        contentDescription = null,
                    )
                },
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsSelectableListRowPreview() {
    FlashcardsTheme {
        Surface {
            Column(
                modifier = Modifier
                    .padding(MaterialTheme.spacing.normal)
                    .flashcardsListGroupContainer()
            ) {
                FlashcardsSelectableListRow(
                    modifier = Modifier.flashcardsListItemShape(FlashcardsListItemPosition.Top),
                    title = "Compose",
                    selected = true,
                    onSelectedChange = {},
                    subtitle = "80 cards",
                    leading = {
                        FlashcardsIconTile(
                            icon = Icons.Default.Star,
                            contentDescription = null,
                        )
                    },
                )
                FlashcardsSelectableListRow(
                    modifier = Modifier.flashcardsListItemShape(FlashcardsListItemPosition.Bottom),
                    title = "Testing",
                    selected = false,
                    onSelectedChange = {},
                    subtitle = "25 cards",
                    leading = {
                        FlashcardsIconTile(
                            icon = Icons.Default.Star,
                            contentDescription = null,
                        )
                    },
                )
            }
        }
    }
}
