package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.CardProgressEntry
import com.rossomak.flashcards.core.domain.model.SessionStartData
import com.rossomak.flashcards.core.domain.usecase.base.UseCase
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Composes [GetFlashcardsUseCase] and [GetSubcategoryProgressUseCase] into the
 * one fan-out both Study Session ViewModels need at load time: one read of each kind per
 * Subcategory in [params], all fired in parallel, merged into
 * [SessionStartData]. Replaces what used to be a near-identical fan-out/merge block duplicated
 * across the two ViewModels.
 *
 * [SessionStartData.flashcardsResult] fails as a whole the moment any one Subcategory's flashcard
 * read fails — a session cannot run without its cards. [SessionStartData.priorProgressByCardId] never
 * fails this call: a failed or never-studied Subcategory's progress read is folded into "no entries"
 * (`getOrNull`), never surfaced as an error and never blocking the session.
 */
class GetSessionStartDataUseCase @Inject constructor(
    private val getFlashcards: GetFlashcardsUseCase,
    private val getSubcategoryProgress: GetSubcategoryProgressUseCase,
) : UseCase<List<String>, SessionStartData> {

    override suspend operator fun invoke(params: List<String>): SessionStartData = coroutineScope {
        val flashcardsDeferred = params.map { subcategoryId -> async { getFlashcards(subcategoryId) } }
        val progressDeferred = params.map { subcategoryId -> async { getSubcategoryProgress(subcategoryId) } }
        val flashcardResults = flashcardsDeferred.awaitAll()
        val progressResults = progressDeferred.awaitAll()

        val flashcardsResult = flashcardResults.firstOrNull { it.isFailure }
            ?: Result.success(flashcardResults.flatMap { it.getOrThrow() })

        val priorProgressByCardId: Map<String, CardProgressEntry> = progressResults
            .mapNotNull { it.getOrNull() }
            .flatMap { it.cards.entries }
            .associate { it.key to it.value }

        SessionStartData(flashcardsResult = flashcardsResult, priorProgressByCardId = priorProgressByCardId)
    }
}
