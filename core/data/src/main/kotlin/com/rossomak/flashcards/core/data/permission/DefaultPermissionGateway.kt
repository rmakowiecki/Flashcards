package com.rossomak.flashcards.core.data.permission

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.rossomak.flashcards.core.common.logd
import com.rossomak.flashcards.core.data.di.UserPreferencesDataStore
import com.rossomak.flashcards.core.domain.model.AppPermission
import com.rossomak.flashcards.core.domain.model.PermissionStatus
import com.rossomak.flashcards.core.domain.model.PermissionStatus.Denied
import com.rossomak.flashcards.core.domain.model.PermissionStatus.Granted
import com.rossomak.flashcards.core.domain.model.PermissionStatus.PermanentlyDenied
import com.rossomak.flashcards.core.domain.repository.PermissionGateway
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The OS only says whether a permission is granted, not whether the user still gets a prompt, so
 * this keeps one "permanently denied" flag per permission in the user preferences DataStore.
 *
 * A request result decides the flag: granted or refused with rationale `true` clears it (the system
 * will prompt again), refused with rationale `false` sets it. Reading the status outside a request
 * never asks for rationale, so it needs no Activity: granted, else the flag, else [Denied].
 *
 * Android 11+ reports a first prompt dismissed without an answer exactly like a permanent refusal,
 * so that dismissal sets the flag too. It heals on the next request: the system still shows its
 * prompt, and any answer other than a permanent refusal clears the flag.
 */
@Singleton
class DefaultPermissionGateway @Inject constructor(
    private val permissionChecker: PermissionChecker,
    @param:UserPreferencesDataStore private val dataStore: DataStore<Preferences>,
) : PermissionGateway {

    private val requestChannel = Channel<AppPermission>(Channel.BUFFERED)
    private val statusChanges = MutableSharedFlow<AppPermission>(extraBufferCapacity = 1)
    private val requestMutex = Mutex()

    @Volatile private var pendingResult: CompletableDeferred<RawResult>? = null

    override val permissionRequests: Flow<AppPermission> = requestChannel.receiveAsFlow()

    override fun observeStatus(permission: AppPermission): Flow<PermissionStatus> =
        merge(flowOf(Unit), statusChanges.filter { it == permission }.map { })
            .map { currentStatus(permission) }

    override suspend fun request(permission: AppPermission): PermissionStatus = requestMutex.withLock {
        if (permissionChecker.isGranted(permission)) {
            clearPermanentlyDenied(permission)
            return@withLock Granted
        }
        val deferred = CompletableDeferred<RawResult>().also { pendingResult = it }
        val rawResult = try {
            requestChannel.send(permission)
            deferred.await()
        } finally {
            if (pendingResult === deferred) pendingResult = null
        }
        val status = classify(permission, rawResult)
        logd { "Permission request for $permission resolved to $status" }
        statusChanges.tryEmit(permission)
        status
    }

    override fun onPermissionResult(permission: AppPermission, isGranted: Boolean, shouldShowRationale: Boolean) {
        // Null when the requester was cancelled (or the process was recreated mid-prompt): nothing waits.
        pendingResult?.complete(RawResult(isGranted = isGranted, shouldShowRationale = shouldShowRationale))
        pendingResult = null
    }

    private suspend fun currentStatus(permission: AppPermission): PermissionStatus {
        if (permissionChecker.isGranted(permission)) {
            clearPermanentlyDenied(permission)
            return Granted
        }
        return if (isPermanentlyDenied(permission)) PermanentlyDenied else Denied
    }

    private suspend fun classify(permission: AppPermission, rawResult: RawResult): PermissionStatus = when {
        rawResult.isGranted -> {
            clearPermanentlyDenied(permission)
            Granted
        }
        rawResult.shouldShowRationale -> {
            clearPermanentlyDenied(permission)
            Denied
        }
        else -> {
            dataStore.edit { it[permanentlyDeniedKey(permission)] = true }
            PermanentlyDenied
        }
    }

    private suspend fun isPermanentlyDenied(permission: AppPermission): Boolean =
        dataStore.data.first()[permanentlyDeniedKey(permission)] == true

    private suspend fun clearPermanentlyDenied(permission: AppPermission) {
        val key = permanentlyDeniedKey(permission)
        if (dataStore.data.first().contains(key)) dataStore.edit { it.remove(key) }
    }

    private fun permanentlyDeniedKey(permission: AppPermission): Preferences.Key<Boolean> =
        booleanPreferencesKey(PERMANENTLY_DENIED_KEY_PREFIX + permission.name.lowercase())

    private data class RawResult(val isGranted: Boolean, val shouldShowRationale: Boolean)

    companion object {
        const val PERMANENTLY_DENIED_KEY_PREFIX = "permission_permanently_denied_"
    }
}
