package com.rossomak.flashcards.feature.home

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import com.rossomak.flashcards.core.ui.composables.FlashcardsOverlineLabel
import com.rossomak.flashcards.core.ui.composables.banners.FlashcardsInfoBanner
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsTextButton
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.spacing

/**
 * Takes the Favorites carousel's place when the User has Recents but no Favorites: the FAVORITES label with a
 * Hide button on its line, then a banner explaining the bookmark. The row is local to Home on purpose; the
 * shared overline label gets no trailing slot.
 */
@Composable
internal fun FavoritesHint(onHide: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Weighted, so the label keeps its own spacing; its end padding sits inside the weight.
            FlashcardsOverlineLabel(
                text = stringResource(R.string.favorites_section_title),
                modifier = Modifier
                    .weight(1f)
                    .alignByBaseline(),
            )
            // Accessibility is traded off here on purpose: without the 48 dp minimum the button stays below the
            // touch target, but the row keeps the label's spacing. The visible "Hide" text is its only label.
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                FlashcardsTextButton(
                    text = stringResource(R.string.favorites_hint_hide_button),
                    onClick = onHide,
                    size = FlashcardsComponentSize.Small,
                    modifier = Modifier
                        .alignByBaseline()
                        .padding(end = MaterialTheme.spacing.normal),
                )
            }
        }
        FlashcardsInfoBanner(
            text = stringResource(R.string.favorites_hint_message),
            icon = Icons.Filled.BookmarkBorder,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.normal),
        )
    }
}

@PreviewLightDark
@Composable
private fun FavoritesHintPreview() {
    FlashcardsTheme {
        Surface {
            FavoritesHint(onHide = {})
        }
    }
}

@Preview(name = "Large font", fontScale = 2f)
@Preview(name = "Large font dark", fontScale = 2f, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun FavoritesHintLargeFontPreview() {
    FlashcardsTheme {
        Surface {
            FavoritesHint(onHide = {})
        }
    }
}

@Preview(name = "RTL", locale = "ar")
@Preview(name = "RTL dark", locale = "ar", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun FavoritesHintRtlPreview() {
    FlashcardsTheme {
        Surface {
            FavoritesHint(onHide = {})
        }
    }
}
