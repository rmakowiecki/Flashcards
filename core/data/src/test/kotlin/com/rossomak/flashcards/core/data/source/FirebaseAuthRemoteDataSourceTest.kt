package com.rossomak.flashcards.core.data.source

import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseAuthRemoteDataSourceTest {

    private val firebaseAuth: FirebaseAuth = mockk(relaxed = true)

    private fun createDataSource(): FirebaseAuthRemoteDataSource = FirebaseAuthRemoteDataSource(firebaseAuth)

    private fun firebaseUser(
        uid: String = "uid-1",
        email: String? = "user@example.com",
        displayName: String? = "Alex",
        photoUri: Uri? = null,
        isAnonymous: Boolean = false,
    ): FirebaseUser = mockk {
        every { this@mockk.uid } returns uid
        every { this@mockk.email } returns email
        every { this@mockk.displayName } returns displayName
        every { this@mockk.photoUrl } returns photoUri
        every { this@mockk.isAnonymous } returns isAnonymous
    }

    @After
    fun tearDown() {
        unmockkStatic(GoogleAuthProvider::class)
    }

    @Test
    fun `getCurrentUser maps the firebase user including photo url`() {
        val photoUri: Uri = mockk()
        every { photoUri.toString() } returns "http://photo"
        every { firebaseAuth.currentUser } returns firebaseUser(photoUri = photoUri)

        val user = createDataSource().getCurrentUser()

        user?.uid shouldBe "uid-1"
        user?.email shouldBe "user@example.com"
        user?.displayName shouldBe "Alex"
        user?.photoUrl shouldBe "http://photo"
        verify(exactly = 1) { firebaseAuth.currentUser }
    }

    @Test
    fun `getCurrentUser returns null when there is no signed in user`() {
        every { firebaseAuth.currentUser } returns null

        val user = createDataSource().getCurrentUser()

        user shouldBe null
        verify(exactly = 1) { firebaseAuth.currentUser }
    }

    @Test
    fun `signInWithGoogleIdToken returns mapped user on success`() = runTest {
        val idToken = "id-token"
        val credential: AuthCredential = mockk()
        val authResult: AuthResult = mockk { every { user } returns firebaseUser() }
        mockkStatic(GoogleAuthProvider::class)
        every { GoogleAuthProvider.getCredential(idToken, null) } returns credential
        every { firebaseAuth.signInWithCredential(credential) } returns Tasks.forResult(authResult)

        val result = createDataSource().signInWithGoogleIdToken(idToken)

        result.isSuccess shouldBe true
        result.getOrThrow().uid shouldBe "uid-1"
        verify(exactly = 1) { firebaseAuth.signInWithCredential(credential) }
    }

    @Test
    fun `signInWithGoogleIdToken fails when firebase returns a null user`() = runTest {
        val idToken = "id-token"
        val credential: AuthCredential = mockk()
        val authResult: AuthResult = mockk { every { user } returns null }
        mockkStatic(GoogleAuthProvider::class)
        every { GoogleAuthProvider.getCredential(idToken, null) } returns credential
        every { firebaseAuth.signInWithCredential(credential) } returns Tasks.forResult(authResult)

        val result = createDataSource().signInWithGoogleIdToken(idToken)

        result.isFailure shouldBe true
        (result.exceptionOrNull() is IllegalStateException) shouldBe true
        verify(exactly = 1) { firebaseAuth.signInWithCredential(credential) }
    }

    @Test
    fun `signInWithGoogleIdToken wraps sign-in failure in failure result`() = runTest {
        val idToken = "id-token"
        val credential: AuthCredential = mockk()
        val error = IllegalStateException("sign-in failed")
        mockkStatic(GoogleAuthProvider::class)
        every { GoogleAuthProvider.getCredential(idToken, null) } returns credential
        every { firebaseAuth.signInWithCredential(credential) } returns Tasks.forException(error)

        val result = createDataSource().signInWithGoogleIdToken(idToken)

        result.isFailure shouldBe true
        result.exceptionOrNull() shouldBe error
        verify(exactly = 1) { firebaseAuth.signInWithCredential(credential) }
    }

    @Test
    fun `signOut delegates to firebase auth`() {
        createDataSource().signOut()

        verify(exactly = 1) { firebaseAuth.signOut() }
    }
}
