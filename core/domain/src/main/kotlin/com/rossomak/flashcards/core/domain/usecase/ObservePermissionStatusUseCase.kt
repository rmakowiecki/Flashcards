package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.AppPermission
import com.rossomak.flashcards.core.domain.model.PermissionStatus
import com.rossomak.flashcards.core.domain.repository.PermissionGateway
import com.rossomak.flashcards.core.domain.usecase.base.UseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObservePermissionStatusUseCase @Inject constructor(
    private val permissionGateway: PermissionGateway,
) : UseCase<AppPermission, Flow<PermissionStatus>> {

    override suspend operator fun invoke(params: AppPermission): Flow<PermissionStatus> =
        permissionGateway.observeStatus(params)
}
