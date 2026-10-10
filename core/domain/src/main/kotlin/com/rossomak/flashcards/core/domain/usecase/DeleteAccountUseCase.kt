package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.AccountDeletionResult
import com.rossomak.flashcards.core.domain.repository.AccountRepository
import com.rossomak.flashcards.core.domain.usecase.base.NoParamUseCase
import javax.inject.Inject

class DeleteAccountUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
) : NoParamUseCase<AccountDeletionResult> {

    override suspend operator fun invoke(): AccountDeletionResult = accountRepository.deleteAccount()
}
