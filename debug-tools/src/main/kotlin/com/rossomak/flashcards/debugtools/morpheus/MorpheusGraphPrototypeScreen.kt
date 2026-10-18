package com.rossomak.flashcards.debugtools.morpheus

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.rossomak.flashcards.core.common.logd
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.debugtools.R
import com.rossomak.morpheus.tuner.MorpheusGraphPlayground

/**
 * PROTOTYPE, throwaway — answers "which animated Morpheus graph background looks best, and on which
 * surfaces". Delete the whole `morpheus` package and the two routing hooks in
 * [com.rossomak.flashcards.debugtools.DebugScreen] / `MainScreenDebugTabs` once a winner is
 * promoted.
 *
 * The lib's [MorpheusGraphPlayground] in Flashcards' brand: [FlashcardsMorpheusGraphPresets] for its pages
 * and both onboarding decks, one [flashcardsPlaygroundStyle] per [FlashcardsComponentStyle], and a
 * copied spec also printed to logcat. Registered on the app's outer graph, so it runs full-screen
 * without the bottom bar.
 */
@Composable
fun MorpheusGraphPrototypeScreen(modifier: Modifier = Modifier, onNavigateBack: () -> Unit) {
    MorpheusGraphPlayground(
        modifier = modifier,
        presets = FlashcardsMorpheusGraphPresets.all,
        morphFamily = FlashcardsMorpheusGraphPresets.morphFamily,
        decks = listOf(FlashcardsMorpheusGraphPresets.onboardingPages, FlashcardsMorpheusGraphPresets.onboardingCometPages),
        styles = FlashcardsComponentStyle.entries.map { style -> flashcardsPlaygroundStyle(style) },
        deckStyle = flashcardsPlaygroundStyle(FlashcardsComponentStyle.OnGradient),
        onSpecExport = { source -> logd { "Morpheus graph spec:\n$source" } },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.debug_voice_harness_back_cd))
            }
        },
    )
}

@PreviewLightDark
@Composable
private fun MorpheusGraphPrototypeScreenPreview() {
    FlashcardsTheme {
        Surface {
            MorpheusGraphPrototypeScreen(onNavigateBack = {})
        }
    }
}
