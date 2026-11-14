package com.rossomak.flashcards.core.ui.composables

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.rossomak.flashcards.core.designsystem.theme.spacing

/**
 * The scroll edges every scrolling screen shares: a top and bottom [flashcardsScrollFade] and a
 * `spacing.normal` gap after the last element, inside the scroll. See ADR-0078.
 *
 * Apply it in place of a bare `verticalScroll`. [padsTop] adds the same gap above the first
 * element; leave it off when that element already carries it, as a [FlashcardsOverlineLabel] does.
 * [backgroundColor] is what the content sits on, painted as the fade.
 */
@Composable
fun Modifier.flashcardsScreenScroll(
    scrollState: ScrollState,
    padsTop: Boolean = false,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
): Modifier = this
    .flashcardsScrollFade(scrollState, backgroundColor = backgroundColor)
    .verticalScroll(scrollState)
    .padding(flashcardsScreenListContentPadding(padsTop))

/**
 * [flashcardsScreenScroll]'s edges for a screen's `LazyColumn`: pass as its `contentPadding`, next to
 * [flashcardsListScrollFade]. Only the vertical gaps: the side gutters stay on the list's modifier.
 * [padsTop] adds the top gap, as on [flashcardsScreenScroll]; leave it off for a list under a pinned header.
 */
@Composable
fun flashcardsScreenListContentPadding(padsTop: Boolean = false): PaddingValues = PaddingValues(
    top = if (padsTop) MaterialTheme.spacing.normal else MaterialTheme.spacing.none,
    bottom = MaterialTheme.spacing.normal,
)
