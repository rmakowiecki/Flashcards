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
 * A screen's `verticalScroll` with edge fades and a `spacing.normal` gap after the last element.
 * [padsTop] adds the gap above the first element too, unless it carries one, like a [FlashcardsOverlineLabel].
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

/** [flashcardsScreenScroll]'s vertical gaps as a screen `LazyColumn`'s `contentPadding`. */
@Composable
fun flashcardsScreenListContentPadding(padsTop: Boolean = false): PaddingValues = PaddingValues(
    top = if (padsTop) MaterialTheme.spacing.normal else MaterialTheme.spacing.none,
    bottom = MaterialTheme.spacing.normal,
)
