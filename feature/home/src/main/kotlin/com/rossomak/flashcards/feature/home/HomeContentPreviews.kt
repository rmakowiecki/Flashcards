package com.rossomak.flashcards.feature.home

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.FavoriteItem.FavoriteCategory
import com.rossomak.flashcards.core.domain.model.FavoriteItem.FavoriteSubcategory
import com.rossomak.flashcards.core.domain.model.LevelProgress
import com.rossomak.flashcards.core.domain.model.ProgressSummary
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.model.SubcategoryProgressSummary
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Content as FavoritesContent
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Empty as FavoritesEmpty
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Failed as FavoritesFailed
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Loading as FavoritesLoading
import com.rossomak.flashcards.feature.home.HomeLevelCardState.Content as LevelCardContent
import com.rossomak.flashcards.feature.home.HomeLevelCardState.Loading as LevelCardLoading
import com.rossomak.flashcards.feature.home.HomeLevelCardState.Unavailable as LevelCardUnavailable
import com.rossomak.flashcards.feature.home.HomeRecentsState.Content as RecentsContent
import com.rossomak.flashcards.feature.home.HomeRecentsState.Empty as RecentsEmpty
import com.rossomak.flashcards.feature.home.HomeRecentsState.Failed as RecentsFailed
import com.rossomak.flashcards.feature.home.HomeRecentsState.Loading as RecentsLoading
import java.time.Instant

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
private val previewLevelCard = LevelCardContent(
    levelProgress = LevelProgress(level = 7, xpIntoCurrentLevel = 2_300L, xpForNextLevel = 6_000L),
    photoUrl = null,
    displayName = "Jane Doe",
)
private val previewProgressSummary = ProgressSummary(
    subcategories = mapOf(previewSubcategory.id to SubcategoryProgressSummary(masteredCount = 10, studiedCount = 25)),
)

private val previewFavoritesAndRecentsState = HomeScreenState(
    favorites = previewFavorites,
    recents = RecentsContent(previewRecentItems),
    progressSummary = previewProgressSummary,
    isProgressResolved = true,
)

@Composable
private fun HomeContentPreviewHost(state: HomeScreenState, levelCard: HomeLevelCardState = previewLevelCard) {
    FlashcardsTheme {
        HomeContent(
            state = state.copy(levelCard = levelCard),
            now = previewRecentsNow,
            zoneId = previewRecentsZoneId,
            onCategoryClick = {},
            onCategoryQuickSessionClick = {},
            onSubcategoryClick = {},
            onSubcategoryPlayClick = {},
            onRecentClick = {},
            onFavoritesHintHide = {},
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
    HomeContentPreviewHost(state = previewFavoritesAndRecentsState)
}

@PreviewLightDark
@Composable
private fun HomeContentFavoritesHintPreview() {
    HomeContentPreviewHost(
        state = HomeScreenState(favorites = FavoritesEmpty, recents = RecentsContent(previewRecentItems), hasHiddenFavoritesHint = false),
    )
}

@PreviewLightDark
@Composable
private fun HomeContentFavoritesHintHiddenPreview() {
    HomeContentPreviewHost(
        state = HomeScreenState(favorites = FavoritesEmpty, recents = RecentsContent(previewRecentItems), hasHiddenFavoritesHint = true),
    )
}

@PreviewLightDark
@Composable
private fun HomeContentRecentsPlaceholderPreview() {
    HomeContentPreviewHost(
        state = HomeScreenState(
            favorites = previewFavorites,
            recents = RecentsEmpty,
            progressSummary = previewProgressSummary,
            isProgressResolved = true,
        ),
    )
}

@PreviewLightDark
@Composable
private fun HomeContentLevelCardSkeletonPreview() {
    HomeContentPreviewHost(state = previewFavoritesAndRecentsState, levelCard = LevelCardLoading)
}

@PreviewLightDark
@Composable
private fun HomeContentLevelCardUnavailablePreview() {
    HomeContentPreviewHost(state = previewFavoritesAndRecentsState, levelCard = LevelCardUnavailable)
}

@PreviewLightDark
@Composable
private fun HomeContentLevelCardLongNamePreview() {
    HomeContentPreviewHost(
        state = previewFavoritesAndRecentsState,
        levelCard = previewLevelCard.copy(displayName = "Bartholomew Maximilian Featherstonehaugh-Worthington III"),
    )
}

@Preview(fontScale = 2f)
@Preview(fontScale = 2f, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun HomeContentLargeFontPreview() {
    HomeContentPreviewHost(state = previewFavoritesAndRecentsState)
}

@Preview(fontScale = 2f)
@Preview(fontScale = 2f, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun HomeContentErrorLargeFontPreview() {
    HomeContentPreviewHost(state = HomeScreenState(favorites = FavoritesFailed, recents = RecentsFailed))
}

@Preview(locale = "ar")
@Preview(locale = "ar", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun HomeContentRtlPreview() {
    HomeContentPreviewHost(state = previewFavoritesAndRecentsState)
}
