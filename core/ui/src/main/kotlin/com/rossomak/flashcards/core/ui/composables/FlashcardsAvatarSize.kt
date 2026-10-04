package com.rossomak.flashcards.core.ui.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.rossomak.flashcards.core.ui.composables.FlashcardsAvatarSize.Large
import com.rossomak.flashcards.core.ui.composables.FlashcardsAvatarSize.Medium
import com.rossomak.flashcards.core.ui.composables.FlashcardsAvatarSize.Small
import com.rossomak.flashcards.core.ui.theme.sizes

/**
 * Size tier of a [FlashcardsAvatar]: each entry fixes the circle's diameter and the typography of
 * its initials fallback.
 */
enum class FlashcardsAvatarSize {
    /** Settings' top app bar. */
    Small,

    /** The level card. */
    Medium,

    /** The Account header. */
    Large,
    ;

    val diameter: Dp
        @Composable get() = with(MaterialTheme.sizes) {
            when (this@FlashcardsAvatarSize) {
                Small -> avatarSmall
                Medium -> avatarMedium
                Large -> avatarLarge
            }
        }

    val initialsStyle: TextStyle
        @Composable get() = with(MaterialTheme.typography) {
            when (this@FlashcardsAvatarSize) {
                Small -> labelLarge
                Medium -> titleLarge
                Large -> headlineMedium
            }
        }
}
