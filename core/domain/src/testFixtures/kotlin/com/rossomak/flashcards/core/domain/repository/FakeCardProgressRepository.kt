package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.ProgressSummary
import com.rossomak.flashcards.core.domain.model.SubcategoryProgress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.yield

class FakeCardProgressRepository : CardProgressRepository {
    private val progressBySubcategoryId: MutableMap<String, SubcategoryProgress> = mutableMapOf()
    private var currentSummary: ProgressSummary? = null
    private val summaryUpdates = MutableSharedFlow<ProgressSummary?>(extraBufferCapacity = 1)

    /** Overrides every [getProgress] call when set, success or failure alike. */
    var resultToReturn: Result<SubcategoryProgress?>? = null

    /**
     * When set, [observeProgressSummary] suspends on this before its first emission — lets a test
     * park the summary read indefinitely to assert an in-between state (e.g. subcategories loaded,
     * summary still pending) instead of only the states before and after `advanceUntilIdle()`
     * drains everything at once. `null` (the default) keeps the old single-[yield] behavior.
     */
    var summaryReadGate: CompletableDeferred<Unit>? = null

    /** Every Subcategory id [getProgress] was actually called with, in call order. */
    val requestedSubcategoryIds: MutableList<String> = mutableListOf()

    fun seed(progress: SubcategoryProgress) {
        progressBySubcategoryId[progress.subcategoryId] = progress
    }

    /**
     * Sets the value [observeProgressSummary] emits, both as the initial snapshot for a not-yet-
     * subscribed collector and, if called again after a collector is already active, as a live
     * update — mirroring a real Firestore snapshot listener re-firing after a change.
     */
    fun seedSummary(summary: ProgressSummary) {
        currentSummary = summary
        summaryUpdates.tryEmit(summary)
    }

    /**
     * [yield]s once before returning, mirroring [com.rossomak.flashcards.core.data.repository.DefaultCardProgressRepository]'s
     * genuine `withContext(Dispatchers.IO)` dispatcher hop — the same reasoning as
     * [FakeSessionSubmissionRepository]'s own [yield].
     */
    override suspend fun getProgress(subcategoryId: String): Result<SubcategoryProgress?> {
        requestedSubcategoryIds.add(subcategoryId)
        yield()
        return resultToReturn ?: Result.success(progressBySubcategoryId[subcategoryId])
    }

    override fun observeProgressSummary(): Flow<ProgressSummary?> = flow {
        summaryReadGate?.await() ?: yield()
        emit(currentSummary)
        emitAll(summaryUpdates)
    }
}
