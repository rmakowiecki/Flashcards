package com.rossomak.flashcards.core.data.worker

import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.FirebaseFunctionsException.Code
import com.rossomak.flashcards.core.data.worker.SessionDeliveryFailure.Permanent
import com.rossomak.flashcards.core.data.worker.SessionDeliveryFailure.Transient
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import org.junit.Test

class SessionDeliveryFailureTest {

    private fun functionsException(code: Code): FirebaseFunctionsException {
        val exception: FirebaseFunctionsException = mockk()
        every { exception.code } returns code
        return exception
    }

    @Test
    fun `a rejection of the session itself is permanent`() {
        listOf(Code.INVALID_ARGUMENT, Code.FAILED_PRECONDITION, Code.PERMISSION_DENIED).forEach { code ->
            classifySessionDeliveryFailure(functionsException(code)) shouldBe Permanent
        }
    }

    @Test
    fun `every other Functions code is transient`() {
        listOf(
            Code.UNAVAILABLE,
            Code.DEADLINE_EXCEEDED,
            Code.INTERNAL,
            Code.UNAUTHENTICATED,
            Code.RESOURCE_EXHAUSTED,
            Code.UNKNOWN,
            Code.CANCELLED,
            Code.NOT_FOUND,
            Code.ALREADY_EXISTS,
            Code.ABORTED,
            Code.OUT_OF_RANGE,
            Code.UNIMPLEMENTED,
            Code.DATA_LOSS,
        ).forEach { code ->
            classifySessionDeliveryFailure(functionsException(code)) shouldBe Transient
        }
    }

    @Test
    fun `a non-Functions exception is transient`() {
        classifySessionDeliveryFailure(IOException("offline")) shouldBe Transient
        classifySessionDeliveryFailure(IllegalStateException("unexpected")) shouldBe Transient
    }
}
