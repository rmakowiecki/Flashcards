package com.rossomak.flashcards.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.rossomak.flashcards.core.ui.composables.level.FlashcardsLevelCard
import com.rossomak.flashcards.core.ui.composables.level.FlashcardsLevelCardSkeleton
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.home.HomeLevelCardState.Content
import com.rossomak.flashcards.feature.home.HomeLevelCardState.Loading
import com.rossomak.flashcards.feature.home.HomeLevelCardState.Unavailable

/**
 * Renders the Level card slot: the skeleton while [state] is [Loading], the card for [Content] and nothing for
 * [Unavailable]. The skeleton and the card have the same height, so the fade between them moves nothing. The
 * gutter and the space above belong to the card, so an omitted card leaves no gap. The space below is the next
 * section's overline label's own top padding.
 *
 * The shared card is animation-agnostic, so the animation rules live here. The bar animates changes to its
 * progress but not its first composition, so Home does not replay a sweep from 0 on every launch. The card is
 * keyed on the Level so a Level change starts the bar at its new fill instead of sweeping backwards; the same
 * snap softens the short false level-up a double-counted delivery can cause.
 */
@Composable
internal fun HomeLevelCard(state: HomeLevelCardState, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = state,
        modifier = modifier,
        // Content to Content (a progress or name update) is not a transition: it recomposes in place.
        contentKey = { it::class },
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "HomeLevelCard",
    ) { shownState ->
        val cardModifier = Modifier.padding(
            start = MaterialTheme.spacing.normal,
            top = MaterialTheme.spacing.normal,
            end = MaterialTheme.spacing.normal,
        )
        when (shownState) {
            Loading -> FlashcardsLevelCardSkeleton(modifier = cardModifier)

            is Content -> key(shownState.levelProgress.level) {
                with(shownState.levelProgress) {
                    FlashcardsLevelCard(
                        level = level,
                        xpIntoCurrentLevel = xpIntoCurrentLevel,
                        xpForNextLevel = xpForNextLevel,
                        progress = progress,
                        photoUrl = shownState.photoUrl,
                        displayName = shownState.displayName,
                        modifier = cardModifier,
                    )
                }
            }

            Unavailable -> Unit
        }
    }
}
