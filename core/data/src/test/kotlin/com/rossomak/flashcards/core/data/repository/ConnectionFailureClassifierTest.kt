package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.domain.model.GradingFailureReason.NoConnection
import com.rossomak.flashcards.core.domain.model.GradingFailureReason.ServiceError
import com.rossomak.flashcards.core.domain.model.VoiceGradingEntitlementException
import io.kotest.matchers.shouldBe
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException
import org.junit.Test

class ConnectionFailureClassifierTest {

    @Test
    fun `a bare IOException is NoConnection`() {
        IOException("connection reset").toFailureReason(NoConnection, ServiceError) shouldBe NoConnection
    }

    @Test
    fun `a wrapper whose cause is an IOException is NoConnection`() {
        // How the streamed callable reports an offline device: its own INTERNAL exception, caused by the IOException.
        RuntimeException("INTERNAL", UnknownHostException("no network")).toFailureReason(NoConnection, ServiceError) shouldBe NoConnection
    }

    @Test
    fun `a wrapper whose cause is a timed-out request is NoConnection`() {
        // How the streamed callable reports DEADLINE_EXCEEDED on the client: caused by an InterruptedIOException.
        RuntimeException("DEADLINE_EXCEEDED", InterruptedIOException("timeout")).toFailureReason(NoConnection, ServiceError) shouldBe NoConnection
    }

    @Test
    fun `an IOException deeper in the cause chain is NoConnection`() {
        IllegalStateException("outer", RuntimeException("middle", IOException("inner"))).toFailureReason(NoConnection, ServiceError) shouldBe NoConnection
    }

    @Test
    fun `a service-side failure without an IOException cause is ServiceError`() {
        // An HTTP 503 (UNAVAILABLE) or any other server status carries no IOException cause.
        RuntimeException("UNAVAILABLE").toFailureReason(NoConnection, ServiceError) shouldBe ServiceError
    }

    @Test
    fun `an entitlement rejection is ServiceError`() {
        VoiceGradingEntitlementException(cause = RuntimeException("PERMISSION_DENIED")).toFailureReason(NoConnection, ServiceError) shouldBe ServiceError
    }

    @Test
    fun `a generic exception is ServiceError`() {
        IllegalStateException("Graded event arrived before any transcript chunk").toFailureReason(NoConnection, ServiceError) shouldBe ServiceError
    }

    @Test
    fun `a cause chain that loops back on itself terminates`() {
        val outer = RuntimeException("outer")
        val inner = RuntimeException("inner", outer)
        outer.initCause(inner)

        outer.toFailureReason(NoConnection, ServiceError) shouldBe ServiceError
    }
}
