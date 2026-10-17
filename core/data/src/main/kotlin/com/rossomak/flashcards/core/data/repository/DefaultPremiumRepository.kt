package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.common.logw
import com.rossomak.flashcards.core.data.source.PremiumRemoteDataSource
import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.repository.AuthRepository
import com.rossomak.flashcards.core.domain.repository.PremiumRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Follows the record of whoever is signed in, restarting on every change of User. A Guest reads as not
 * Premium without a listener, since paid functions refuse a Guest even when a grant exists.
 */
class DefaultPremiumRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val remoteDataSource: PremiumRemoteDataSource,
) : PremiumRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observePremium(): Flow<Boolean?> = authRepository.observeAuthUser()
        .map { user -> user.premiumUid() }
        .distinctUntilChanged()
        .flatMapLatest { uid -> if (uid == null) flowOf(false) else observeRecord(uid) }
        .distinctUntilChanged()

    override suspend fun fetchPremium(): Result<Boolean> {
        val uid = authRepository.getCurrentUser().premiumUid() ?: return Result.success(false)
        return runCatchingFirestore { remoteDataSource.fetchPremium(uid) }
            .onFailure { exception -> logw(exception) { "Premium fetch failed" } }
    }

    private fun observeRecord(uid: String): Flow<Boolean?> = remoteDataSource.observePremium(uid)
        .retryOnFirestorePermissionDenied()
        .map<Boolean, Boolean?> { isPremium -> isPremium }
        .onStart { emit(null) }
        .catch { exception ->
            logw(exception) { "Premium listener failed" }
            emit(null)
        }

    /** The uid whose record counts, or null for a Guest or when signed out. */
    private fun AuthUser?.premiumUid(): String? = this?.takeIf { user -> user.provider != null }?.uid
}
