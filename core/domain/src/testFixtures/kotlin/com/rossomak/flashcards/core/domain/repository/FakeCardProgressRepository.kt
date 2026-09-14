package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.ProgressSummary
import com.rossomak.flashcards.core.domain.model.SubcategoryProgress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.yield

class FakeCardProgressRepository : CardProgressRepository {
    private val progressBySubcategoryId: MutableMap<String, SubcategoryProgress> = mutableMapOf()
    private var summary: ProgressSummary? = null

    /** Overrides every [getProgress] call when set, success or failure alike. */
    var resultToReturn: Result<SubcategoryProgress?>? = null

    /** Overrides every [getProgressSummary] call when set, success or failure alike. */
    var summaryResultToReturn: Result<ProgressSummary?>? = null

    /**
     * When set, [getProgressSummary] suspends on this until the test completes it — lets a test
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

    fun seedSummary(summary: ProgressSummary) {
        this.summary = summary
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

    override suspend fun getProgressSummary(): Result<ProgressSummary?> {
        summaryReadGate?.await() ?: yield()
        return summaryResultToReturn ?: Result.success(summary)
    }
}
