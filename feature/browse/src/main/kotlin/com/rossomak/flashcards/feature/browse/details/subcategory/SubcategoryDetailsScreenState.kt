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
    val isFavorite: Boolean = false,
    val activeDialog: SubcategoryDetailsDialog? = null,
) {

    /**
     * Drives the Filter badge. Sort has no badge of its own (ADR-0038).
     *
     * Compares against the live [availableTags] rather than a fixed constant: "all tags selected"
     * is the default, and which tags that means depends on the Subcategory's own pool.
     */
    val hasActiveFilters: Boolean
        get() = filters.selectedTags != availableTags.toSet() || filters.difficultyRange != DIFFICULTY_BOUNDS

    /**
     * How many cards the CTA would start a session on — the *filtered pool*, which the Preview
     * screen may then narrow further by the user's saved session length. The two numbers are
     * allowed to differ: length is a sticky preference, not something a browse filter overrides
     * (ADR-0038).
     */
    val sessionCardCount: Int
        get() = (content as? SubcategoryDetailsContentState.Cards)?.flashcards?.size ?: 0

    companion object {
        val DIFFICULTY_BOUNDS: IntRange =
            StudySessionConfig.MIN_DIFFICULTY..StudySessionConfig.MAX_DIFFICULTY
    }
}

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

    /**
     * Carries a resource id rather than a built string: the ViewModel has no business holding
     * user-facing English, and lint cannot see a hardcoded one there (ADR-0023).
     */
    data class Error(@StringRes val messageRes: Int) : SubcategoryDetailsContentState

    data class Cards(val flashcards: List<Flashcard>) : SubcategoryDetailsContentState

    /** Filters excluded every card in the pool. */
    data object NoMatches : SubcategoryDetailsContentState
}
