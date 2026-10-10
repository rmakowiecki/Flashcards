package com.rossomak.flashcards.feature.home

import com.rossomak.flashcards.core.domain.model.ProgressSummary
import com.rossomak.flashcards.feature.home.HomeBody.FirstSession
import com.rossomak.flashcards.feature.home.HomeBody.LoadError
import com.rossomak.flashcards.feature.home.HomeBody.Resolving
import com.rossomak.flashcards.feature.home.HomeBody.Sections
import com.rossomak.flashcards.feature.home.HomeFavoritesArea.Carousel
import com.rossomak.flashcards.feature.home.HomeFavoritesArea.Hint
import com.rossomak.flashcards.feature.home.HomeFavoritesArea.Omitted as FavoritesOmitted
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Content as FavoritesContent
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Empty as FavoritesEmpty
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Failed as FavoritesFailed
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Loading as FavoritesLoading
import com.rossomak.flashcards.feature.home.HomeRecentsArea.Omitted as RecentsOmitted
import com.rossomak.flashcards.feature.home.HomeRecentsArea.Placeholder
import com.rossomak.flashcards.feature.home.HomeRecentsArea.Rows
import com.rossomak.flashcards.feature.home.HomeRecentsState.Content as RecentsContent
import com.rossomak.flashcards.feature.home.HomeRecentsState.Empty as RecentsEmpty
import com.rossomak.flashcards.feature.home.HomeRecentsState.Failed as RecentsFailed
import com.rossomak.flashcards.feature.home.HomeRecentsState.Loading as RecentsLoading

/**
 * @param levelCard what Home knows about the Level card; [HomeLevelCardState.Loading] until the Level and the
 * auth user have both emitted. Stored on its own and never part of [body].
 * @param favorites what Home knows about the Favorites; [HomeFavoritesState.Loading] until the first emission.
 * @param recents what Home knows about the Recents; [HomeRecentsState.Loading] until the first emission.
 * @param hasRevealCeilingElapsed whether the reveal ceiling ran out. Once it has, a section still loading is left
 * out of [body] instead of holding back the other one, and joins in place when it arrives.
 * @param hasHiddenFavoritesHint whether the User hid the Favorites hint; `null` until the preference is read.
 * Consulted only when Favorites are empty next to Recents, the one body the hint can appear in.
 * @param progressSummary the User's per-Subcategory counts; `null` until a summary document exists.
 * @param isProgressResolved whether the summary read has finished. `false` renders every Subcategory's
 * progress as unknown, which is not the same as a resolved zero.
 */
data class HomeScreenState(
    val levelCard: HomeLevelCardState = HomeLevelCardState.Loading,
    val favorites: HomeFavoritesState = FavoritesLoading,
    val recents: HomeRecentsState = RecentsLoading,
    val hasRevealCeilingElapsed: Boolean = false,
    val hasHiddenFavoritesHint: Boolean? = null,
    val progressSummary: ProgressSummary? = null,
    val isProgressResolved: Boolean = false,
) {
    /**
     * Favorites and Recents reveal together: [Resolving] while either is loading, until the ceiling elapses. A
     * [Failed][FavoritesFailed] section is never "none", so it never produces [FirstSession]: with no content
     * anywhere it is [LoadError], next to content it is simply left out. When the Favorites hint could show,
     * the hint preference holds the body back too, so an already-hidden hint never flashes.
     */
    val body: HomeBody
        get() {
            val isAnyLoading = favorites is FavoritesLoading || recents is RecentsLoading
            val isAnyFailed = favorites is FavoritesFailed || recents is RecentsFailed
            val hasAnyContent = favorites is FavoritesContent || recents is RecentsContent
            val isHintPreferenceUnresolved = canShowFavoritesHint && hasHiddenFavoritesHint == null
            return when {
                (isAnyLoading || isHintPreferenceUnresolved) && !hasRevealCeilingElapsed -> Resolving
                favorites is FavoritesEmpty && recents is RecentsEmpty -> FirstSession
                isAnyFailed && !hasAnyContent -> LoadError
                else -> Sections(favorites = favoritesArea(), recents = recentsArea())
            }
        }

    private val canShowFavoritesHint: Boolean
        get() = favorites is FavoritesEmpty && recents is RecentsContent

    /** An unresolved preference after the ceiling omits the hint; a late `false` joins in place. */
    private fun favoritesArea(): HomeFavoritesArea = when {
        favorites is FavoritesContent -> Carousel(favorites.items)
        canShowFavoritesHint && hasHiddenFavoritesHint == false -> Hint
        else -> FavoritesOmitted
    }

    private fun recentsArea(): HomeRecentsArea = when {
        recents is RecentsContent -> Rows(recents.items)
        recents is RecentsEmpty && favorites is FavoritesContent -> Placeholder
        else -> RecentsOmitted
    }
}
