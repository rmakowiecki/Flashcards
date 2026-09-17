package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.UserPreferences
import com.rossomak.flashcards.core.domain.repository.UserPreferencesRepository
import com.rossomak.flashcards.core.domain.usecase.base.NoParamUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveUserPreferencesUseCase @Inject constructor(
    private val repository: UserPreferencesRepository,
) : NoParamUseCase<Flow<UserPreferences>> {
    override suspend operator fun invoke(): Flow<UserPreferences> = repository.userPreferences()
}
