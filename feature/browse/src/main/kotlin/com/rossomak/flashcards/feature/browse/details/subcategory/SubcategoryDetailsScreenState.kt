package com.rossomak.flashcards.feature.browse.details.subcategory

import androidx.annotation.StringRes
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.FlashcardSortOrder
import com.rossomak.flashcards.core.domain.model.StudySessionConfig
import com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardFilters

/**
 * @param content what the list area is showing. Sealed rather than a loading flag plus a nullable
 * error plus a list, so "loading and failed at once" is unrepresentable and the screen's `when`
 * cannot silently depend on branch order.
 * @param sortOrder session-scoped, seeded from the user's saved preference on load and carried into
 * the Study Session the CTA starts — browsing and studying share one notion of order (ADR-0038).
 * Deliberately **not** badged in the toolbar: seeded from a preference, "non-default" would be
 * permanently lit for anyone whose saved order is not [FlashcardSortOrder.Default].
 * @param filters session-scoped and never a saved default — tags belong to one Subcategory and
 * cannot carry to another (ADR-0030). Every tag starts selected: the ViewModel materializes
 * [FlashcardFilters.selectedTags] to the pool's full tag set once it loads, rather than leaving it
 * empty-meaning-all, so the Filters dialog opens with every chip already checked.
 * @param availableTags the tag vocabulary of the whole pool, so a chip never vanishes because the
 * user filtered it out.
 * @param totalCount unfiltered pool size, the second number in "filtered to 4 of 80".
 * @param hasActiveFilters drives the Filter badge and the counted CTA; sort has no badge of its own
 * (ADR-0038). Computed by the ViewModel whenever [filters] or [availableTags] change, and held as a
 * field so the screen's several reads per composition don't each rebuild a tag set.
 * @param isFavorite live from [UserFavoritesRepository.observeFavorites][com.rossomak.flashcards.core.domain.repository.UserFavoritesRepository.observeFavorites], written via [SubcategoryDetailsViewModel.onFavoriteToggle].
 */
data class SubcategoryDetailsScreenState(
    val subcategoryId: String = "",
    val categoryName: String = "",
    val subcategoryName: String = "",
    val content: SubcategoryDetailsContentState = SubcategoryDetailsContentState.Loading,
    val sortOrder: FlashcardSortOrder = FlashcardSortOrder.Default,
    val filters: FlashcardFilters = FlashcardFilters(selectedTags = emptySet(), difficultyRange = DIFFICULTY_BOUNDS),
    val availableTags: List<String> = emptyList(),
    val totalCount: Int = 0,
    val hasActiveFilters: Boolean = false,
    val isFavorite: Boolean = false,
    val activeDialog: SubcategoryDetailsDialog? = null,
) {

    /**
     * How many cards the CTA would start a session on — the *filtered pool*, which the Preview
     * screen may then narrow further by the user's saved session length. The two numbers are
     * allowed to differ: length is a sticky preference, not something a browse filter overrides
     * (ADR-0038).
     */
    val sessionCardCount: Int
        get() = (content as? SubcategoryDetailsContentState.FlashcardsList)?.flashcards?.size ?: 0

    companion object {
        val DIFFICULTY_BOUNDS: IntRange =
            StudySessionConfig.MIN_DIFFICULTY..StudySessionConfig.MAX_DIFFICULTY
    }
}

/**
 * Whether [filters] narrow the pool at all. Compares against the live [availableTags] rather than a
 * fixed constant: "all tags selected" is the default, and which tags that means depends on the
 * Subcategory's own pool.
 */
internal fun hasActiveFilters(filters: FlashcardFilters, availableTags: List<String>): Boolean =
    filters.selectedTags != availableTags.toSet() ||
        filters.difficultyRange != SubcategoryDetailsScreenState.DIFFICULTY_BOUNDS

/**
 * The four situations the flashcard list area can be in.
 *
 * There is deliberately **no case for a Subcategory that holds no Flashcards**: a Subcategory always
 * contains at least one (see `CONTEXT.md`), so an empty list can only ever mean the filters excluded
 * everything — [NoMatches]. The same invariant is what lets the data layer read cache-first and treat
 * an empty result as a cache miss (ADR-0038); anything that makes an empty Subcategory representable
 * has to revisit both.
 */
sealed interface SubcategoryDetailsContentState {

    data object Loading : SubcategoryDetailsContentState

    data class FlashcardsList(val flashcards: List<Flashcard>) : SubcategoryDetailsContentState

    data class Error(@param:StringRes val messageRes: Int) : SubcategoryDetailsContentState

    /** Filters excluded every card in the pool. */
    data object NoMatches : SubcategoryDetailsContentState
}
