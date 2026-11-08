package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.usecase.base.NoParamUseCase
import javax.inject.Inject

/** Whether a User is signed in. A Guest is not. */
class IsSignedInUseCase @Inject constructor(private val getCurrentAuthUser: GetCurrentAuthUserUseCase) : NoParamUseCase<Boolean> {

    override suspend operator fun invoke(): Boolean = getCurrentAuthUser()?.isAnonymous == false
}
