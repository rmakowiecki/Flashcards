package com.rossomak.flashcards.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.rossomak.flashcards.core.ui.composables.FlashcardsOverlineLabel
import com.rossomak.flashcards.core.ui.composables.banners.FlashcardsInfoBanner
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.spacing

/**
 * Takes the Recents list's place when the User has Favorites but no Recents. Renders its own RECENTLY STUDIED
 * label, since the list's label belongs to the list.
 */
@Composable
internal fun RecentsPlaceholder(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        FlashcardsOverlineLabel(text = stringResource(R.string.recents_section_title))
        FlashcardsInfoBanner(
            text = stringResource(R.string.recents_placeholder_message),
            icon = Icons.Filled.History,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.normal),
        )
    }
}

@PreviewLightDark
@Composable
private fun RecentsPlaceholderPreview() {
    FlashcardsTheme {
        Surface {
            RecentsPlaceholder()
        }
    }
}
