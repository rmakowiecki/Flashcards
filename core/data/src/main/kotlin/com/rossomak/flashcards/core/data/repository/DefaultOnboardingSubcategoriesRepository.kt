package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.data.mapper.toDomain
import com.rossomak.flashcards.core.data.source.OnboardingSubcategoriesRemoteDataSource
import com.rossomak.flashcards.core.domain.model.OnboardingSubcategory
import com.rossomak.flashcards.core.domain.repository.OnboardingSubcategoriesRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultOnboardingSubcategoriesRepository @Inject constructor(
    private val remoteDataSource: OnboardingSubcategoriesRemoteDataSource,
) : OnboardingSubcategoriesRepository {

    // Generic catch is deliberate: this is the repository boundary converting any Firestore
    // failure into Result.failure, same as DefaultFlashcardRepository's fetch* methods.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun fetchOnboardingSubcategories(): Result<List<OnboardingSubcategory>> = withContext(Dispatchers.IO) {
        try {
            Result.success(remoteDataSource.getOnboardingSubcategories().mapNotNull { it.toDomain() })
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }
}
