package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.AppPermission
import com.rossomak.flashcards.core.domain.model.PermissionStatus
import com.rossomak.flashcards.core.domain.repository.PermissionGateway
import com.rossomak.flashcards.core.domain.usecase.base.UseCase
import javax.inject.Inject

class RequestPermissionUseCase @Inject constructor(
    private val permissionGateway: PermissionGateway,
) : UseCase<AppPermission, PermissionStatus> {

    override suspend operator fun invoke(params: AppPermission): PermissionStatus =
        permissionGateway.request(params)
}
