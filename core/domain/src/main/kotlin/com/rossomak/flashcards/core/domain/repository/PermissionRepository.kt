package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.AppPermission
import com.rossomak.flashcards.core.domain.model.PermissionStatus
import kotlinx.coroutines.flow.Flow

/**
 * Checks and requests runtime permissions. Screens use [observeStatus] and [request]; the single
 * Activity-scoped launcher host consumes [permissionRequests] and reports back through
 * [onPermissionResult].
 */
interface PermissionRepository {

    /**
     * Cold: reads the current status on every new collection and re-emits after every request
     * result. It never polls, so a change made outside the app (system Settings) is picked up by
     * collecting again, e.g. on resume.
     */
    fun observeStatus(permission: AppPermission): Flow<PermissionStatus>

    /**
     * Suspends until the user answers the system prompt and returns the resulting status. Returns
     * [PermissionStatus.Granted] without prompting when already granted. Requests run one at a time.
     */
    suspend fun request(permission: AppPermission): PermissionStatus

    /** Launcher-host side: one-shot requests to launch the system prompt for. */
    val permissionRequests: Flow<AppPermission>

    /** Launcher-host side: the raw outcome of the prompt launched for [permission]. */
    fun onPermissionResult(permission: AppPermission, isGranted: Boolean, shouldShowRationale: Boolean)
}
