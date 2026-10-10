package com.rossomak.flashcards.feature.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import com.rossomak.flashcards.feature.auth.LoginFailureReason.NoConnection
import com.rossomak.flashcards.feature.auth.LoginFailureReason.NoCredentialAvailable
import com.rossomak.flashcards.feature.auth.LoginFailureReason.Unknown
import io.kotest.matchers.shouldBe
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Test

class GoogleSignInFailureClassifierTest {

    @Test
    fun `offline, a missing credential is NoConnection`() {
        NoCredentialException().toLoginFailureReason(isInternetAvailable = false) shouldBe NoConnection
    }

    @Test
    fun `offline, a generic exception is NoConnection`() {
        IllegalStateException(MISSING_CLIENT_ID_MESSAGE).toLoginFailureReason(isInternetAvailable = false) shouldBe
            NoConnection
    }

    @Test
    fun `offline, an IOException is NoConnection`() {
        IOException(TIMEOUT_MESSAGE).toLoginFailureReason(isInternetAvailable = false) shouldBe NoConnection
    }

    @Test
    fun `online, an IOException is NoConnection`() {
        IOException(TIMEOUT_MESSAGE).toLoginFailureReason(isInternetAvailable = true) shouldBe NoConnection
    }

    @Test
    fun `online, an IOException anywhere in the cause chain is NoConnection`() {
        IllegalStateException(IOException(TIMEOUT_MESSAGE)).toLoginFailureReason(isInternetAvailable = true) shouldBe
            NoConnection
    }

    @Test
    fun `online, a missing credential is NoCredentialAvailable`() {
        NoCredentialException().toLoginFailureReason(isInternetAvailable = true) shouldBe NoCredentialAvailable
    }

    @Test
    fun `online, any other Credential Manager failure is Unknown`() {
        GetCredentialUnknownException().toLoginFailureReason(isInternetAvailable = true) shouldBe Unknown
    }

    @Test
    fun `online, a generic exception is Unknown`() {
        IllegalStateException(MISSING_CLIENT_ID_MESSAGE).toLoginFailureReason(isInternetAvailable = true) shouldBe
            Unknown
    }

    @Test
    fun `online, a cancelled coroutine is Unknown`() {
        CancellationException(ACTIVITY_RECREATED_MESSAGE).toLoginFailureReason(isInternetAvailable = true) shouldBe
            Unknown
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
        const val TIMEOUT_MESSAGE = "timeout"
    }
}
