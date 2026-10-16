package com.rossomak.flashcards.debugtools.showcase

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.rossomak.flashcards.core.domain.model.FavoriteItem
import com.rossomak.flashcards.core.domain.model.UserFavorites
import com.rossomak.flashcards.debugtools.showcase.fixtures.ShowcaseTaxonomy
import com.rossomak.flashcards.debugtools.showcase.fixtures.ShowcaseUser
import com.rossomak.flashcards.debugtools.showcase.fixtures.showcaseRecentItems
import com.rossomak.flashcards.feature.home.HomeContent
import com.rossomak.flashcards.feature.home.HomeFavoritesState
import com.rossomak.flashcards.feature.home.HomeRecentsState
import com.rossomak.flashcards.feature.home.HomeScreenState
import java.time.Instant

/** [UserFavorites] resolved the way Home resolves them: newest first, Categories and Topics mixed. */
internal fun UserFavorites.toFavoriteItems(): List<FavoriteItem> = with(ShowcaseTaxonomy) {
    val favoriteCategories = categoryIds.map { (categoryId, favoritedAt) -> FavoriteItem.FavoriteCategory(categoryById(categoryId), favoritedAt) }
    val favoriteSubcategories = subcategoryIds.map { (subcategoryId, favoritedAt) ->
        val subcategory = subcategoriesByCategoryId.values.flatten().first { it.id == subcategoryId }
        FavoriteItem.FavoriteSubcategory(subcategory, categoryById(subcategory.categoryId), favoritedAt)
    }
    (favoriteCategories + favoriteSubcategories).sortedByDescending { it.favoritedAt }
}

private fun homeState(now: Instant) = HomeScreenState(
    levelCard = ShowcaseUser.levelCard,
    favorites = HomeFavoritesState.Content(ShowcaseTaxonomy.favorites.toFavoriteItems()),
    recents = HomeRecentsState.Content(showcaseRecentItems(now)),
    hasHiddenFavoritesHint = true,
    progressSummary = ShowcaseTaxonomy.progressSummary,
    isProgressResolved = true,
)

/**
 * Home with Favorites and Recents both loaded. The tab shell's bottom bar is not drawn, so the content
 * pads for the system navigation bar itself, which Main's scaffold does in the app.
 */
@Composable
internal fun HomeShowcase() {
    val now = remember { Instant.now() }
    val state = remember(now) { homeState(now) }
    HomeContent(
        modifier = Modifier.navigationBarsPadding(),
        state = state,
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
