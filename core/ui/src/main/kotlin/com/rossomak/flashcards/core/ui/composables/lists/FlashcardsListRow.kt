package com.rossomak.flashcards.core.ui.composables.lists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.composables.FlashcardsIconTile
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.spacing

/** Opacity applied to the whole row when `enabled = false`, per the design's disabled rows. */
private const val DISABLED_ALPHA = 0.6f

/**
 * The generic design-system list row: an optional [leading] slot, a title with optional
 * [secondaryText] label, and an optional [trailing] slot — one or two lines. Settings, category,
 * and subcategory rows are all this row composed with different slots (icon tile / play button
 * leading; chevron / switch / stepper trailing). A row that also needs a wrapping description
 * line is [FlashcardsDetailedListRow] instead — this row has no `subtitle` slot, so the two
 * shapes can't be conflated at a call site.
 *
 * The whole row is one merged, clickable node with the given [role] — unless [onClick] is `null`,
 * in which case the row carries no click semantics at all (a caller with its own separately
 * clickable trailing content, e.g. [FlashcardsSettingRow]'s Edit button, wants the row itself to
 * be inert). Decorative trailing chevrons should pass `contentDescription = null` (see
 * [FlashcardsChevron]). When [enabled] is `false`, the whole row dims rather than only disabling
 * the click.
 *
 * [onLongClick] is opt-in: leaving it `null` keeps the row on the cheaper [Modifier.clickable]
 * path with its existing click semantics, unchanged. Passing it switches the row to
 * [Modifier.combinedClickable], which already fires a [HapticFeedbackType.LongPress] haptic on
 * long-press itself — this row doesn't call it a second time. Pairing [onLongClick] with a `null`
 * [onClick] is nonsensical and unsupported; every caller that wants long-press also has a real tap
 * target.
 *
 * [secondaryContent] overrides [secondaryText] when given, for a caller that needs more than a
 * single-styled line — multi-color spans, a marquee, placeholder dashes while data resolves — the
 * row still reserves the same line, it just stops drawing it itself.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FlashcardsListRow(
    title: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    secondaryText: String? = null,
    secondaryContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    isFavorited: Boolean = false,
    role: Role = Role.Button,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val clickModifier = when {
        onClick == null -> Modifier
        onLongClick != null -> Modifier.combinedClickable(
            enabled = enabled,
            role = role,
            onLongClick = onLongClick,
            onClick = onClick,
        )
        else -> Modifier.clickable(enabled = enabled, role = role, onClick = onClick)
    }
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .heightIn(min = MaterialTheme.sizes.listRowMinHeight)
            .then(clickModifier)
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
            if (secondaryContent != null) {
                secondaryContent()
            } else if (secondaryText != null) {
                Text(
                    text = secondaryText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            // Unweighted and sized to its own content: every call site's trailing is a chevron,
            // switch, stepper, or icon button — always small and fixed-size, never long text (a
            // settings row's current *value* lives in secondaryText, not trailing). Giving trailing
            // a weight(1f) here — matching the title column's own weight(1f) — split the row 50/50
            // by allocation regardless of content, forcing the title into exactly half the row even
            // when short and leaving trailing stranded at that midpoint instead of flush against the
            // row's right edge. Title's weight(1f) below already claims all space trailing doesn't
            // use, which is what actually keeps trailing pinned to the end.
            trailing()
        }
    }
}

/**
 * Decorative drill-in chevron for the [FlashcardsListRow] trailing slot. Its content
 * description is intentionally `null`: the row itself is the labeled, clickable node.
 */
@Composable
fun FlashcardsChevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@ShowkaseComposable(name = "List row — nav", group = "Lists")
@Composable
fun FlashcardsListRowShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsListRow(
                title = "Compose",
                onClick = {},
                secondaryText = "in Android",
                trailing = { FlashcardsChevron() },
            )
        }
    }
}

@ShowkaseComposable(name = "List row — disabled", group = "Lists")
@Composable
fun FlashcardsListRowDisabledShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsListRow(
                title = "Speak your answer aloud",
                onClick = {},
                secondaryText = "Coming soon",
                enabled = false,
                trailing = { FlashcardsChevron() },
            )
        }
    }
}

@ShowkaseComposable(name = "List row — long-press", group = "Lists")
@Composable
fun FlashcardsListRowLongPressShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsListRow(
                title = "Compose",
                onClick = {},
                onLongClick = {},
                secondaryText = "Long-press to select",
                trailing = { FlashcardsChevron() },
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsListRowLongPressPreview() {
    FlashcardsTheme {
        Surface {
            FlashcardsListRow(
                title = "Compose",
                onClick = {},
                onLongClick = {},
                secondaryText = "Long-press to select",
                trailing = { FlashcardsChevron() },
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsListRowPreview() {
    FlashcardsTheme {
        Surface {
            Column(
                modifier = Modifier
                    .padding(MaterialTheme.spacing.normal)
                    .flashcardsListGroupContainer()
            ) {
                FlashcardsListRow(
                    modifier = Modifier.flashcardsListItemShape(FlashcardsListItemPosition.Top),
                    title = "Notifications",
                    onClick = {},
                    secondaryText = "Allowed",
                    trailing = { FlashcardsChevron() },
                )
                FlashcardsListRow(
                    modifier = Modifier.flashcardsListItemShape(FlashcardsListItemPosition.Middle),
                    title = "Compose Navigation",
                    onClick = {},
                    secondaryText = "in Android",
                    trailing = { FlashcardsChevron() },
                )
                FlashcardsListRow(
                    modifier = Modifier.flashcardsListItemShape(FlashcardsListItemPosition.Bottom),
                    title = "Kotlin Coroutines",
                    onClick = {},
                    secondaryText = "24 cards",
                    leading = {
                        FlashcardsIconTile(
                            icon = Icons.Default.Star,
                            contentDescription = null,
                        )
                    },
                    trailing = { FlashcardsChevron() },
                )
            }
        }
    }
}
