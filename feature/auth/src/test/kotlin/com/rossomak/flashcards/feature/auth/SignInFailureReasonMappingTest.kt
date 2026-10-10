package com.rossomak.flashcards.feature.auth

import com.rossomak.flashcards.core.domain.model.SignInFailureReason
import com.rossomak.flashcards.feature.auth.LoginFailureReason.NoConnection
import com.rossomak.flashcards.feature.auth.LoginFailureReason.Unknown
import io.kotest.matchers.shouldBe
import org.junit.Test

class SignInFailureReasonMappingTest {

    @Test
    fun `offline gives NoConnection for every reason`() {
        allReasons.forEach { reason ->
            reason.toLoginFailureReason(isInternetAvailable = false) shouldBe NoConnection
        }
    }

    @Test
    fun `online NoConnection maps to NoConnection`() {
        SignInFailureReason.NoConnection.toLoginFailureReason(isInternetAvailable = true) shouldBe NoConnection
    }

    @Test
    fun `online Unknown maps to Unknown`() {
        SignInFailureReason.Unknown.toLoginFailureReason(isInternetAvailable = true) shouldBe Unknown
    }

    private companion object {
        val allReasons = listOf(SignInFailureReason.NoConnection, SignInFailureReason.Unknown)
    }
}
