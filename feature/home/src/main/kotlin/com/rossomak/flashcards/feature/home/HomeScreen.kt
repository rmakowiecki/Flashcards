package com.rossomak.flashcards.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.model.SubcategoryProgressSummary
import com.rossomak.flashcards.core.ui.R
import com.rossomak.flashcards.core.ui.composables.flashcardsScrollFade
import com.rossomak.flashcards.core.ui.navigation.observeAsEvents
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.brandColors
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.home.HomeDestination.CategoryDetails
import com.rossomak.flashcards.feature.home.HomeDestination.QuickSessionPreviewStudySession
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
        onRecentClick = {},
    )
}

/** @param now what Recents' start times are worded against; [HomeScreen] ticks it once a minute. */
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
) {
    val scrollState = rememberScrollState()
    // Bottom edge only: the top bar scrolls with this column, so a top fade would paint a strip of
    // background over its gradient before it has scrolled away.
    Column(
        modifier = modifier
            .fillMaxSize()
            .flashcardsScrollFade(scrollState, fadeTop = false)
            .verticalScroll(scrollState),
    ) {
        HomeTopBar()
        UserGreetingSection(userName = "Ross")
        val favorites = state.favorites
        val recents = state.recents
        if (state.showsEmptyState()) {
            HomeEmptyState(modifier = Modifier.padding(vertical = MaterialTheme.spacing.large))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar() {
    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.brandColors.topBarGradient)) {
        CenterAlignedTopAppBar(
            title = {
                Icon(
                    painter = painterResource(R.drawable.flashcards_white),
                    contentDescription = "Flashcards",
                    tint = Color.Unspecified,
                    modifier = Modifier.height(64.dp),
                )
            },
            actions = {
                BadgedBox(
                    badge = {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.tertiary, CircleShape),
                        )
                    },
                ) {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = "Notifications",
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )
    }
}

@Composable
private fun UserGreetingSection(userName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = userName.first().uppercase(),
                style = MaterialTheme.typography.titleLarge.copy(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
        Column {
            Text(
                text = "Good morning,",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            Text(
                text = "$userName 👋",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

@Composable
private fun HomeEmptyState(modifier: Modifier = Modifier) {
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
            text = "Ready to start learning?",
            style = MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            ),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Pick a category and run your first study session — your recents and favorites will appear here.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {},
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
                text = "Start your first session",
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
