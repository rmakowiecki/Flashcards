package com.rossomak.flashcards.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.rossomak.flashcards.core.ui.composables.flashcardsScrollFade
import com.rossomak.flashcards.core.ui.navigation.observeAsEvents
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.home.HomeDestination.CategoryDetails
import com.rossomak.flashcards.feature.home.HomeDestination.QuickSessionPreviewStudySession
import com.rossomak.flashcards.feature.home.HomeDestination.RecentPreviewStudySession
import com.rossomak.flashcards.feature.home.HomeDestination.SubcategoryDetails
import com.rossomak.flashcards.feature.home.HomeDestination.SubcategoryPreviewStudySession
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Content as FavoritesContent
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Hidden as FavoritesHidden
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Loading as FavoritesLoading
import com.rossomak.flashcards.feature.home.HomeRecentsState.Content as RecentsContent
import com.rossomak.flashcards.feature.home.HomeRecentsState.Hidden as RecentsHidden
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
    )
}

/** @param now what Recents' start times are worded against; [HomeScreen] ticks it once a minute. */
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
) {
    val scrollState = rememberScrollState()
    // No top app bar: MainScreen leaves each tab root to pad for the status bar itself.
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .flashcardsScrollFade(scrollState)
            .verticalScroll(scrollState),
    ) {
        val favorites = state.favorites
        val recents = state.recents
        if (state.showsEmptyState()) {
            HomeEmptyState(
                modifier = Modifier.padding(vertical = MaterialTheme.spacing.large),
                onStartClick = onBrowseClick,
            )
        } else {
            if (favorites is FavoritesContent) {
                FavoritesCarousel(
                    items = favorites.items,
                    progressSummary = state.progressSummary,
                    isProgressResolved = state.isProgressResolved,
                    onCategoryClick = onCategoryClick,
                    onCategoryQuickSessionClick = onCategoryQuickSessionClick,
                    onSubcategoryClick = onSubcategoryClick,
                    onSubcategoryPlayClick = onSubcategoryPlayClick,
                )
            }
            if (recents is RecentsContent) {
                RecentSessionsSection(
                    items = recents.items,
                    now = now,
                    zoneId = zoneId,
                    onRecentClick = onRecentClick,
                    modifier = Modifier.padding(bottom = MaterialTheme.spacing.normal),
                )
            }
        }
    }
}

/** Only when both sections are Hidden, so the empty state never flashes while one is still Loading. */
private fun HomeScreenState.showsEmptyState(): Boolean = favorites is FavoritesHidden && recents is RecentsHidden

@Composable
private fun HomeEmptyState(modifier: Modifier = Modifier, onStartClick: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyStateIllustration()

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            ),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.home_empty_message),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onStartClick,
            shape = RoundedCornerShape(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(20.dp),
            )
            Text(
                text = stringResource(R.string.home_empty_start_button),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
            )
        }
    }
}

@Composable
private fun EmptyStateIllustration() {
    Box(
        modifier = Modifier
            .size(160.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(80.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 90.dp, height = 72.dp)
                .offset(x = (-18).dp, y = 10.dp)
                .rotate(-15f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Box(
            modifier = Modifier
                .size(64.dp)
                .offset(x = 12.dp, y = (-6).dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(36.dp),
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
        )
    }
}

@PreviewLightDark
@Composable
private fun HomeContentLoadingPreview() {
    HomeContentPreviewHost(state = HomeScreenState(favorites = FavoritesLoading, recents = RecentsLoading))
}

@PreviewLightDark
@Composable
private fun HomeContentEmptyPreview() {
    HomeContentPreviewHost(state = HomeScreenState(favorites = FavoritesHidden, recents = RecentsHidden))
}

@PreviewLightDark
@Composable
private fun HomeContentRecentsOnlyPreview() {
    HomeContentPreviewHost(state = HomeScreenState(favorites = FavoritesHidden, recents = RecentsContent(previewRecentItems)))
}

@PreviewLightDark
@Composable
private fun HomeContentFavoritesOnlyPreview() {
    HomeContentPreviewHost(
        state = HomeScreenState(
            favorites = previewFavorites,
            recents = RecentsHidden,
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
