package com.rossomak.flashcards.core.data.permission

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.rossomak.flashcards.core.domain.model.AppPermission.RecordAudio
import com.rossomak.flashcards.core.domain.model.PermissionStatus
import com.rossomak.flashcards.core.domain.model.PermissionStatus.Denied
import com.rossomak.flashcards.core.domain.model.PermissionStatus.Granted
import com.rossomak.flashcards.core.domain.model.PermissionStatus.PermanentlyDenied
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DefaultPermissionRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(scope = dataStoreScope) {
            temporaryFolder.newFile("user_preferences.preferences_pb")
        }
    }

    private val permanentlyDeniedKey = booleanPreferencesKey(DefaultPermissionRepository.PERMANENTLY_DENIED_KEY_PREFIX + "recordaudio")

    private var isRecordAudioGranted = false

    private val repository by lazy {
        DefaultPermissionRepository(permissionChecker = { isRecordAudioGranted }, dataStore = dataStore)
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
    }

    private suspend fun storePermanentlyDenied() {
        dataStore.edit { it[permanentlyDeniedKey] = true }
    }

    private suspend fun storedPermanentlyDenied(): Boolean? = dataStore.data.first()[permanentlyDeniedKey]

    /** Runs [DefaultPermissionRepository.request], answers the launched prompt with the given raw result, and returns the classified status. */
    private suspend fun TestScope.requestAnswering(isGranted: Boolean, shouldShowRationale: Boolean): PermissionStatus {
        val result = async { repository.request(RecordAudio) }
        repository.permissionRequests.first() shouldBe RecordAudio
        runCurrent()
        repository.onPermissionResult(RecordAudio, isGranted = isGranted, shouldShowRationale = shouldShowRationale)
        return result.await()
    }

    @Test
    fun `observeStatus emits Denied when not granted and no flag is stored`() = runTest {
        repository.observeStatus(RecordAudio).first() shouldBe Denied
    }

    @Test
    fun `observeStatus emits PermanentlyDenied when the flag is stored`() = runTest {
        storePermanentlyDenied()

        repository.observeStatus(RecordAudio).first() shouldBe PermanentlyDenied
    }

    @Test
    fun `observeStatus emits Granted when the system reports the permission granted`() = runTest {
        isRecordAudioGranted = true

        repository.observeStatus(RecordAudio).first() shouldBe Granted
    }

    @Test
    fun `reading Granted clears a stored flag`() = runTest {
        storePermanentlyDenied()
        isRecordAudioGranted = true

        repository.observeStatus(RecordAudio).first() shouldBe Granted
        isRecordAudioGranted = false

        storedPermanentlyDenied() shouldBe null
        repository.observeStatus(RecordAudio).first() shouldBe Denied
    }

    @Test
    fun `a granted result returns Granted and clears the flag`() = runTest {
        storePermanentlyDenied()

        val status = requestAnswering(isGranted = true, shouldShowRationale = false)
        isRecordAudioGranted = true

        status shouldBe Granted
        storedPermanentlyDenied() shouldBe null
        repository.observeStatus(RecordAudio).first() shouldBe Granted
    }

    @Test
    fun `a denial with rationale returns Denied without setting the flag`() = runTest {
        val status = requestAnswering(isGranted = false, shouldShowRationale = true)

        status shouldBe Denied
        storedPermanentlyDenied() shouldBe null
        repository.observeStatus(RecordAudio).first() shouldBe Denied
    }

    @Test
    fun `a denial with rationale clears a stale flag`() = runTest {
        storePermanentlyDenied()

        val status = requestAnswering(isGranted = false, shouldShowRationale = true)

        status shouldBe Denied
        storedPermanentlyDenied() shouldBe null
        repository.observeStatus(RecordAudio).first() shouldBe Denied
    }

    @Test
    fun `a denial without rationale returns PermanentlyDenied and sets the flag`() = runTest {
        val status = requestAnswering(isGranted = false, shouldShowRationale = false)

        status shouldBe PermanentlyDenied
        storedPermanentlyDenied() shouldBe true
        repository.observeStatus(RecordAudio).first() shouldBe PermanentlyDenied
    }

    @Test
    fun `a denial without rationale while flagged stays PermanentlyDenied`() = runTest {
        storePermanentlyDenied()

        val status = requestAnswering(isGranted = false, shouldShowRationale = false)

        status shouldBe PermanentlyDenied
        storedPermanentlyDenied() shouldBe true
    }

    @Test
    fun `request when already granted returns Granted without launching a prompt`() = runTest {
        isRecordAudioGranted = true

        repository.request(RecordAudio) shouldBe Granted
        withTimeoutOrNull(NO_LAUNCH_TIMEOUT_MS) { repository.permissionRequests.first() } shouldBe null
    }

    @Test
    fun `observeStatus re-emits after a request result`() = runTest {
        val emissions = async { repository.observeStatus(RecordAudio).take(2).toList() }
        runCurrent()

        requestAnswering(isGranted = false, shouldShowRationale = false)

        emissions.await() shouldBe listOf(Denied, PermanentlyDenied)
    }

    private companion object {
        const val NO_LAUNCH_TIMEOUT_MS = 100L
    }
}
