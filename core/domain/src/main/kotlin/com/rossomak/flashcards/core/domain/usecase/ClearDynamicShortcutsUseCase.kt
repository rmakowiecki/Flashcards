package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.repository.AppShortcutsRepository
import com.rossomak.flashcards.core.domain.usecase.base.NoParamUseCase
import javax.inject.Inject

/**
 * Removes all of the app's dynamic shortcuts, e.g. when a signed-out startup must not keep
 * showing shortcuts pinned to a previous session's favorites.
 */
class ClearDynamicShortcutsUseCase @Inject constructor(
    private val appShortcutsRepository: AppShortcutsRepository,
) : NoParamUseCase<Unit> {

    override suspend operator fun invoke() {
        appShortcutsRepository.syncDynamicShortcuts(emptyList())
    }
}
