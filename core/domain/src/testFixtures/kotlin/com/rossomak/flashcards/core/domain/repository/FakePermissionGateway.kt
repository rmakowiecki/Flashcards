package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.AppPermission
import com.rossomak.flashcards.core.domain.model.PermissionStatus
import com.rossomak.flashcards.core.domain.model.PermissionStatus.Granted
import com.rossomak.flashcards.core.domain.model.PermissionStatus.NotRequested
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakePermissionGateway : PermissionGateway {

    /** Current status per permission; an absent entry reads as [NotRequested]. */
    val statuses = MutableStateFlow<Map<AppPermission, PermissionStatus>>(emptyMap())

    /** The status the next prompting [request] resolves to (and stores in [statuses]). */
    var nextRequestResult: PermissionStatus = Granted

    /** Every permission [request] actually prompted for, in order; already-granted requests are not recorded. */
    val launchedRequests = mutableListOf<AppPermission>()

    override val permissionRequests: Flow<AppPermission> = emptyFlow()

    override fun observeStatus(permission: AppPermission): Flow<PermissionStatus> =
        statuses.map { it[permission] ?: NotRequested }

    override suspend fun request(permission: AppPermission): PermissionStatus {
        if (statuses.value[permission] == Granted) return Granted
        launchedRequests += permission
        val result = nextRequestResult
        statuses.update { it + (permission to result) }
        return result
    }

    override fun onPermissionResult(permission: AppPermission, isGranted: Boolean, shouldShowRationale: Boolean) = Unit
}
