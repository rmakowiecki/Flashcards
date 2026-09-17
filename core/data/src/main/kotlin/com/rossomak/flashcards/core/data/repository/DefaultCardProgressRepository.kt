package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.data.mapper.toDomain
import com.rossomak.flashcards.core.data.source.CardProgressRemoteDataSource
import com.rossomak.flashcards.core.data.source.ProgressSummaryRemoteDataSource
import com.rossomak.flashcards.core.domain.model.ProgressSummary
import com.rossomak.flashcards.core.domain.model.SubcategoryProgress
import com.rossomak.flashcards.core.domain.repository.CardProgressRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultCardProgressRepository @Inject constructor(
    private val remoteDataSource: CardProgressRemoteDataSource,
    private val progressSummaryRemoteDataSource: ProgressSummaryRemoteDataSource,
) : CardProgressRepository {

    override suspend fun getProgress(subcategoryId: String): Result<SubcategoryProgress?> =
        runCatchingFirestoreWrite { remoteDataSource.getProgress(subcategoryId)?.toDomain(subcategoryId) }

    override fun observeProgressSummary(): Flow<ProgressSummary?> =
        progressSummaryRemoteDataSource.observeSummary()
            .map { it?.toDomain() }
            .retryOnFirestorePermissionDenied()
}
