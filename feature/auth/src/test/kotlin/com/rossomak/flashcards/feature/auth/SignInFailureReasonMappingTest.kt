package com.rossomak.flashcards.feature.auth

import com.rossomak.flashcards.core.domain.model.AuthProvider
import com.rossomak.flashcards.core.domain.model.AuthProvider.GitHub
import com.rossomak.flashcards.core.domain.model.AuthProvider.Google
import com.rossomak.flashcards.core.domain.model.SignInFailureReason
import com.rossomak.flashcards.feature.auth.LoginFailureReason.AccountExistsWithDifferentProvider
import com.rossomak.flashcards.feature.auth.LoginFailureReason.NoConnection
import com.rossomak.flashcards.feature.auth.LoginFailureReason.Unknown
import io.kotest.matchers.shouldBe
import org.junit.Test

class SignInFailureReasonMappingTest {

    @Test
    fun `offline gives NoConnection for every reason and provider`() {
        allReasons.forEach { reason ->
            AuthProvider.entries.forEach { provider ->
                reason.toLoginFailureReason(isInternetAvailable = false, attemptedProvider = provider) shouldBe NoConnection
            }
        }
    }

    @Test
    fun `online NoConnection maps to NoConnection`() {
        SignInFailureReason.NoConnection.toLoginFailureReason(isInternetAvailable = true, attemptedProvider = Google) shouldBe
            NoConnection
    }

    @Test
    fun `online Unknown maps to Unknown`() {
        SignInFailureReason.Unknown.toLoginFailureReason(isInternetAvailable = true, attemptedProvider = Google) shouldBe
            Unknown
    }

    @Test
    fun `an email clash on a GitHub attempt points to Google`() {
        SignInFailureReason.AccountExistsWithDifferentProvider
            .toLoginFailureReason(isInternetAvailable = true, attemptedProvider = GitHub) shouldBe
            AccountExistsWithDifferentProvider(existingProvider = Google)
    }

    @Test
    fun `an email clash on a Google attempt points to GitHub`() {
        SignInFailureReason.AccountExistsWithDifferentProvider
            .toLoginFailureReason(isInternetAvailable = true, attemptedProvider = Google) shouldBe
            AccountExistsWithDifferentProvider(existingProvider = GitHub)
    }

    private companion object {
        val allReasons = listOf(
            SignInFailureReason.NoConnection,
            SignInFailureReason.AccountExistsWithDifferentProvider,
            SignInFailureReason.Unknown,
        )
    }
}
