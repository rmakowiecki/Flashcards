package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.usecase.base.NoParamUseCase
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.withTimeoutOrNull

/** Whether a User is signed in. A Guest is not, and an auth read slower than [AUTH_TIMEOUT_MS] counts as signed out. */
class IsSignedInUseCase @Inject constructor(private val getCurrentAuthUser: GetCurrentAuthUserUseCase) : NoParamUseCase<Boolean> {

    override suspend operator fun invoke(): Boolean = withTimeoutOrNull(AUTH_TIMEOUT_MS.milliseconds) {
        getCurrentAuthUser()?.isAnonymous == false
    } ?: false

    private companion object {
        const val AUTH_TIMEOUT_MS = 1000L
    }
}
