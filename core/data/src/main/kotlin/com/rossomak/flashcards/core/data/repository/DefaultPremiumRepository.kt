package com.rossomak.flashcards.core.data.repository

import com.google.firebase.firestore.FirebaseFirestoreException
import com.rossomak.flashcards.core.common.logw
import com.rossomak.flashcards.core.data.source.PremiumRemoteDataSource
import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.repository.AuthRepository
import com.rossomak.flashcards.core.domain.repository.PremiumRepository
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** A Guest reads as not Premium with no listener: paid functions refuse a Guest even when a grant exists. */
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
            .recoverCatching { exception -> if (exception.isUnreachable()) throw exception else false }
    }

    private fun Throwable.isUnreachable(): Boolean =
        this is IOException || (this is FirebaseFirestoreException && code in UNREACHABLE_CODES)

    private fun observeRecord(uid: String): Flow<Boolean?> = remoteDataSource.observePremium(uid)
        .retryOnFirestorePermissionDenied()
        .map<Boolean, Boolean?> { isPremium -> isPremium }
        .onStart { emit(null) }
        .catch { exception ->
            logw(exception) { "Premium listener failed" }
            emit(null)
        }

    private fun AuthUser?.premiumUid(): String? = this?.takeIf { user -> user.provider != null }?.uid

    private companion object {
        val UNREACHABLE_CODES = setOf(
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
        )
    }
}
