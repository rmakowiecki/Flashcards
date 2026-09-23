package com.rossomak.flashcards.core.data.permission

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rossomak.flashcards.core.common.logd
import com.rossomak.flashcards.core.data.di.UserPreferencesDataStore
import com.rossomak.flashcards.core.domain.model.AppPermission
import com.rossomak.flashcards.core.domain.model.PermissionStatus
import com.rossomak.flashcards.core.domain.model.PermissionStatus.Denied
import com.rossomak.flashcards.core.domain.model.PermissionStatus.Granted
import com.rossomak.flashcards.core.domain.model.PermissionStatus.NotRequested
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
 * this remembers each permission's last denial in the user preferences DataStore. A denial with
 * rationale `true` is soft; a later denial with rationale `false` means the system stopped
 * prompting. A first denial with rationale `false` is how Android 11+ reports a prompt dismissed
 * without an answer, so it is not treated as permanent.
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
            clearLastDenial(permission)
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
            clearLastDenial(permission)
            return Granted
        }
        return when (readLastDenial(permission)) {
            null -> NotRequested
            LastDenial.Soft -> Denied
            LastDenial.Permanent -> PermanentlyDenied
        }
    }

    private suspend fun classify(permission: AppPermission, rawResult: RawResult): PermissionStatus = when {
        rawResult.isGranted -> {
            clearLastDenial(permission)
            Granted
        }
        rawResult.shouldShowRationale -> {
            writeLastDenial(permission, LastDenial.Soft)
            Denied
        }
        readLastDenial(permission) != null -> {
            writeLastDenial(permission, LastDenial.Permanent)
            PermanentlyDenied
        }
        else -> NotRequested
    }

    private suspend fun readLastDenial(permission: AppPermission): LastDenial? =
        dataStore.data.first()[lastDenialKey(permission)]
            ?.let { stored -> LastDenial.entries.firstOrNull { it.name == stored } }

    private suspend fun writeLastDenial(permission: AppPermission, lastDenial: LastDenial) {
        dataStore.edit { it[lastDenialKey(permission)] = lastDenial.name }
    }

    private suspend fun clearLastDenial(permission: AppPermission) {
        val key = lastDenialKey(permission)
        if (dataStore.data.first().contains(key)) dataStore.edit { it.remove(key) }
    }

    private fun lastDenialKey(permission: AppPermission): Preferences.Key<String> =
        stringPreferencesKey(LAST_DENIAL_KEY_PREFIX + permission.name.lowercase())

    private enum class LastDenial { Soft, Permanent }

    private data class RawResult(val isGranted: Boolean, val shouldShowRationale: Boolean)

    companion object {
        const val LAST_DENIAL_KEY_PREFIX = "permission_last_denial_"
    }
}
