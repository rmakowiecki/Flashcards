package com.rossomak.flashcards.core.ui.composables.lists

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.composables.FlashcardsDifficultyBadge
import com.rossomak.flashcards.core.ui.composables.FlashcardsMetadataBadge
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize
import com.rossomak.flashcards.core.ui.composables.withInlineCode
import com.rossomak.flashcards.core.ui.theme.FlashcardsMotion
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.spacing
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * An expandable flashcard row: a leading [FlashcardsDifficultyBadge], the question [title], flat
 * study [tags], and a chevron that toggles an in-place reveal of [expandedContent] (typically the
 * answer).
 *
 * The component owns the expand animation — the chevron rotates and the reveal fades/expands
 * here — so screens only hoist [expanded] + [onExpandedChange] and never reimplement it. The
 * header announces its expanded/collapsed state to accessibility services.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlashcardsExpandableListRow(
    difficulty: Int,
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    expandedStateDescription: String,
    collapsedStateDescription: String,
    modifier: Modifier = Modifier,
    tags: ImmutableList<String> = persistentListOf(),
    expandedContent: @Composable (() -> Unit)? = null,
) {
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLowest
    // Read only in the graphics layer below, so animation frames rotate the chevron without recomposing the row.
    val chevronRotation = animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(FlashcardsMotion.DURATION_MEDIUM_MS, easing = FlashcardsMotion.EmphasizedEasing),
        label = "chevronRotation",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .semantics {
                stateDescription = if (expanded) expandedStateDescription else collapsedStateDescription
            }
            .clickable { onExpandedChange(!expanded) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = MaterialTheme.spacing.normal,
                    vertical = MaterialTheme.spacing.small,
                ),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            FlashcardsDifficultyBadge(level = difficulty)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
            ) {
                Text(
                    text = title.withInlineCode(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxsmall),
                    ) {
                        tags.forEach { tag ->
                            FlashcardsMetadataBadge(label = tag, size = FlashcardsComponentSize.Small)
                        }
                    }
                }
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { rotationZ = chevronRotation.value },
            )
        }
        AnimatedVisibility(visible = expanded) {
            if (expandedContent != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = MaterialTheme.spacing.normal,
                            vertical = MaterialTheme.spacing.small,
                        ),
                ) {
                    expandedContent()
                }
            }
        }
    }
}

private val previewTags = persistentListOf("Views", "State")

@ShowkaseComposable(name = "Expandable list row — expanded", group = "Lists")
@PreviewLightDark
@Composable
fun FlashcardsExpandableListRowExpandedShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsExpandableListRow(
                difficulty = 2,
                title = "What does `remember` do differently from `rememberSaveable`?",
                expanded = true,
                onExpandedChange = {},
                expandedStateDescription = "Expanded",
                collapsedStateDescription = "Collapsed",
                tags = persistentListOf("State"),
                expandedContent = {
                    Text(
                        text = ("`remember` retains a value across recompositions only, while " +
                            "`rememberSaveable` also survives configuration changes and process death.")
                            .withInlineCode(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
            )
        }
    }
}

@ShowkaseComposable(name = "Expandable list row — collapsed", group = "Lists")
@Composable
fun FlashcardsExpandableListRowCollapsedShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsExpandableListRow(
                difficulty = 5,
                title = "What is recomposition in Jetpack Compose?",
                expanded = false,
                onExpandedChange = {},
                expandedStateDescription = "Expanded",
                collapsedStateDescription = "Collapsed",
                tags = previewTags,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsExpandableListRowPreview() {
    FlashcardsTheme {
        Surface {
            FlashcardsExpandableListRow(
                difficulty = 5,
                title = "What is recomposition in Jetpack Compose?",
                expanded = false,
                onExpandedChange = {},
                expandedStateDescription = "Expanded",
                collapsedStateDescription = "Collapsed",
                tags = previewTags,
            )
        }
    }
}
