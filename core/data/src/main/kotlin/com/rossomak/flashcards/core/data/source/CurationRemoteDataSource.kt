package com.rossomak.flashcards.core.data.source

import com.rossomak.flashcards.core.data.model.CurationRequestDto
import com.rossomak.flashcards.core.domain.model.CurationAction

interface CurationRemoteDataSource {

    suspend fun getCurationRequests(cardIds: List<String>): Map<String, CurationRequestDto>

    suspend fun upsertCurationActions(cardId: String, subcategoryId: String, actions: Set<CurationAction>)
}
