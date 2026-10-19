@file:Suppress("MagicNumber")

package com.rossomak.flashcards.debugtools.showcase.fixtures

import com.rossomak.flashcards.core.ui.composables.level.FlashcardsLevelCardState

/** The made-up User; no photo, so the avatar shows initials. */
internal object ShowcaseUser {

    const val DISPLAY_NAME = "Ross"
    const val LEVEL = 14
    const val XP_INTO_LEVEL = 1_500L
    const val XP_FOR_NEXT_LEVEL = 7_500L
    const val CURRENT_STREAK = 6

    val levelCard = FlashcardsLevelCardState.Content(
        level = LEVEL,
        xpIntoCurrentLevel = XP_INTO_LEVEL,
        xpForNextLevel = XP_FOR_NEXT_LEVEL,
        photoUrl = null,
        displayName = DISPLAY_NAME,
    )
}
