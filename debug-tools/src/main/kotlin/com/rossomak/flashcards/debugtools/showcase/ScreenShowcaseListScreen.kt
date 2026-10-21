package com.rossomak.flashcards.debugtools.showcase

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.core.ui.backdrop.BrandMorpheusGraphSpecs
import com.rossomak.flashcards.core.ui.composables.FlashcardsOverlineLabel
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsTopAppBar
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsTopAppBarGraph
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsTopAppBarSize
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsChevron
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsListGroup
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsListGroupItem
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.debugtools.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenShowcaseListScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit,
    onNavigateToEntry: (ScreenShowcaseEntry) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            FlashcardsTopAppBar(
                title = stringResource(R.string.screen_showcase_title),
                size = FlashcardsTopAppBarSize.Medium,
                scrollBehavior = scrollBehavior,
                onNavigateBack = onNavigateBack,
                backdrop = { FlashcardsTopAppBarGraph(spec = BrandMorpheusGraphSpecs.topAppBarOnSurface, fill = BrandMorpheusGraphSpecs.onSurfaceFill) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = MaterialTheme.spacing.normal),
        ) {
            ScreenShowcaseGroup.entries.forEach { group ->
                FlashcardsOverlineLabel(text = stringResource(group.titleRes))
                FlashcardsListGroup(
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.normal),
                    items = ScreenShowcaseEntry.entries.filter { it.group == group }.map { entry ->
                        FlashcardsListGroupItem.Row(
                            title = stringResource(entry.titleRes),
                            onClick = { onNavigateToEntry(entry) },
                            trailing = { FlashcardsChevron() },
                        )
                    },
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun ScreenShowcaseListScreenPreview() {
    FlashcardsTheme {
        ScreenShowcaseListScreen(onNavigateBack = {}, onNavigateToEntry = {})
    }
}
