package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.SignInResult
import com.rossomak.flashcards.core.domain.repository.AuthRepository
import com.rossomak.flashcards.core.domain.usecase.base.NoParamUseCase
import javax.inject.Inject

class SignInWithGitHubUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) : NoParamUseCase<SignInResult> {

    override suspend operator fun invoke(): SignInResult = authRepository.signInWithGitHub()
}
