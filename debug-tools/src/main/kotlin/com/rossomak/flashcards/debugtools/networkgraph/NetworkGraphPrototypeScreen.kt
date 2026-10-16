package com.rossomak.flashcards.debugtools.networkgraph

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.rossomak.flashcards.core.common.logd
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.networkgraph.tuner.NetworkGraphPlayground

/**
 * PROTOTYPE, throwaway — answers "which animated network-graph background looks best, and on which
 * surfaces". Delete the whole `networkgraph` package and the two routing hooks in
 * [com.rossomak.flashcards.debugtools.DebugScreen] / `MainScreenDebugTabs` once a winner is
 * promoted.
 *
 * The lib's [NetworkGraphPlayground] in Flashcards' brand: [FlashcardsNetworkGraphPresets] for its pages
 * and both onboarding decks, one [flashcardsPlaygroundStyle] per [FlashcardsComponentStyle], and a
 * copied spec also printed to logcat. Registered on the app's outer graph, so it runs full-screen
 * without the bottom bar.
 */
@Composable
fun NetworkGraphPrototypeScreen(modifier: Modifier = Modifier, onNavigateBack: () -> Unit) {
    NetworkGraphPlayground(
        modifier = modifier,
        presets = FlashcardsNetworkGraphPresets.all,
        morphFamily = FlashcardsNetworkGraphPresets.morphFamily,
        decks = listOf(FlashcardsNetworkGraphPresets.onboardingPages, FlashcardsNetworkGraphPresets.onboardingCometPages),
        styles = FlashcardsComponentStyle.entries.map { style -> flashcardsPlaygroundStyle(style) },
        deckStyle = flashcardsPlaygroundStyle(FlashcardsComponentStyle.OnGradient),
        onSpecExport = { source -> logd { "Network graph spec:\n$source" } },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
        },
    )
}

@PreviewLightDark
@Composable
private fun NetworkGraphPrototypeScreenPreview() {
    FlashcardsTheme {
        Surface {
            NetworkGraphPrototypeScreen(onNavigateBack = {})
        }
    }
}
