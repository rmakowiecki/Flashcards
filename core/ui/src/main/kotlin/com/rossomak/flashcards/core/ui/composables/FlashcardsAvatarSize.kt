package com.rossomak.flashcards.core.ui.composables

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Size tier of a [FlashcardsAvatar]: each entry fixes the circle's diameter. The initials, the
 * person icon and the photo all scale linearly with it.
 */
enum class FlashcardsAvatarSize(val diameter: Dp) {
    /** Settings' top app bar. */
    Small(32.dp),

    /** Level card. */
    Medium(56.dp),

    /** Account header: as tall as the three text lines beside it, so the large top app bar stays 152dp. */
    Large(72.dp),
}
