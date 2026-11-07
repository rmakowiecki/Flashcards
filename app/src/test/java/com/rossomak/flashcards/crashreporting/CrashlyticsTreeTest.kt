package com.rossomak.flashcards.crashreporting

import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class CrashlyticsTreeTest {

    private val crashReporter: CrashReporter = mockk(relaxed = true)

    @Before
    fun setUp() {
        Timber.plant(CrashlyticsTree(crashReporter))
    }

    @After
    fun tearDown() {
        Timber.uprootAll()
    }

    @Test
    fun `error with throwable records the exception after its breadcrumb`() {
        val failure = IllegalStateException(FAILURE_MESSAGE)

        Timber.e(failure, LOG_MESSAGE)

        verifyOrder {
            crashReporter.log(ERROR_BREADCRUMB)
            crashReporter.recordException(failure)
        }
    }

    @Test
    fun `assert with throwable records the exception after its breadcrumb`() {
        val failure = IllegalStateException(FAILURE_MESSAGE)

        Timber.wtf(failure, LOG_MESSAGE)

        verifyOrder {
            crashReporter.log("A: $LOG_MESSAGE\njava.lang.IllegalStateException: $FAILURE_MESSAGE")
            crashReporter.recordException(failure)
        }
    }

    @Test
    fun `error without throwable leaves a breadcrumb and records no exception`() {
        Timber.e(LOG_MESSAGE)

        verify { crashReporter.log("E: $LOG_MESSAGE") }
        verify(exactly = 0) { crashReporter.recordException(any()) }
    }

    @Test
    fun `warning with throwable leaves a breadcrumb and records no exception`() {
        val failure = IllegalStateException(FAILURE_MESSAGE)

        Timber.w(failure, LOG_MESSAGE)

        verify { crashReporter.log("W: $LOG_MESSAGE\njava.lang.IllegalStateException: $FAILURE_MESSAGE") }
        verify(exactly = 0) { crashReporter.recordException(any()) }
    }

    @Test
    fun `info line leaves a breadcrumb`() {
        Timber.i(LOG_MESSAGE)

        verify { crashReporter.log("I: $LOG_MESSAGE") }
    }

    @Test
    fun `tagged line keeps its tag in the breadcrumb`() {
        Timber.tag(TAG).i(LOG_MESSAGE)

        verify { crashReporter.log("I/$TAG: $LOG_MESSAGE") }
    }

    @Test
    fun `debug and verbose lines are dropped`() {
        Timber.d(LOG_MESSAGE)
        Timber.v(LOG_MESSAGE)

        verify(exactly = 0) { crashReporter.log(any()) }
        verify(exactly = 0) { crashReporter.recordException(any()) }
    }

    @Test
    fun `breadcrumb drops the stack frames Timber appends`() {
        val failure = IllegalStateException(FAILURE_MESSAGE, RuntimeException(CAUSE_MESSAGE))

        Timber.e(failure, LOG_MESSAGE)

        verify(exactly = 1) { crashReporter.log(any()) }
        verifyOrder {
            crashReporter.log(ERROR_BREADCRUMB)
            crashReporter.recordException(failure)
        }
    }

    private companion object {
        const val LOG_MESSAGE = "Failed to submit response for card card-1"
        const val FAILURE_MESSAGE = "network down"
        const val CAUSE_MESSAGE = "socket closed"
        const val TAG = "Submission"
        const val ERROR_BREADCRUMB = "E: $LOG_MESSAGE\njava.lang.IllegalStateException: $FAILURE_MESSAGE"
    }
}
