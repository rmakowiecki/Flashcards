package com.rossomak.flashcards.debugtools.showcase

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.rossomak.flashcards.core.domain.model.FavoriteItem
import com.rossomak.flashcards.debugtools.showcase.fixtures.LocalShowcaseFixtures
import com.rossomak.flashcards.debugtools.showcase.fixtures.ShowcaseFixtures
import com.rossomak.flashcards.debugtools.showcase.fixtures.ShowcaseUser
import com.rossomak.flashcards.debugtools.showcase.fixtures.recentItems
import com.rossomak.flashcards.debugtools.showcase.fixtures.showcaseFavorites
import com.rossomak.flashcards.debugtools.showcase.fixtures.showcaseProgressSummary
import com.rossomak.flashcards.feature.home.HomeContent
import com.rossomak.flashcards.feature.home.HomeFavoritesState
import com.rossomak.flashcards.feature.home.HomeRecentsState
import com.rossomak.flashcards.feature.home.HomeScreenState
import com.rossomak.flashcards.feature.home.HomeStreakState
import java.time.Instant

/** Newest first, Categories and Topics mixed, as Home resolves them. */
private fun ShowcaseFixtures.favoriteItems(): List<FavoriteItem> {
    val favoriteCategories = showcaseFavorites.categoryIds.map { (id, favoritedAt) -> FavoriteItem.FavoriteCategory(category(id), favoritedAt) }
    val favoriteSubcategories = showcaseFavorites.subcategoryIds.map { (id, favoritedAt) ->
        val subcategory = subcategory(id)
        FavoriteItem.FavoriteSubcategory(subcategory, category(subcategory.categoryId), favoritedAt)
    }
    return (favoriteCategories + favoriteSubcategories).sortedByDescending { it.favoritedAt }
}

private fun ShowcaseFixtures.homeState(now: Instant) = HomeScreenState(
    levelCard = ShowcaseUser.levelCard,
    favorites = HomeFavoritesState.Content(favoriteItems()),
    recents = HomeRecentsState.Content(recentItems(now)),
    streak = HomeStreakState.Content(
        days = ShowcaseUser.CURRENT_STREAK,
        bestDays = ShowcaseUser.BEST_STREAK,
        isAtRisk = false,
        studiedMinutes = ShowcaseUser.STUDIED_MINUTES_TODAY,
        goalMinutes = ShowcaseUser.DAILY_GOAL_MINUTES,
    ),
    hasHiddenFavoritesHint = true,
    progressSummary = showcaseProgressSummary,
    isProgressResolved = true,
)

/** Pads for the system navigation bar itself: Main's scaffold, which does it in the app, is not drawn. */
@Composable
internal fun HomeShowcase() {
    val fixtures = LocalShowcaseFixtures.current
    val now = remember { Instant.now() }
    HomeContent(
        modifier = Modifier.navigationBarsPadding(),
        state = remember(fixtures) { fixtures.homeState(now) },
        now = { now },
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
