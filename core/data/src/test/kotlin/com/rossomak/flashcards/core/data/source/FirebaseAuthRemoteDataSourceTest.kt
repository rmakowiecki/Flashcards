package com.rossomak.flashcards.core.data.source

import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserInfo
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
        providerData: List<UserInfo> = emptyList(),
    ): FirebaseUser = mockk {
        every { this@mockk.uid } returns uid
        every { this@mockk.email } returns email
        every { this@mockk.displayName } returns displayName
        every { this@mockk.photoUrl } returns photoUri
        every { this@mockk.isAnonymous } returns isAnonymous
        every { this@mockk.providerData } returns providerData
    }

    private fun uriOf(value: String?): Uri? = value?.let { text ->
        val uri: Uri = mockk()
        every { uri.toString() } returns text
        uri
    }

    private fun googleProfile(
        email: String? = null,
        displayName: String? = null,
        photoUrl: String? = null,
    ): UserInfo = mockk {
        every { providerId } returns GoogleAuthProvider.PROVIDER_ID
        every { this@mockk.email } returns email
        every { this@mockk.displayName } returns displayName
        every { this@mockk.photoUrl } returns uriOf(photoUrl)
    }

    private fun otherProfile(): UserInfo = mockk {
        every { providerId } returns "firebase"
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
    fun `a photo url with a smaller size suffix is requested at the larger size`() {
        every { firebaseAuth.currentUser } returns firebaseUser(photoUri = uriOf("$PHOTO_BASE=s96-c"))

        createDataSource().getCurrentUser()?.photoUrl shouldBe "$PHOTO_BASE=s256-c"
        verify(exactly = 1) { firebaseAuth.currentUser }
    }

    @Test
    fun `a photo url with a larger size suffix is left unchanged`() {
        every { firebaseAuth.currentUser } returns firebaseUser(photoUri = uriOf("$PHOTO_BASE=s400-c"))

        createDataSource().getCurrentUser()?.photoUrl shouldBe "$PHOTO_BASE=s400-c"
        verify(exactly = 1) { firebaseAuth.currentUser }
    }

    @Test
    fun `a photo url without a size suffix is left unchanged`() {
        every { firebaseAuth.currentUser } returns firebaseUser(photoUri = uriOf("$PHOTO_BASE.jpg"))

        createDataSource().getCurrentUser()?.photoUrl shouldBe "$PHOTO_BASE.jpg"
        verify(exactly = 1) { firebaseAuth.currentUser }
    }

    @Test
    fun `a size suffix that is followed by a query is left unchanged`() {
        every { firebaseAuth.currentUser } returns firebaseUser(photoUri = uriOf("$PHOTO_BASE=s96-c?v=2"))

        createDataSource().getCurrentUser()?.photoUrl shouldBe "$PHOTO_BASE=s96-c?v=2"
        verify(exactly = 1) { firebaseAuth.currentUser }
    }

    @Test
    fun `a missing photo stays missing`() {
        every { firebaseAuth.currentUser } returns firebaseUser(photoUri = null)

        createDataSource().getCurrentUser()?.photoUrl shouldBe null
        verify(exactly = 1) { firebaseAuth.currentUser }
    }

    @Test
    fun `null profile fields fall back to the google provider entry`() {
        every { firebaseAuth.currentUser } returns firebaseUser(
            email = null,
            displayName = null,
            photoUri = null,
            providerData = listOf(
                otherProfile(),
                googleProfile(email = GOOGLE_EMAIL, displayName = GOOGLE_NAME, photoUrl = "$PHOTO_BASE=s96-c"),
            ),
        )

        val user = createDataSource().getCurrentUser()

        user?.email shouldBe GOOGLE_EMAIL
        user?.displayName shouldBe GOOGLE_NAME
        user?.photoUrl shouldBe "$PHOTO_BASE=s256-c"
        verify(exactly = 1) { firebaseAuth.currentUser }
    }

    @Test
    fun `blank profile fields fall back to the google provider entry`() {
        every { firebaseAuth.currentUser } returns firebaseUser(
            email = " ",
            displayName = "",
            photoUri = uriOf(""),
            providerData = listOf(
                googleProfile(email = GOOGLE_EMAIL, displayName = GOOGLE_NAME, photoUrl = "$PHOTO_BASE=s96-c"),
            ),
        )

        val user = createDataSource().getCurrentUser()

        user?.email shouldBe GOOGLE_EMAIL
        user?.displayName shouldBe GOOGLE_NAME
        user?.photoUrl shouldBe "$PHOTO_BASE=s256-c"
        verify(exactly = 1) { firebaseAuth.currentUser }
    }

    @Test
    fun `blank profile fields with no provider entry are missing`() {
        every { firebaseAuth.currentUser } returns firebaseUser(email = "", displayName = " ", photoUri = uriOf(" "))

        val user = createDataSource().getCurrentUser()

        user?.email shouldBe null
        user?.displayName shouldBe null
        user?.photoUrl shouldBe null
        verify(exactly = 1) { firebaseAuth.currentUser }
    }

    @Test
    fun `profile fields on the user win over the google provider entry`() {
        every { firebaseAuth.currentUser } returns firebaseUser(
            email = OWN_EMAIL,
            displayName = OWN_NAME,
            photoUri = uriOf("$OWN_PHOTO_BASE=s96-c"),
            providerData = listOf(
                googleProfile(email = GOOGLE_EMAIL, displayName = GOOGLE_NAME, photoUrl = "$PHOTO_BASE=s96-c"),
            ),
        )

        val user = createDataSource().getCurrentUser()

        user?.email shouldBe OWN_EMAIL
        user?.displayName shouldBe OWN_NAME
        user?.photoUrl shouldBe "$OWN_PHOTO_BASE=s256-c"
        verify(exactly = 1) { firebaseAuth.currentUser }
    }

    @Test
    fun `each profile field falls back on its own`() {
        every { firebaseAuth.currentUser } returns firebaseUser(
            email = OWN_EMAIL,
            displayName = " ",
            photoUri = null,
            providerData = listOf(
                googleProfile(email = GOOGLE_EMAIL, displayName = GOOGLE_NAME, photoUrl = "$PHOTO_BASE=s96-c"),
            ),
        )

        val user = createDataSource().getCurrentUser()

        user?.email shouldBe OWN_EMAIL
        user?.displayName shouldBe GOOGLE_NAME
        user?.photoUrl shouldBe "$PHOTO_BASE=s256-c"
        verify(exactly = 1) { firebaseAuth.currentUser }
    }

    @Test
    fun `blank fields on the google provider entry are missing`() {
        every { firebaseAuth.currentUser } returns firebaseUser(
            email = null,
            displayName = null,
            photoUri = null,
            providerData = listOf(otherProfile(), googleProfile(email = " ", displayName = "", photoUrl = " ")),
        )

        val user = createDataSource().getCurrentUser()

        user?.email shouldBe null
        user?.displayName shouldBe null
        user?.photoUrl shouldBe null
        verify(exactly = 1) { firebaseAuth.currentUser }
    }

    @Test
    fun `a provider list without a google entry gives no fallback`() {
        every { firebaseAuth.currentUser } returns firebaseUser(
            email = null,
            displayName = null,
            photoUri = null,
            providerData = listOf(otherProfile()),
        )

        val user = createDataSource().getCurrentUser()

        user?.email shouldBe null
        user?.displayName shouldBe null
        user?.photoUrl shouldBe null
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

    private companion object {
        const val PHOTO_BASE = "https://host/photo"
        const val OWN_PHOTO_BASE = "https://host/own"
        const val GOOGLE_EMAIL = "g@example.com"
        const val GOOGLE_NAME = "Gina"
        const val OWN_EMAIL = "own@example.com"
        const val OWN_NAME = "Olivia"
    }
}
