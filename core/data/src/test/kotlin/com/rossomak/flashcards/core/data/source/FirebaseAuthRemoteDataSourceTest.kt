package com.rossomak.flashcards.core.data.source

import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
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
    fun `signInWithGoogleIdToken links when current user is an anonymous guest`() = runTest {
        val idToken = "id-token"
        val credential: AuthCredential = mockk()
        val guest = firebaseUser(uid = "guest-uid", isAnonymous = true)
        val linkedUser = firebaseUser(uid = "guest-uid", isAnonymous = false)
        val authResult: AuthResult = mockk { every { user } returns linkedUser }
        mockkStatic(GoogleAuthProvider::class)
        every { GoogleAuthProvider.getCredential(idToken, null) } returns credential
        every { firebaseAuth.currentUser } returns guest
        every { guest.linkWithCredential(credential) } returns Tasks.forResult(authResult)

        val result = createDataSource().signInWithGoogleIdToken(idToken)

        result.isSuccess shouldBe true
        result.getOrThrow().uid shouldBe "guest-uid"
        verify(exactly = 1) { guest.linkWithCredential(credential) }
        verify(exactly = 0) { firebaseAuth.signInWithCredential(any()) }
    }

    @Test
    fun `signInWithGoogleIdToken falls back to sign-in when the guest link collides with an existing user`() =
        runTest {
            val idToken = "id-token"
            val credential: AuthCredential = mockk()
            val guest = firebaseUser(uid = "guest-uid", isAnonymous = true)
            val existingUser = firebaseUser(uid = "existing-uid", isAnonymous = false)
            val authResult: AuthResult = mockk { every { user } returns existingUser }
            mockkStatic(GoogleAuthProvider::class)
            every { GoogleAuthProvider.getCredential(idToken, null) } returns credential
            every { firebaseAuth.currentUser } returns guest
            every { guest.linkWithCredential(credential) } returns
                Tasks.forException(mockk<FirebaseAuthUserCollisionException>(relaxed = true))
            every { firebaseAuth.signInWithCredential(credential) } returns Tasks.forResult(authResult)

            val result = createDataSource().signInWithGoogleIdToken(idToken)

            result.isSuccess shouldBe true
            result.getOrThrow().uid shouldBe "existing-uid"
            verify(exactly = 1) { guest.linkWithCredential(credential) }
            verify(exactly = 1) { firebaseAuth.signInWithCredential(credential) }
        }

    @Test
    fun `signInAnonymously returns mapped user on success`() = runTest {
        val authResult: AuthResult = mockk { every { user } returns firebaseUser(isAnonymous = true) }
        every { firebaseAuth.signInAnonymously() } returns Tasks.forResult(authResult)

        val result = createDataSource().signInAnonymously()

        result.isSuccess shouldBe true
        result.getOrThrow().isAnonymous shouldBe true
        verify(exactly = 1) { firebaseAuth.signInAnonymously() }
    }

    @Test
    fun `signInAnonymously fails when firebase returns a null user`() = runTest {
        val authResult: AuthResult = mockk { every { user } returns null }
        every { firebaseAuth.signInAnonymously() } returns Tasks.forResult(authResult)

        val result = createDataSource().signInAnonymously()

        result.isFailure shouldBe true
        (result.exceptionOrNull() is IllegalStateException) shouldBe true
        verify(exactly = 1) { firebaseAuth.signInAnonymously() }
    }

    @Test
    fun `signInAnonymously wraps sign-in failure in failure result`() = runTest {
        val error = IllegalStateException("anonymous sign-in failed")
        every { firebaseAuth.signInAnonymously() } returns Tasks.forException(error)

        val result = createDataSource().signInAnonymously()

        result.isFailure shouldBe true
        result.exceptionOrNull() shouldBe error
        verify(exactly = 1) { firebaseAuth.signInAnonymously() }
    }

    @Test
    fun `signOut delegates to firebase auth`() {
        createDataSource().signOut()

        verify(exactly = 1) { firebaseAuth.signOut() }
    }
}
