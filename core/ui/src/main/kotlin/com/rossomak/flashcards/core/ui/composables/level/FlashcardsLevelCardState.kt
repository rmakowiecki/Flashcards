package com.rossomak.flashcards.core.ui.composables.level

/**
 * What a [FlashcardsLevelCard] knows about the figures it draws. One type for every screen that shows
 * the card, so each of them holds the same three states. The parameters are primitives on purpose: the
 * card does not know the domain level model.
 */
sealed interface FlashcardsLevelCardState {

    /** The figures have not arrived yet. The card shows a shimmering skeleton. */
    data object Loading : FlashcardsLevelCardState

    /**
     * The figures to draw. A null [photoUrl] or [displayName] is a User without them, not a missing value.
     * [xpForNextLevel] is the total that completes [level]; the card derives the bar's fill from it and
     * [xpIntoCurrentLevel].
     */
    data class Content(
        val level: Int,
        val xpIntoCurrentLevel: Long,
        val xpForNextLevel: Long,
        val photoUrl: String?,
        val displayName: String?,
    ) : FlashcardsLevelCardState

    /**
     * The figures could not be loaded. A distinct state so a failure UI can be added later; for now the card
     * shows the [Loading] skeleton's blocks without the shimmer.
     */
    data object Unavailable : FlashcardsLevelCardState
}
