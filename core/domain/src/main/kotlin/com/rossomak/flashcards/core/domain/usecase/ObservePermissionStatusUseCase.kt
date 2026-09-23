package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.AppPermission
import com.rossomak.flashcards.core.domain.model.PermissionStatus
import com.rossomak.flashcards.core.domain.repository.PermissionRepository
import com.rossomak.flashcards.core.domain.usecase.base.UseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObservePermissionStatusUseCase @Inject constructor(
    private val permissionRepository: PermissionRepository,
) : UseCase<AppPermission, Flow<PermissionStatus>> {

    override suspend operator fun invoke(params: AppPermission): Flow<PermissionStatus> =
        permissionRepository.observeStatus(params)
}
