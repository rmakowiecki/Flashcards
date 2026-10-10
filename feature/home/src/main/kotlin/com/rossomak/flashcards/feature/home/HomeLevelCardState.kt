package com.rossomak.flashcards.feature.home

import com.rossomak.flashcards.core.domain.model.LevelProgress

/**
 * What Home knows about the Level card. Independent of [HomeScreenState.body]: the card never holds the
 * sections back and the sections never hold it back.
 */
sealed interface HomeLevelCardState {

    /** Neither the Level stream nor the auth user has resolved. No ceiling applies, so this can last. */
    data object Loading : HomeLevelCardState

    /** Both resolved. A null [photoUrl] or [displayName] is a User without them, not a missing value. */
    data class Content(
        val levelProgress: LevelProgress,
        val photoUrl: String?,
        val displayName: String?,
    ) : HomeLevelCardState

    /** The Level stream failed or ended before its first emission: the card is omitted until Retry. */
    data object Unavailable : HomeLevelCardState
}
