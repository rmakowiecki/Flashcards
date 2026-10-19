package com.rossomak.flashcards.feature.home

import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.ProgressSummary
import com.rossomak.flashcards.core.domain.model.RecentItem
import com.rossomak.flashcards.core.domain.model.SessionSourceType
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.ui.R as CoreUiR
import com.rossomak.flashcards.core.ui.composables.FlashcardsEmptyState
import com.rossomak.flashcards.core.ui.composables.FlashcardsEmptyStateTone
import com.rossomak.flashcards.core.ui.composables.FlashcardsLoadStateContent
import com.rossomak.flashcards.core.ui.composables.FlashcardsRevealContent
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledButton
import com.rossomak.flashcards.core.ui.composables.flashcardsScrollFade
import com.rossomak.flashcards.core.ui.composables.level.FlashcardsLevelCard
import com.rossomak.flashcards.core.ui.navigation.observeAsEvents
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
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Ticks only while Home is STARTED and refreshes at once on every start. Held as State, not read with `by`, so
    // only the Recent rows that read it recompose per tick.
    val nowState = produceState(initialValue = Instant.now(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = Instant.now()
                delay(1.minutes)
            }
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
        now = { nowState.value },
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
 * @param now provider of what Recents' start times are worded against, read only where a Recent's start time is
 * worded so a tick recomposes just those rows.
 */
@Suppress("LongParameterList") // one callback per hoisted ViewModel action; a holder class would only rename the sprawl.
@Composable
fun HomeContent(
    modifier: Modifier = Modifier,
    state: HomeScreenState,
    now: () -> Instant,
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
        val scrollState = rememberScrollState()
        LevelCardAboveBody(
            viewportHeight = maxHeight,
            modifier = Modifier
                .fillMaxSize()
                .flashcardsScrollFade(scrollState)
                .verticalScroll(scrollState),
            card = {
                // The gutter and the space above belong to the card; the space below is the next section's overline
                // label's own top padding.
                FlashcardsLevelCard(
                    state = state.levelCard,
                    modifier = Modifier.padding(
                        start = MaterialTheme.spacing.normal,
                        top = MaterialTheme.spacing.normal,
                        end = MaterialTheme.spacing.normal,
                    ),
                )
            },
            body = {
                // Keyed by kind: a section changing inside Sections updates in place (see HomeSections).
                FlashcardsLoadStateContent(targetState = state.body, contentKey = { body -> body::class }) { body ->
                    when (body) {
                        Resolving -> loading()
                        FirstSession -> content {
                            FlashcardsEmptyState(
                                modifier = Modifier.align(Alignment.Center),
                                icon = Icons.AutoMirrored.Filled.MenuBook,
                                title = stringResource(R.string.home_empty_title),
                                supportingText = stringResource(R.string.home_empty_message),
                                button = {
                                    FlashcardsFilledButton(text = stringResource(R.string.home_empty_start_button), onClick = onBrowseClick)
                                },
                            )
                        }

                        LoadError -> content {
                            FlashcardsEmptyState(
                                modifier = Modifier.align(Alignment.Center),
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

                        is Sections -> content {
                            HomeSections(
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
            },
        )
    }
}

/**
 * Stacks [card] above [body] and gives the body at least the part of [viewportHeight] the card
 * leaves, with no upper bound: a centered body state or the loading indicator sits in that space,
 * while a taller body grows and the screen scrolls. `verticalScroll` hands its content an unbounded
 * max height, and a `Column` weight can't express this: it caps its child at the leftover space.
 */
@Composable
private fun LevelCardAboveBody(
    viewportHeight: Dp,
    modifier: Modifier = Modifier,
    card: @Composable () -> Unit,
    body: @Composable () -> Unit,
) {
    Layout(contents = listOf(card, body), modifier = modifier) { (cardMeasurables, bodyMeasurables), constraints ->
        val width = constraints.maxWidth
        val viewportHeightPx = viewportHeight.roundToPx()
        val cardPlaceable = cardMeasurables.first().measure(Constraints(maxWidth = width))
        val bodyPlaceable = bodyMeasurables.first().measure(
            Constraints(maxWidth = width, minHeight = (viewportHeightPx - cardPlaceable.height).coerceAtLeast(0)),
        )
        val height = maxOf(viewportHeightPx, cardPlaceable.height + bodyPlaceable.height)
        layout(width, height) {
            cardPlaceable.placeRelative(0, 0)
            bodyPlaceable.placeRelative(0, cardPlaceable.height)
        }
    }
}

@Suppress("LongParameterList") // one callback per hoisted ViewModel action; a holder class would only rename the sprawl.
@Composable
private fun HomeSections(
    sections: Sections,
    progressSummary: ProgressSummary?,
    isProgressResolved: Boolean,
    now: () -> Instant,
    zoneId: ZoneId,
    onCategoryClick: (Category) -> Unit,
    onCategoryQuickSessionClick: (Category) -> Unit,
    onSubcategoryClick: (Subcategory) -> Unit,
    onSubcategoryPlayClick: (Subcategory) -> Unit,
    onRecentClick: (RecentItem) -> Unit,
    onFavoritesHintHide: () -> Unit,
) {
    // Each area keyed by kind, so a section arriving after the reveal ceiling reveals by itself while
    // new items inside an area update in place.
    Column {
        FlashcardsRevealContent(targetState = sections.favorites, contentKey = { favorites -> favorites::class }) { favorites ->
            when (favorites) {
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
        }
        FlashcardsRevealContent(targetState = sections.recents, contentKey = { recents -> recents::class }) { recents ->
            when (recents) {
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
}
