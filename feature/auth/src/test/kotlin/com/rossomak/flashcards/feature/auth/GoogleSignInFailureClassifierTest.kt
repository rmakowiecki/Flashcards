package com.rossomak.flashcards.feature.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import com.rossomak.flashcards.feature.auth.LoginFailureReason.NoCredentialAvailable
import com.rossomak.flashcards.feature.auth.LoginFailureReason.Unknown
import io.kotest.matchers.shouldBe
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Test

class GoogleSignInFailureClassifierTest {

    @Test
    fun `a missing credential is NoCredentialAvailable`() {
        NoCredentialException().toLoginFailureReason() shouldBe NoCredentialAvailable
    }

    @Test
    fun `any other Credential Manager failure is Unknown`() {
        GetCredentialUnknownException().toLoginFailureReason() shouldBe Unknown
    }

    @Test
    fun `a generic exception is Unknown`() {
        IllegalStateException(MISSING_CLIENT_ID_MESSAGE).toLoginFailureReason() shouldBe Unknown
    }

    @Test
    fun `a dismissed account picker is a cancellation`() {
        GetCredentialCancellationException().isGoogleSignInCancellation() shouldBe true
    }

    @Test
    fun `a cancelled coroutine is not a picker cancellation`() {
        CancellationException(ACTIVITY_RECREATED_MESSAGE).isGoogleSignInCancellation() shouldBe false
    }

    @Test
    fun `a cancelled coroutine is Unknown`() {
        CancellationException(ACTIVITY_RECREATED_MESSAGE).toLoginFailureReason() shouldBe Unknown
    }

    @Test
    fun `a missing credential is not a cancellation`() {
        NoCredentialException().isGoogleSignInCancellation() shouldBe false
    }

    @Test
    fun `a generic exception is not a cancellation`() {
        IllegalStateException(MISSING_CLIENT_ID_MESSAGE).isGoogleSignInCancellation() shouldBe false
    }

    private companion object {
        const val MISSING_CLIENT_ID_MESSAGE = "Missing GOOGLE_WEB_CLIENT_ID"
        const val ACTIVITY_RECREATED_MESSAGE = "Activity recreated"
    }
}
