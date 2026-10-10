package com.rossomak.flashcards.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.ProgressSummary
import com.rossomak.flashcards.core.domain.model.RecentItem
import com.rossomak.flashcards.core.domain.model.SessionSourceType
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.ui.R as CoreUiR
import com.rossomak.flashcards.core.ui.composables.FlashcardsEmptyState
import com.rossomak.flashcards.core.ui.composables.FlashcardsEmptyStateTone
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledButton
import com.rossomak.flashcards.core.ui.composables.flashcardsScrollFade
import com.rossomak.flashcards.core.ui.navigation.observeAsEvents
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
import com.rossomak.flashcards.feature.home.HomeFavoritesArea.Carousel
import com.rossomak.flashcards.feature.home.HomeFavoritesArea.Hint
import com.rossomak.flashcards.feature.home.HomeFavoritesArea.Omitted as FavoritesOmitted
import com.rossomak.flashcards.feature.home.HomeRecentsArea.Omitted as RecentsOmitted
import com.rossomak.flashcards.feature.home.HomeRecentsArea.Placeholder
import com.rossomak.flashcards.feature.home.HomeRecentsArea.Rows
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
        onFavoritesHintHide = viewModel::onFavoritesHintHide,
        onBrowseClick = onNavigateToBrowse,
        onRetry = viewModel::onRetry,
    )
}

/**
 * Renders the Level card, then [HomeScreenState.body], in one scrolling column, so what shows below the card is
 * decided in one place.
 *
 * @param now what Recents' start times are worded against; [HomeScreen] ticks it once a minute.
 */
@Suppress("LongParameterList") // one callback per hoisted ViewModel action; a holder class would only rename the sprawl.
@Composable
internal fun HomeContent(
    modifier: Modifier = Modifier,
    state: HomeScreenState,
    now: Instant,
    zoneId: ZoneId = ZoneId.systemDefault(),
    onCategoryClick: (Category) -> Unit,
    onCategoryQuickSessionClick: (Category) -> Unit,
    onSubcategoryClick: (Subcategory) -> Unit,
    onSubcategoryPlayClick: (Subcategory) -> Unit,
    onRecentClick: (RecentItem) -> Unit,
    onFavoritesHintHide: () -> Unit,
    onBrowseClick: () -> Unit,
    onRetry: () -> Unit,
) {
    // No top app bar: MainScreen leaves each tab root to pad for the status bar itself.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        val density = LocalDensity.current
        val scrollState = rememberScrollState()
        var levelCardHeightPx by remember { mutableIntStateOf(0) }
        // verticalScroll gives its content an unbounded height, so a weight(1f) area would collapse instead of
        // filling what the card leaves of the viewport. The area takes that space as a minimum height instead,
        // and grows (and the screen scrolls) when its content is taller.
        val belowCardMinHeight = (maxHeight - with(density) { levelCardHeightPx.toDp() }).coerceAtLeast(0.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .flashcardsScrollFade(scrollState)
                .verticalScroll(scrollState),
        ) {
            HomeLevelCard(
                state = state.levelCard,
                modifier = Modifier.onSizeChanged { levelCardHeightPx = it.height },
            )
            when (val body = state.body) {
                Resolving -> Unit

                FirstSession -> CenteredBelowCard(minHeight = belowCardMinHeight) {
                    FlashcardsEmptyState(
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        title = stringResource(R.string.home_empty_title),
                        supportingText = stringResource(R.string.home_empty_message),
                        button = {
                            FlashcardsFilledButton(text = stringResource(R.string.home_empty_start_button), onClick = onBrowseClick)
                        },
                    )
                }

                LoadError -> CenteredBelowCard(minHeight = belowCardMinHeight) {
                    FlashcardsEmptyState(
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
                }

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
                    onFavoritesHintHide = onFavoritesHintHide,
                )
            }
        }
    }
}

/** Centers [content] in the viewport space the Level card leaves; see [HomeContent] for why it is a minimum height. */
@Composable
private fun CenteredBelowCard(minHeight: Dp, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight),
        contentAlignment = Alignment.Center,
    ) {
        content()
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
    onFavoritesHintHide: () -> Unit,
) {
    Column {
        when (val favorites = sections.favorites) {
            is Carousel -> FavoritesCarousel(
                items = favorites.items,
                progressSummary = progressSummary,
                isProgressResolved = isProgressResolved,
                onCategoryClick = onCategoryClick,
                onCategoryQuickSessionClick = onCategoryQuickSessionClick,
                onSubcategoryClick = onSubcategoryClick,
                onSubcategoryPlayClick = onSubcategoryPlayClick,
            )

            Hint -> FavoritesHint(onHide = onFavoritesHintHide)

            FavoritesOmitted -> Unit
        }
        when (val recents = sections.recents) {
            is Rows -> RecentSessionsSection(
                items = recents.items,
                now = now,
                zoneId = zoneId,
                onRecentClick = onRecentClick,
                modifier = Modifier.padding(bottom = MaterialTheme.spacing.normal),
            )

            Placeholder -> RecentsPlaceholder(modifier = Modifier.padding(bottom = MaterialTheme.spacing.normal))

            RecentsOmitted -> Unit
        }
    }
}
