package com.rossomak.flashcards.feature.home

import com.rossomak.flashcards.core.domain.model.ProgressSummary
import com.rossomak.flashcards.core.ui.composables.level.FlashcardsLevelCardState
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
import com.rossomak.flashcards.feature.home.HomeStreakArea.Omitted as StreakOmitted
import com.rossomak.flashcards.feature.home.HomeStreakArea.Row as StreakRow
import com.rossomak.flashcards.feature.home.HomeStreakState.Content as StreakContent
import com.rossomak.flashcards.feature.home.HomeStreakState.Failed as StreakFailed
import com.rossomak.flashcards.feature.home.HomeStreakState.Loading as StreakLoading

/**
 * @param levelCard what Home knows about the Level card; [FlashcardsLevelCardState.Loading] until the Level and
 * the auth user have both emitted. Stored on its own and never part of [body].
 * @param favorites what Home knows about the Favorites; [HomeFavoritesState.Loading] until the first emission.
 * @param recents what Home knows about the Recents; [HomeRecentsState.Loading] until the first emission.
 * @param streak what Home knows about the Streak and Daily Goal row; [HomeStreakState.Loading] until the scoring state
 * emits and, for a User who studied, the Daily Goal.
 * @param hasRevealCeilingElapsed whether the reveal ceiling ran out. Once it has, a section still loading is left
 * out of [body] instead of holding back the others, and joins in place when it arrives.
 * @param hasHiddenFavoritesHint whether the User hid the Favorites hint; `null` until the preference is read.
 * Consulted only when Favorites are empty next to Recents, the one body the hint can appear in.
 * @param progressSummary the User's per-Subcategory counts; `null` until a summary document exists.
 * @param isProgressResolved whether the summary read has finished. `false` renders every Subcategory's
 * progress as unknown, which is not the same as a resolved zero.
 */
data class HomeScreenState(
    val levelCard: FlashcardsLevelCardState = FlashcardsLevelCardState.Loading,
    val favorites: HomeFavoritesState = FavoritesLoading,
    val recents: HomeRecentsState = RecentsLoading,
    val streak: HomeStreakState = StreakLoading,
    val hasRevealCeilingElapsed: Boolean = false,
    val hasHiddenFavoritesHint: Boolean? = null,
    val progressSummary: ProgressSummary? = null,
    val isProgressResolved: Boolean = false,
) {
    /**
     * [FirstSession] reads Favorites and Recents only, so it never waits for the row. Otherwise every section
     * reveals together: [Resolving] while any is loading, until the ceiling elapses. A Failed section is never
     * "none": with no content anywhere it is [LoadError], next to content it is left out. An unresolved hint
     * preference holds the body back too, so a hidden hint never flashes.
     */
    val body: HomeBody
        get() {
            val isAnyLoading = favorites is FavoritesLoading || recents is RecentsLoading || streak is StreakLoading
            val isAnyFailed = favorites is FavoritesFailed || recents is RecentsFailed || streak is StreakFailed
            val hasAnyContent = favorites is FavoritesContent || recents is RecentsContent || streak is StreakContent
            val isHintPreferenceUnresolved = canShowFavoritesHint && hasHiddenFavoritesHint == null
            return when {
                favorites is FavoritesEmpty && recents is RecentsEmpty -> FirstSession
                (isAnyLoading || isHintPreferenceUnresolved) && !hasRevealCeilingElapsed -> Resolving
                isAnyFailed && !hasAnyContent -> LoadError
                else -> Sections(streak = streakArea(), favorites = favoritesArea(), recents = recentsArea())
            }
        }

    private fun streakArea(): HomeStreakArea = if (streak is StreakContent) StreakRow(streak) else StreakOmitted

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
