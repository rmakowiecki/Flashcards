package com.rossomak.flashcards.core.data.permission

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rossomak.flashcards.core.domain.model.AppPermission.RecordAudio
import com.rossomak.flashcards.core.domain.model.PermissionStatus
import com.rossomak.flashcards.core.domain.model.PermissionStatus.Denied
import com.rossomak.flashcards.core.domain.model.PermissionStatus.Granted
import com.rossomak.flashcards.core.domain.model.PermissionStatus.NotRequested
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

class DefaultPermissionGatewayTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(scope = dataStoreScope) {
            temporaryFolder.newFile("user_preferences.preferences_pb")
        }
    }

    private val lastDenialKey = stringPreferencesKey(DefaultPermissionGateway.LAST_DENIAL_KEY_PREFIX + "recordaudio")

    private var isRecordAudioGranted = false

    private val gateway by lazy {
        DefaultPermissionGateway(permissionChecker = { isRecordAudioGranted }, dataStore = dataStore)
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
    }

    private suspend fun storeLastDenial(value: String) {
        dataStore.edit { it[lastDenialKey] = value }
    }

    private suspend fun storedLastDenial(): String? = dataStore.data.first()[lastDenialKey]

    /** Runs [DefaultPermissionGateway.request], answers the launched prompt with the given raw result, and returns the classified status. */
    private suspend fun TestScope.requestAnswering(isGranted: Boolean, shouldShowRationale: Boolean): PermissionStatus {
        val result = async { gateway.request(RecordAudio) }
        gateway.permissionRequests.first() shouldBe RecordAudio
        runCurrent()
        gateway.onPermissionResult(RecordAudio, isGranted = isGranted, shouldShowRationale = shouldShowRationale)
        return result.await()
    }

    @Test
    fun `observeStatus emits NotRequested when not granted and no denial is stored`() = runTest {
        gateway.observeStatus(RecordAudio).first() shouldBe NotRequested
    }

    @Test
    fun `observeStatus emits Denied for a stored soft denial`() = runTest {
        storeLastDenial("Soft")

        gateway.observeStatus(RecordAudio).first() shouldBe Denied
    }

    @Test
    fun `observeStatus emits PermanentlyDenied for a stored permanent denial`() = runTest {
        storeLastDenial("Permanent")

        gateway.observeStatus(RecordAudio).first() shouldBe PermanentlyDenied
    }

    @Test
    fun `observeStatus emits Granted when the system reports the permission granted`() = runTest {
        isRecordAudioGranted = true

        gateway.observeStatus(RecordAudio).first() shouldBe Granted
    }

    @Test
    fun `reading Granted clears a stored permanent denial`() = runTest {
        storeLastDenial("Permanent")
        isRecordAudioGranted = true

        gateway.observeStatus(RecordAudio).first() shouldBe Granted
        isRecordAudioGranted = false

        storedLastDenial() shouldBe null
        gateway.observeStatus(RecordAudio).first() shouldBe NotRequested
    }

    @Test
    fun `a granted result returns Granted and clears the stored denial`() = runTest {
        storeLastDenial("Soft")

        val status = requestAnswering(isGranted = true, shouldShowRationale = false)
        isRecordAudioGranted = true

        status shouldBe Granted
        storedLastDenial() shouldBe null
        gateway.observeStatus(RecordAudio).first() shouldBe Granted
    }

    @Test
    fun `a denial with rationale returns Denied and stores a soft denial`() = runTest {
        val status = requestAnswering(isGranted = false, shouldShowRationale = true)

        status shouldBe Denied
        storedLastDenial() shouldBe "Soft"
        gateway.observeStatus(RecordAudio).first() shouldBe Denied
    }

    @Test
    fun `a denial without rationale after a soft denial returns PermanentlyDenied`() = runTest {
        storeLastDenial("Soft")

        val status = requestAnswering(isGranted = false, shouldShowRationale = false)

        status shouldBe PermanentlyDenied
        storedLastDenial() shouldBe "Permanent"
        gateway.observeStatus(RecordAudio).first() shouldBe PermanentlyDenied
    }

    @Test
    fun `a denial without rationale after a permanent denial stays PermanentlyDenied`() = runTest {
        storeLastDenial("Permanent")

        val status = requestAnswering(isGranted = false, shouldShowRationale = false)

        status shouldBe PermanentlyDenied
        storedLastDenial() shouldBe "Permanent"
    }

    @Test
    fun `a denial without rationale and no prior denial is a dismissed prompt and stays NotRequested`() = runTest {
        val status = requestAnswering(isGranted = false, shouldShowRationale = false)

        status shouldBe NotRequested
        storedLastDenial() shouldBe null
        gateway.observeStatus(RecordAudio).first() shouldBe NotRequested
    }

    @Test
    fun `request when already granted returns Granted without launching a prompt`() = runTest {
        isRecordAudioGranted = true

        gateway.request(RecordAudio) shouldBe Granted
        withTimeoutOrNull(NO_LAUNCH_TIMEOUT_MS) { gateway.permissionRequests.first() } shouldBe null
    }

    @Test
    fun `observeStatus re-emits after a request result`() = runTest {
        val emissions = async { gateway.observeStatus(RecordAudio).take(2).toList() }
        runCurrent()

        requestAnswering(isGranted = false, shouldShowRationale = true)

        emissions.await() shouldBe listOf(NotRequested, Denied)
    }

    private companion object {
        const val NO_LAUNCH_TIMEOUT_MS = 100L
    }
}
