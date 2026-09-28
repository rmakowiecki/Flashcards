package com.rossomak.flashcards.core.data

import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.rossomak.flashcards.core.data.worker.SessionSubmissionDeliveryWorker
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class SessionSubmissionDrainSchedulerTest {

    private val workManager: WorkManager = mockk()

    @Before
    fun setUp() {
        // scheduleDrain logs via android.util.Log, unavailable outside instrumented/Robolectric
        // tests — stub it rather than pull in either just for this.
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun createScheduler(): SessionSubmissionDrainScheduler = SessionSubmissionDrainScheduler(workManager)

    @Test
    fun `scheduleDrain enqueues the drain worker as unique work with KEEP policy`() {
        val requestSlot = slot<OneTimeWorkRequest>()
        every { workManager.enqueueUniqueWork(any(), any(), capture(requestSlot)) } returns mockk()

        createScheduler().scheduleDrain()

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                SessionSubmissionDrainScheduler.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>(),
            )
        }
        requestSlot.captured.workSpec.workerClassName shouldBe SessionSubmissionDeliveryWorker::class.java.name
    }

    @Test
    fun `scheduleDrain requires a connected network so it never runs and fails offline`() {
        val requestSlot = slot<OneTimeWorkRequest>()
        every { workManager.enqueueUniqueWork(any(), any(), capture(requestSlot)) } returns mockk()

        createScheduler().scheduleDrain()

        requestSlot.captured.workSpec.constraints.requiredNetworkType shouldBe NetworkType.CONNECTED
    }

    @Test
    fun `scheduleDrain called twice is idempotent-safe to issue redundantly`() {
        every { workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>()) } returns mockk()

        val scheduler = createScheduler()
        scheduler.scheduleDrain()
        scheduler.scheduleDrain()

        verify(exactly = 2) { workManager.enqueueUniqueWork(any(), ExistingWorkPolicy.KEEP, any<OneTimeWorkRequest>()) }
    }

    @Test
    fun `scheduleDrainForFinishedSession enqueues with REPLACE and returns the enqueued request's id`() {
        val requestSlot = slot<OneTimeWorkRequest>()
        every { workManager.enqueueUniqueWork(any(), any(), capture(requestSlot)) } returns mockk()

        val requestId = createScheduler().scheduleDrainForFinishedSession()

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                SessionSubmissionDrainScheduler.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                any<OneTimeWorkRequest>(),
            )
        }
        requestId shouldBe requestSlot.captured.id
        requestSlot.captured.workSpec.workerClassName shouldBe SessionSubmissionDeliveryWorker::class.java.name
        requestSlot.captured.workSpec.constraints.requiredNetworkType shouldBe NetworkType.CONNECTED
    }
}
