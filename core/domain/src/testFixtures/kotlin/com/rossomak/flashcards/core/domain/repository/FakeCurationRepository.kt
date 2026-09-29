package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.CurationAction
import com.rossomak.flashcards.core.domain.model.CurationRequest
import kotlinx.coroutines.CompletableDeferred

class FakeCurationRepository : CurationRepository {
    var curationRequestsToReturn: Result<Map<String, CurationRequest>> = Result.success(emptyMap())
    var upsertResultToReturn: Result<Unit> = Result.success(Unit)

    /** When set, a submission stays in flight until this completes. */
    var pendingUpsert: CompletableDeferred<Unit>? = null

    /** Every submission, in call order: card id, subcategory id, the whole reported set. */
    val submittedReports: MutableList<Triple<String, String, Set<CurationAction>>> = mutableListOf()

    override suspend fun getCurationRequests(cardIds: List<String>): Result<Map<String, CurationRequest>> =
        curationRequestsToReturn

    override suspend fun upsertCurationActions(
        cardId: String,
        subcategoryId: String,
        actions: Set<CurationAction>,
    ): Result<Unit> {
        submittedReports.add(Triple(cardId, subcategoryId, actions))
        pendingUpsert?.await()
        return upsertResultToReturn
    }
}
