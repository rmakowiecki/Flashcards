package com.rossomak.flashcards.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.FavoriteItem.FavoriteCategory
import com.rossomak.flashcards.core.domain.model.FavoriteItem.FavoriteSubcategory
import com.rossomak.flashcards.core.domain.model.ProgressSummary
import com.rossomak.flashcards.core.domain.model.RecentItem
import com.rossomak.flashcards.core.domain.model.SessionSourceType
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.model.SubcategoryProgressSummary
import com.rossomak.flashcards.core.ui.R as CoreUiR
import com.rossomak.flashcards.core.ui.composables.FlashcardsEmptyState
import com.rossomak.flashcards.core.ui.composables.FlashcardsEmptyStateTone
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledButton
import com.rossomak.flashcards.core.ui.composables.flashcardsScrollFade
import com.rossomak.flashcards.core.ui.navigation.observeAsEvents
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.home.HomeBody.FirstSession
import com.rossomak.flashcards.feature.home.HomeBody.LoadError
import com.rossomak.flashcards.feature.home.HomeBody.Resolving
import com.rossomak.flashcards.feature.home.HomeBody.Sections
import com.rossomak.flashcards.feature.home.HomeDestination.CategoryDetails
import com.rossomak.flashcards.feature.home.HomeDestination.QuickSessionPreviewStudySession
import com.rossomak.flashcards.feature.home.HomeDestination.RecentPreviewStudySession
import com.rossomak.flashcards.feature.home.HomeDestination.SubcategoryDetails
import com.rossomak.flashcards.feature.home.HomeDestination.SubcategoryPreviewStudySession
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Content as FavoritesContent
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Empty as FavoritesEmpty
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Failed as FavoritesFailed
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Loading as FavoritesLoading
import com.rossomak.flashcards.feature.home.HomeRecentsState.Content as RecentsContent
import com.rossomak.flashcards.feature.home.HomeRecentsState.Empty as RecentsEmpty
import com.rossomak.flashcards.feature.home.HomeRecentsState.Failed as RecentsFailed
import com.rossomak.flashcards.feature.home.HomeRecentsState.Loading as RecentsLoading
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToCategoryDetails: (categoryId: String, categoryName: String) -> Unit,
    onNavigateToSubcategoryDetails: (
        categoryId: String,
        categoryName: String,
        subcategoryId: String,
        subcategoryName: String,
    ) -> Unit,
    onNavigateToPreviewStudySession: (
        categoryId: String,
        categoryName: String,
        subcategoryId: String,
        subcategoryName: String,
    ) -> Unit,
    onNavigateToPreviewQuickSession: (categoryId: String, categoryName: String) -> Unit,
    onNavigateToPreviewRecentSession: (
        categoryId: String,
        categoryName: String,
        sourceType: SessionSourceType,
        subcategoryIds: List<String>,
        subcategoryNames: List<String>,
        studyMode: StudyMode,
        voiceAnsweringEnabled: Boolean?,
        readAloudEnabled: Boolean?,
    ) -> Unit,
    onNavigateToBrowse: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now by produceState(initialValue = Instant.now()) {
        while (true) {
            delay(1.minutes)
            value = Instant.now()
        }
    }

    observeAsEvents(viewModel.events) { destination ->
        with(destination) {
            when (this) {
                is CategoryDetails -> onNavigateToCategoryDetails(
                    categoryId,
                    categoryName,
                )

                is SubcategoryDetails -> onNavigateToSubcategoryDetails(
                    categoryId,
                    categoryName,
                    subcategoryId,
                    subcategoryName,
                )

                is SubcategoryPreviewStudySession -> onNavigateToPreviewStudySession(
                    categoryId,
                    categoryName,
                    subcategoryId,
                    subcategoryName,
                )

                is QuickSessionPreviewStudySession -> onNavigateToPreviewQuickSession(
                    categoryId,
                    categoryName,
                )

                is RecentPreviewStudySession -> onNavigateToPreviewRecentSession(
                    categoryId,
                    categoryName,
                    sourceType,
                    subcategoryIds,
                    subcategoryNames,
                    studyMode,
                    voiceAnsweringEnabled,
                    readAloudEnabled,
                )
            }
        }
    }

    HomeContent(
        modifier = modifier,
        state = state,
        now = now,
        onCategoryClick = viewModel::onFavoriteCategorySelect,
        onCategoryQuickSessionClick = viewModel::onFavoriteCategoryQuickSessionStart,
        onSubcategoryClick = viewModel::onFavoriteSubcategorySelect,
        onSubcategoryPlayClick = viewModel::onFavoriteSubcategorySessionStart,
        onRecentClick = viewModel::onRecentSelect,
        onBrowseClick = onNavigateToBrowse,
        onRetry = viewModel::onRetry,
    )
}

/**
 * Renders [HomeScreenState.body] and nothing else, so what shows is decided in one place.
 *
 * @param now what Recents' start times are worded against; [HomeScreen] ticks it once a minute.
 */
@Suppress("LongParameterList") // one callback per hoisted ViewModel action; a holder class would only rename the sprawl.
@Composable
private fun HomeContent(
    modifier: Modifier = Modifier,
    state: HomeScreenState,
    now: Instant,
    zoneId: ZoneId = ZoneId.systemDefault(),
    onCategoryClick: (Category) -> Unit,
    onCategoryQuickSessionClick: (Category) -> Unit,
    onSubcategoryClick: (Subcategory) -> Unit,
    onSubcategoryPlayClick: (Subcategory) -> Unit,
    onRecentClick: (RecentItem) -> Unit,
    onBrowseClick: () -> Unit,
    onRetry: () -> Unit,
) {
    // No top app bar: MainScreen leaves each tab root to pad for the status bar itself.
    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        when (val body = state.body) {
            Resolving -> Unit

            FirstSession -> FlashcardsEmptyState(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                title = stringResource(R.string.home_empty_title),
                supportingText = stringResource(R.string.home_empty_message),
                button = {
                    FlashcardsFilledButton(text = stringResource(R.string.home_empty_start_button), onClick = onBrowseClick)
                },
            )

            LoadError -> FlashcardsEmptyState(
                icon = Icons.Filled.CloudOff,
                title = stringResource(R.string.home_error_title),
                supportingText = stringResource(R.string.home_error_message),
                tone = FlashcardsEmptyStateTone.Error,
                button = {
                    FlashcardsFilledButton(
                        text = stringResource(CoreUiR.string.common_retry_button),
                        onClick = onRetry,
                        icon = Icons.Filled.Refresh,
                    )
                },
            )

            is Sections -> HomeSections(
                sections = body,
                progressSummary = state.progressSummary,
                isProgressResolved = state.isProgressResolved,
                now = now,
                zoneId = zoneId,
                onCategoryClick = onCategoryClick,
                onCategoryQuickSessionClick = onCategoryQuickSessionClick,
                onSubcategoryClick = onSubcategoryClick,
                onSubcategoryPlayClick = onSubcategoryPlayClick,
                onRecentClick = onRecentClick,
            )
        }
    }
}

@Suppress("LongParameterList") // one callback per hoisted ViewModel action; a holder class would only rename the sprawl.
@Composable
private fun HomeSections(
    sections: Sections,
    progressSummary: ProgressSummary?,
    isProgressResolved: Boolean,
    now: Instant,
    zoneId: ZoneId,
    onCategoryClick: (Category) -> Unit,
    onCategoryQuickSessionClick: (Category) -> Unit,
    onSubcategoryClick: (Subcategory) -> Unit,
    onSubcategoryPlayClick: (Subcategory) -> Unit,
    onRecentClick: (RecentItem) -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .flashcardsScrollFade(scrollState)
            .verticalScroll(scrollState),
    ) {
        if (sections.favoriteItems.isNotEmpty()) {
            FavoritesCarousel(
                items = sections.favoriteItems,
                progressSummary = progressSummary,
                isProgressResolved = isProgressResolved,
                onCategoryClick = onCategoryClick,
                onCategoryQuickSessionClick = onCategoryQuickSessionClick,
                onSubcategoryClick = onSubcategoryClick,
                onSubcategoryPlayClick = onSubcategoryPlayClick,
            )
        }
        if (sections.recentItems.isNotEmpty()) {
            RecentSessionsSection(
                items = sections.recentItems,
                now = now,
                zoneId = zoneId,
                onRecentClick = onRecentClick,
                modifier = Modifier.padding(bottom = MaterialTheme.spacing.normal),
            )
        }
    }
}

private val previewCategory = Category(
    id = "android",
    name = "Android",
    order = 0,
    subcategoryCount = 14,
    iconSvg = null,
    color = "#2B6AA5",
    featuredSubcategoryNames = emptyList(),
)
private val previewSubcategory = Subcategory(
    id = "compose",
    name = "Compose",
    categoryId = previewCategory.id,
    categoryName = previewCategory.name,
    order = 0,
    cardCount = 30,
)

private val previewFavorites = FavoritesContent(
    listOf(
        FavoriteSubcategory(previewSubcategory, previewCategory, Instant.parse("2026-05-05T10:00:00Z")),
        FavoriteCategory(previewCategory, Instant.parse("2026-05-05T10:00:00Z")),
    ),
)
private val previewProgressSummary = ProgressSummary(
    subcategories = mapOf(previewSubcategory.id to SubcategoryProgressSummary(masteredCount = 10, studiedCount = 25)),
)

@Composable
private fun HomeContentPreviewHost(state: HomeScreenState) {
    FlashcardsTheme {
        HomeContent(
            state = state,
            now = previewRecentsNow,
            zoneId = previewRecentsZoneId,
            onCategoryClick = {},
            onCategoryQuickSessionClick = {},
            onSubcategoryClick = {},
            onSubcategoryPlayClick = {},
            onRecentClick = {},
            onBrowseClick = {},
            onRetry = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun HomeContentResolvingPreview() {
    HomeContentPreviewHost(state = HomeScreenState(favorites = FavoritesLoading, recents = RecentsLoading))
}

@PreviewLightDark
@Composable
private fun HomeContentFirstSessionPreview() {
    HomeContentPreviewHost(state = HomeScreenState(favorites = FavoritesEmpty, recents = RecentsEmpty))
}

@PreviewLightDark
@Composable
private fun HomeContentErrorPreview() {
    HomeContentPreviewHost(state = HomeScreenState(favorites = FavoritesFailed, recents = RecentsFailed))
}

@PreviewLightDark
@Composable
private fun HomeContentRecentsOnlyPreview() {
    HomeContentPreviewHost(state = HomeScreenState(favorites = FavoritesFailed, recents = RecentsContent(previewRecentItems)))
}

@PreviewLightDark
@Composable
private fun HomeContentFavoritesOnlyPreview() {
    HomeContentPreviewHost(
        state = HomeScreenState(
            favorites = previewFavorites,
            recents = RecentsFailed,
            progressSummary = previewProgressSummary,
            isProgressResolved = true,
        ),
    )
}

@PreviewLightDark
@Composable
private fun HomeContentFavoritesAndRecentsPreview() {
    HomeContentPreviewHost(
        state = HomeScreenState(
            favorites = previewFavorites,
            recents = RecentsContent(previewRecentItems),
            progressSummary = previewProgressSummary,
            isProgressResolved = true,
        ),
    )
}
