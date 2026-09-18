// Grouping file: FlashcardsListGroupItem is the closed set of row kinds a group can render;
// FlashcardsListGroup and flashcardsListGroupItems are the bounded and lazy containers for it.
@file:Suppress("MatchingDeclarationName")

package com.rossomak.flashcards.core.ui.composables.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SwipeUp
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.composables.FlashcardsIconTile
import com.rossomak.flashcards.core.ui.composables.FlashcardsOverlineLabel
import com.rossomak.flashcards.core.ui.composables.FlashcardsStepper
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsIconButton
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.spacing
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The closed set of row kinds [FlashcardsListGroup] and [flashcardsListGroupItems] can render —
 * one variant per dedicated row composable ([FlashcardsListRow], [FlashcardsDetailedListRow],
 * [FlashcardsSelectableListRow], [FlashcardsExpandableListRow]). There is no free-form
 * `content: @Composable () -> Unit` slot: a group can only ever render these row kinds, so
 * [flashcardsListItemShape] stays owned entirely by the container that dispatches on this sealed
 * type — a row composable never shapes itself.
 */
sealed interface FlashcardsListGroupItem {

    /**
     * A stable identity for this row (e.g. a domain model's `id`), used as the composition/lazy
     * item key. Rows with per-row remembered state — [Selectable]'s selection tint animation,
     * [ExpandableRow]'s chevron rotation and expand/collapse transition — must set this when the
     * list can reorder, filter, or resize while visible; otherwise Compose reuses composition
     * slots by position and that internal animation state can end up attached to the wrong row.
     * Defaults to `null`, in which case the container falls back to the row's index — fine for
     * lists that never reorder/filter in place.
     */
    val key: Any?

    /**
     * Renders as [FlashcardsListRow]: a 1- or 2-line row (title, optional [secondaryText] or
     * [secondaryContent]). For a row that also needs a wrapping description line, use [DetailedRow]
     * instead — `Row` has no `subtitle` field, so the two shapes can't be conflated at a call site.
     */
    data class Row(
        val title: String,
        val onClick: () -> Unit,
        val onLongClick: (() -> Unit)? = null,
        val secondaryText: String? = null,
        val secondaryContent: (@Composable () -> Unit)? = null,
        val enabled: Boolean = true,
        val isFavorited: Boolean = false,
        val leading: (@Composable () -> Unit)? = null,
        val trailing: (@Composable () -> Unit)? = null,
        override val key: Any? = null,
    ) : FlashcardsListGroupItem

    /**
     * Renders as [FlashcardsDetailedListRow]: a 3-line row — title, a wrapping [subtitle]
     * description, and a [secondaryText] label/count below it. Both are mandatory; a row that
     * only needs one extra line is a [Row], not a `DetailedRow`.
     */
    data class DetailedRow(
        val title: String,
        val subtitle: String,
        val secondaryText: String,
        val onClick: () -> Unit,
        val enabled: Boolean = true,
        val isFavorited: Boolean = false,
        val leading: (@Composable () -> Unit)? = null,
        val trailing: (@Composable () -> Unit)? = null,
        override val key: Any? = null,
    ) : FlashcardsListGroupItem

    /** Renders as [FlashcardsSelectableListRow]. */
    data class Selectable(
        val title: String,
        val selected: Boolean,
        val onSelectedChange: (Boolean) -> Unit,
        val subtitle: String? = null,
        val subtitleContent: (@Composable () -> Unit)? = null,
        val enabled: Boolean = true,
        val isFavorited: Boolean = false,
        val leading: (@Composable () -> Unit)? = null,
        override val key: Any? = null,
    ) : FlashcardsListGroupItem

    /** Renders as [FlashcardsExpandableListRow]. */
    data class ExpandableRow(
        val difficulty: Int,
        val title: String,
        val expanded: Boolean,
        val onExpandedChange: (Boolean) -> Unit,
        val expandedStateDescription: String,
        val collapsedStateDescription: String,
        val tags: ImmutableList<String> = persistentListOf(),
        val expandedContent: (@Composable () -> Unit)? = null,
        override val key: Any? = null,
    ) : FlashcardsListGroupItem
}

/**
 * Dispatches [item] to its row composable with the already-shaped [modifier], shared by
 * [FlashcardsListGroup] and [flashcardsListGroupItems] so bounded and lazy containers render
 * the exact same set of row kinds.
 */
@Composable
private fun FlashcardsListGroupRow(item: FlashcardsListGroupItem, modifier: Modifier) {
    when (item) {
        is FlashcardsListGroupItem.Row -> FlashcardsListRow(
            modifier = modifier,
            title = item.title,
            onClick = item.onClick,
            onLongClick = item.onLongClick,
            secondaryText = item.secondaryText,
            secondaryContent = item.secondaryContent,
            enabled = item.enabled,
            isFavorited = item.isFavorited,
            leading = item.leading,
            trailing = item.trailing,
        )
        is FlashcardsListGroupItem.DetailedRow -> FlashcardsDetailedListRow(
            modifier = modifier,
            title = item.title,
            subtitle = item.subtitle,
            secondaryText = item.secondaryText,
            onClick = item.onClick,
            enabled = item.enabled,
            isFavorited = item.isFavorited,
            leading = item.leading,
            trailing = item.trailing,
        )
        is FlashcardsListGroupItem.Selectable -> FlashcardsSelectableListRow(
            modifier = modifier,
            title = item.title,
            selected = item.selected,
            onSelectedChange = item.onSelectedChange,
            subtitle = item.subtitle,
            subtitleContent = item.subtitleContent,
            enabled = item.enabled,
            isFavorited = item.isFavorited,
            leading = item.leading,
        )
        is FlashcardsListGroupItem.ExpandableRow -> FlashcardsExpandableListRow(
            modifier = modifier,
            difficulty = item.difficulty,
            title = item.title,
            expanded = item.expanded,
            onExpandedChange = item.onExpandedChange,
            expandedStateDescription = item.expandedStateDescription,
            collapsedStateDescription = item.collapsedStateDescription,
            tags = item.tags,
            expandedContent = item.expandedContent,
        )
    }
}

/**
 * A bounded (non-lazy) list container: one rounded card wrapping a `Column` of [items], each
 * shaped by [flashcardsListItemShape] against the [flashcardsListGroupContainer] background so
 * interior seams read as a 1dp gap rather than a drawn divider. For short, fixed sets — settings
 * sections, category lists, selection groups (roughly under ~15 rows, so composing every row
 * up front costs nothing). Once a list can grow past what fits comfortably on screen — e.g. a
 * subcategory list with dozens of rows — use [flashcardsListGroupItems] inside a `LazyColumn`
 * instead, so only visible rows get composed.
 */
@Composable
fun FlashcardsListGroup(
    items: List<FlashcardsListGroupItem>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.flashcardsListGroupContainer()) {
        items.forEachIndexed { index, item ->
            key(item.key ?: index) {
                val position = FlashcardsListItemPosition.of(index, items.size)
                FlashcardsListGroupRow(
                    item = item,
                    modifier = Modifier
                        .fillMaxWidth()
                        .flashcardsListItemShape(position),
                )
            }
        }
    }
}

/**
 * The lazy counterpart of [FlashcardsListGroup]: adds [items] to a `LazyColumn` (wrapped in
 * [flashcardsListGroupContainer] by the caller) with the same per-row [flashcardsListItemShape]
 * positioning, so a list too long to bind with [FlashcardsListGroup] still renders as one
 * rounded card while only composing rows near the viewport. Keys each item by
 * [FlashcardsListGroupItem.key] (falling back to its index) and types it by its sealed variant,
 * so Compose never reuses a row's composition slot — and any per-row remembered/animated state —
 * for a different item or a different row kind.
 */
fun LazyListScope.flashcardsListGroupItems(items: List<FlashcardsListGroupItem>) {
    itemsIndexed(
        items,
        key = { index, item -> item.key ?: index },
        contentType = { _, item -> item::class },
    ) { index, item ->
        val position = FlashcardsListItemPosition.of(index, items.size)
        FlashcardsListGroupRow(
            item = item,
            modifier = Modifier
                .fillMaxWidth()
                .flashcardsListItemShape(position),
        )
    }
}

/**
 * [SwitchDefaults.colors] using the app's secondary (purple) role for the "on" track, matching
 * the design's settings-switch. `secondary`/`onSecondary` is the same solid+white M3 pairing the
 * checkbox in [FlashcardsSelectableListRow] uses, and stays theme-adaptive for free.
 */
@Composable
private fun accentSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
    checkedTrackColor = MaterialTheme.colorScheme.secondary,
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
    uncheckedBorderColor = Color.Transparent,
)

@ShowkaseComposable(name = "List group — permissions", group = "Lists")
@Composable
fun FlashcardsListGroupShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsListGroup(
                items = listOf(
                    FlashcardsListGroupItem.Row(
                        title = "Notifications",
                        onClick = {},
                        secondaryText = "Allowed",
                        leading = {
                            FlashcardsIconTile(
                                icon = Icons.Default.NotificationsActive,
                                contentDescription = null,
                            )
                        },
                        trailing = { FlashcardsChevron() },
                    ),
                    FlashcardsListGroupItem.Row(
                        title = "Microphone",
                        onClick = {},
                        secondaryText = "Not granted",
                        leading = {
                            FlashcardsIconTile(
                                icon = Icons.Default.Mic,
                                contentDescription = null,
                            )
                        },
                        trailing = { FlashcardsChevron() },
                    ),
                ),
            )
        }
    }
}

@ShowkaseComposable(name = "List group — study sessions", group = "Lists")
@Composable
fun FlashcardsListGroupStudySessionsShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsListGroup(
                items = listOf(
                    FlashcardsListGroupItem.Row(
                        title = "Session size",
                        onClick = {},
                        secondaryText = "Cards per session",
                        leading = {
                            FlashcardsIconTile(
                                icon = Icons.Default.Tag,
                                contentDescription = null,
                            )
                        },
                        trailing = {
                            FlashcardsStepper(
                                value = 20,
                                onDecrement = {},
                                onIncrement = {},
                                decrementContentDescription = "Fewer cards",
                                incrementContentDescription = "More cards",
                            )
                        },
                    ),
                    FlashcardsListGroupItem.Row(
                        title = "Auto-flip on swipe",
                        onClick = {},
                        secondaryText = "Reveal answer with a single swipe",
                        leading = {
                            FlashcardsIconTile(
                                icon = Icons.Default.SwipeUp,
                                contentDescription = null,
                            )
                        },
                        trailing = { Switch(checked = false, onCheckedChange = null, colors = accentSwitchColors()) },
                    ),
                    FlashcardsListGroupItem.Row(
                        title = "Shuffle order",
                        onClick = {},
                        secondaryText = "Always randomise card order",
                        leading = {
                            FlashcardsIconTile(
                                icon = Icons.Default.Shuffle,
                                contentDescription = null,
                            )
                        },
                        trailing = { Switch(checked = true, onCheckedChange = null, colors = accentSwitchColors()) },
                    ),
                ),
            )
        }
    }
}

/**
 * Sample per-category colors, deliberately hardcoded rather than fetched — this Showkase catalog
 * entry stays on [FlashcardsIconTile] (not [com.rossomak.flashcards.core.ui.composables.FlashcardsVectorIconTile])
 * so the debug-only design-system browser never has to author sample category SVG glyphs. Each category defines
 * its own color independently — these are NOT sourced from `colorScheme` roles, even where a
 * value happens to coincide with one (Android's matches this app's brand purple, but that's a
 * coincidence of this particular category, not a theme reference). Real values live on
 * `Category.color`.
 */
private val categoryColorAndroid = Color(0xFF6B2FA0)
private val categoryColorPython = Color(0xFF0277BD)
private val categoryColorIos = Color(0xFF00838F)
private const val CATEGORY_TILE_ALPHA = 0.12f

@ShowkaseComposable(name = "List group — categories", group = "Lists")
@Composable
fun FlashcardsListGroupCategoriesShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsListGroup(
                items = listOf(
                    FlashcardsListGroupItem.DetailedRow(
                        title = "Android",
                        onClick = {},
                        subtitle = "Compose · Coroutines · Compose Navigation",
                        secondaryText = "13 topics",
                        leading = {
                            FlashcardsIconTile(
                                icon = Icons.Default.Android,
                                contentDescription = null,
                                containerColor = categoryColorAndroid.copy(alpha = CATEGORY_TILE_ALPHA),
                                contentColor = categoryColorAndroid,
                            )
                        },
                        trailing = { FlashcardsChevron() },
                    ),
                    FlashcardsListGroupItem.DetailedRow(
                        title = "Python",
                        onClick = {},
                        subtitle = "Async · Typing · Decorators",
                        secondaryText = "6 topics",
                        leading = {
                            FlashcardsIconTile(
                                icon = Icons.Default.Terminal,
                                contentDescription = null,
                                containerColor = categoryColorPython.copy(alpha = CATEGORY_TILE_ALPHA),
                                contentColor = categoryColorPython,
                            )
                        },
                        trailing = { FlashcardsChevron() },
                    ),
                    FlashcardsListGroupItem.DetailedRow(
                        title = "iOS",
                        onClick = {},
                        subtitle = "SwiftUI · Combine · UIKit",
                        secondaryText = "5 topics",
                        leading = {
                            FlashcardsIconTile(
                                icon = Icons.Default.PhoneIphone,
                                contentDescription = null,
                                containerColor = categoryColorIos.copy(alpha = CATEGORY_TILE_ALPHA),
                                contentColor = categoryColorIos,
                            )
                        },
                        trailing = { FlashcardsChevron() },
                    ),
                ),
            )
        }
    }
}

@ShowkaseComposable(name = "List group — search results", group = "Lists")
@Composable
fun FlashcardsListGroupSearchResultsShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsListGroup(
                items = listOf(
                    FlashcardsListGroupItem.Row(
                        title = "Compose",
                        onClick = {},
                        secondaryText = "in Android",
                        trailing = {
                            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall)) {
                                FlashcardsIconButton(
                                    icon = Icons.Default.PlayArrow,
                                    contentDescription = "Study Compose",
                                    onClick = {},
                                    size = FlashcardsComponentSize.Small,
                                )
                                FlashcardsChevron()
                            }
                        },
                    ),
                    FlashcardsListGroupItem.Row(
                        title = "Compose Navigation",
                        onClick = {},
                        secondaryText = "in Android",
                        trailing = {
                            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall)) {
                                FlashcardsIconButton(
                                    icon = Icons.Default.PlayArrow,
                                    contentDescription = "Study Compose Navigation",
                                    onClick = {},
                                    size = FlashcardsComponentSize.Small,
                                )
                                FlashcardsChevron()
                            }
                        },
                    ),
                ),
            )
        }
    }
}

@ShowkaseComposable(name = "List group — multi-select topics", group = "Lists")
@Composable
fun FlashcardsListGroupMultiSelectShowcase() {
    FlashcardsTheme {
        Surface {
            Column {
                FlashcardsOverlineLabel(
                    text = "3 of 13 topics selected",
                )
                FlashcardsListGroup(
                    items = listOf(
                        FlashcardsListGroupItem.Selectable(
                            title = "Compose",
                            selected = true,
                            onSelectedChange = {},
                            subtitle = "80 cards",
                            leading = {
                                FlashcardsIconTile(icon = Icons.Default.Android, contentDescription = null)
                            },
                        ),
                        FlashcardsListGroupItem.Selectable(
                            title = "Coroutines",
                            selected = true,
                            onSelectedChange = {},
                            subtitle = "40 cards",
                            leading = {
                                FlashcardsIconTile(icon = Icons.Default.Android, contentDescription = null)
                            },
                        ),
                        FlashcardsListGroupItem.Selectable(
                            title = "Compose Navigation",
                            selected = false,
                            onSelectedChange = {},
                            subtitle = "30 cards",
                            leading = {
                                FlashcardsIconTile(icon = Icons.Default.Android, contentDescription = null)
                            },
                        ),
                        FlashcardsListGroupItem.Selectable(
                            title = "Testing",
                            selected = false,
                            onSelectedChange = {},
                            subtitle = "25 cards",
                            leading = {
                                FlashcardsIconTile(icon = Icons.Default.Android, contentDescription = null)
                            },
                        ),
                        FlashcardsListGroupItem.Selectable(
                            title = "Architecture",
                            selected = true,
                            onSelectedChange = {},
                            subtitle = "20 cards",
                            leading = {
                                FlashcardsIconTile(icon = Icons.Default.Android, contentDescription = null)
                            },
                        ),
                    ),
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsListGroupPreview() {
    FlashcardsTheme {
        Surface {
            FlashcardsListGroup(
                items = listOf(
                    FlashcardsListGroupItem.Row(
                        title = "Notifications",
                        onClick = {},
                        secondaryText = "Allowed",
                        leading = {
                            FlashcardsIconTile(
                                icon = Icons.Default.NotificationsActive,
                                contentDescription = null,
                            )
                        },
                        trailing = { FlashcardsChevron() },
                    ),
                    FlashcardsListGroupItem.Row(
                        title = "Microphone",
                        onClick = {},
                        secondaryText = "Not granted",
                        leading = {
                            FlashcardsIconTile(
                                icon = Icons.Default.Mic,
                                contentDescription = null,
                            )
                        },
                        trailing = { FlashcardsChevron() },
                    ),
                ),
            )
        }
    }
}
