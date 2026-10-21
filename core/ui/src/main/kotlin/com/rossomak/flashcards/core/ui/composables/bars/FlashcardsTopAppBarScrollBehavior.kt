package com.rossomak.flashcards.core.ui.composables.bars

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable

/** The scroll behavior every [FlashcardsCollapsingTopAppBar] screen uses: the bar collapses until pinned. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberFlashcardsTopAppBarScrollBehavior(): TopAppBarScrollBehavior =
    TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

/** For a static preview, [collapsed] starts the offset past the end of travel, which the bar pulls back to exactly collapsed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberPreviewTopAppBarScrollBehavior(collapsed: Boolean = false): TopAppBarScrollBehavior =
    TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState(initialHeightOffset = if (collapsed) -Float.MAX_VALUE else 0f))
