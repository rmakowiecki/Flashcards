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
import java.time.ZoneId
import kotlin.time.Duration.Companion.seconds

/** Newest first, Categories and Topics mixed, as Home resolves them. */
private fun ShowcaseFixtures.favoriteItems(): List<FavoriteItem> {
    val favoriteCategories = showcaseFavorites.categoryIds.map { (id, favoritedAt) -> FavoriteItem.FavoriteCategory(category(id), favoritedAt) }
    val favoriteSubcategories = showcaseFavorites.subcategoryIds.map { (id, favoritedAt) ->
        val subcategory = subcategory(id)
        FavoriteItem.FavoriteSubcategory(subcategory, category(subcategory.categoryId), favoritedAt)
    }
    return (favoriteCategories + favoriteSubcategories).sortedByDescending { it.favoritedAt }
}

/** The Daily Goal sums today's Recents, so the ring matches the rows. */
private fun ShowcaseFixtures.homeState(now: Instant, zone: ZoneId): HomeScreenState {
    val recents = recentItems(now, zone)
    val startOfToday = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
    val studiedMinutesToday = recents.filter { it.session.startedAt >= startOfToday }.sumOf { it.session.durationSeconds }.seconds.inWholeMinutes.toInt()
    return HomeScreenState(
        levelCard = ShowcaseUser.levelCard,
        favorites = HomeFavoritesState.Content(favoriteItems()),
        recents = HomeRecentsState.Content(recents),
        streak = HomeStreakState.Content(
            days = ShowcaseUser.CURRENT_STREAK,
            bestDays = ShowcaseUser.BEST_STREAK,
            isAtRisk = false,
            studiedMinutes = studiedMinutesToday,
            goalMinutes = ShowcaseUser.DAILY_GOAL_MINUTES,
            goalPercent = studiedMinutesToday * 100 / ShowcaseUser.DAILY_GOAL_MINUTES,
            isGoalMet = studiedMinutesToday >= ShowcaseUser.DAILY_GOAL_MINUTES,
        ),
        hasHiddenFavoritesHint = true,
        progressSummary = showcaseProgressSummary,
        isProgressResolved = true,
    )
}

/** Pads for the navigation bar, which Main's scaffold does in the app. */
@Composable
internal fun HomeShowcase() {
    val fixtures = LocalShowcaseFixtures.current
    val now = remember { Instant.now() }
    val zone = remember { ZoneId.systemDefault() }
    HomeContent(
        modifier = Modifier.navigationBarsPadding(),
        state = remember(fixtures) { fixtures.homeState(now, zone) },
        now = { now },
        zoneId = zone,
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
