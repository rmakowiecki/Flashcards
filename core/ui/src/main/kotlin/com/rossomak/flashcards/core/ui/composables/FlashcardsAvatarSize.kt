package com.rossomak.flashcards.core.ui.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.rossomak.flashcards.core.ui.composables.FlashcardsAvatarSize.Large
import com.rossomak.flashcards.core.ui.composables.FlashcardsAvatarSize.Medium
import com.rossomak.flashcards.core.ui.composables.FlashcardsAvatarSize.Small
import com.rossomak.flashcards.core.ui.theme.sizes

/**
 * Size tier of a [FlashcardsAvatar]: each entry fixes the circle's diameter. The initials, the
 * person icon and the photo all scale linearly with it.
 */
enum class FlashcardsAvatarSize {
    Small,
    Medium,
    Large;

    val diameter: Dp
        @Composable get() = with(MaterialTheme.sizes) {
            when (this@FlashcardsAvatarSize) {
                Small -> avatarSmall
                Medium -> avatarMedium
                Large -> avatarLarge
            }
        }
}
