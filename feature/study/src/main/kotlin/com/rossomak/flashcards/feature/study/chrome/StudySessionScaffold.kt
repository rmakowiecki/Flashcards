package com.rossomak.flashcards.feature.study.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import com.rossomak.flashcards.core.ui.backdrop.BrandGradientBackdrop
import com.rossomak.flashcards.core.ui.backdrop.blurBackdropScope
import com.rossomak.flashcards.core.ui.composables.FlashcardsBottomSheet
import com.rossomak.flashcards.core.ui.composables.rememberFlashcardsBottomSheetState
import com.rossomak.flashcards.feature.study.StudyMorpheusGraphs

/**
 * The Rated and Fast session layout: header, card body and a docked non-dismissible
 * [FlashcardsBottomSheet] (ADR-0043). The sheet is an unaligned sibling of [Scaffold], so it owns the
 * screen bottom and its nav-bar inset; its measured height pads the body and the snackbar.
 */
@Composable
internal fun StudySessionScaffold(
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState,
    topBar: @Composable () -> Unit,
    sheetContent: @Composable ColumnScope.() -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    var sheetHeightPx by remember { mutableIntStateOf(0) }
    val sheetHeight = with(LocalDensity.current) { sheetHeightPx.toDp() }
    val layoutDirection = LocalLayoutDirection.current
    Box(modifier = modifier.fillMaxSize().blurBackdropScope()) {
        BrandGradientBackdrop(graph = StudyMorpheusGraphs.session)
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = sheetHeight)) },
            topBar = topBar,
        ) { innerPadding ->
            content(
                PaddingValues(
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    top = innerPadding.calculateTopPadding(),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                    bottom = sheetHeight,
                ),
            )
        }
        FlashcardsBottomSheet(
            state = rememberFlashcardsBottomSheetState(dismissible = false),
            onDismissRequest = {},
            modifier = Modifier.onSizeChanged { size -> sheetHeightPx = size.height },
            content = sheetContent,
        )
    }
}
