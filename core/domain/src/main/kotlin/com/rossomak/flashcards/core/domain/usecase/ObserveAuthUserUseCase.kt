package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.repository.AuthRepository
import com.rossomak.flashcards.core.domain.usecase.base.NoParamUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * The signed-in user, then every change to it. The current user goes out first, on collect, because
 * the repository's listener reports it only a frame later, which would show a signed-in screen's
 * placeholder before the user's own data. The listener then repeats it once; `distinctUntilChanged`
 * drops that repeat.
 */
class ObserveAuthUserUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) : NoParamUseCase<Flow<AuthUser?>> {
    override suspend operator fun invoke(): Flow<AuthUser?> = flow {
        emit(authRepository.getCurrentUser())
        emitAll(authRepository.observeAuthUser())
    }.distinctUntilChanged()
}
