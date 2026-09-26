package com.rossomak.flashcards.feature.study.voice

import com.rossomak.flashcards.core.domain.model.VoiceGradingEntitlementException
import com.rossomak.flashcards.feature.study.voice.VoiceAnswerFailureReason.GradingFailed.NoConnection
import com.rossomak.flashcards.feature.study.voice.VoiceAnswerFailureReason.GradingFailed.ServiceError
import io.kotest.matchers.shouldBe
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException
import org.junit.Test

class GradingFailureReasonTest {

    @Test
    fun `a bare IOException is NoConnection`() {
        IOException("connection reset").toGradingFailureReason() shouldBe NoConnection
    }

    @Test
    fun `a wrapper whose cause is an IOException is NoConnection`() {
        // How the streamed callable reports an offline device: its own INTERNAL exception, caused by the IOException.
        RuntimeException("INTERNAL", UnknownHostException("no network")).toGradingFailureReason() shouldBe NoConnection
    }

    @Test
    fun `a wrapper whose cause is a timed-out request is NoConnection`() {
        // How the streamed callable reports DEADLINE_EXCEEDED on the client: caused by an InterruptedIOException.
        RuntimeException("DEADLINE_EXCEEDED", InterruptedIOException("timeout")).toGradingFailureReason() shouldBe NoConnection
    }

    @Test
    fun `an IOException deeper in the cause chain is NoConnection`() {
        IllegalStateException("outer", RuntimeException("middle", IOException("inner"))).toGradingFailureReason() shouldBe NoConnection
    }

    @Test
    fun `a service-side failure without an IOException cause is ServiceError`() {
        // An HTTP 503 (UNAVAILABLE) or any other server status carries no IOException cause.
        RuntimeException("UNAVAILABLE").toGradingFailureReason() shouldBe ServiceError
    }

    @Test
    fun `an entitlement rejection is ServiceError`() {
        VoiceGradingEntitlementException(cause = RuntimeException("PERMISSION_DENIED")).toGradingFailureReason() shouldBe ServiceError
    }

    @Test
    fun `a generic exception is ServiceError`() {
        IllegalStateException("Graded event arrived before any transcript chunk").toGradingFailureReason() shouldBe ServiceError
    }

    @Test
    fun `a cause chain that loops back on itself terminates`() {
        val outer = RuntimeException("outer")
        val inner = RuntimeException("inner", outer)
        outer.initCause(inner)

        outer.toGradingFailureReason() shouldBe ServiceError
    }
}
