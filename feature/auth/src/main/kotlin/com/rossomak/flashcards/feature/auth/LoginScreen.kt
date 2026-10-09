package com.rossomak.flashcards.feature.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rossomak.flashcards.core.ui.R as CoreUiR
import com.rossomak.flashcards.core.ui.animation.SHARED_ELEMENT_DURATION_MS
import com.rossomak.flashcards.core.ui.animation.SharedElementKey
import com.rossomak.flashcards.core.ui.animation.sharedElementByKey
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledButton
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.navigation.observeAsEvents
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.brandColors
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.auth.LoginDestination.Main
import com.rossomak.flashcards.feature.auth.LoginDestination.Onboarding
import com.rossomak.flashcards.feature.auth.LoginFailureReason.NoCredentialAvailable
import com.rossomak.flashcards.feature.auth.LoginFailureReason.Unknown
import com.rossomak.flashcards.feature.auth.LoginMessage.SignInFailed
import com.rossomak.flashcards.feature.auth.LoginPhase.Idle
import com.rossomak.flashcards.feature.auth.LoginPhase.SignedIn
import com.rossomak.flashcards.feature.auth.LoginPhase.SigningIn
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Private rather than a size token: Splash, Welcome and Login each draw the logo at a different
// size, and that difference is what makes the shared-element hand-off between them visible.
private val LogoWidth = 200.dp
private val LogoHeight = LogoWidth * (1000f / 1800f)

private const val BUTTON_REVEAL_MS = 450

// Short enough to finish well inside the screen's own exit fade.
private const val BUTTON_HIDE_MS = 200

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
    onNavigateToMain: () -> Unit,
    onNavigateToOnboarding: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val signInLauncher = remember(context) { GoogleSignInLauncher(context) }

    observeAsEvents(viewModel.events) { destination ->
        when (destination) {
            Main -> onNavigateToMain()
            Onboarding -> onNavigateToOnboarding()
        }
    }

    val noCredentialMessage = stringResource(R.string.login_no_credential_error)
    val signInFailedMessage = stringResource(R.string.login_signin_error)
    val snackbarScope = rememberCoroutineScope()
    observeAsEvents(viewModel.messages) { message ->
        when (message) {
            is SignInFailed -> {
                val (text, duration) = when (message.reason) {
                    NoCredentialAvailable -> noCredentialMessage to SnackbarDuration.Long
                    Unknown -> signInFailedMessage to SnackbarDuration.Short
                }
                snackbarScope.launch { snackbarHostState.showSnackbar(message = text, duration = duration) }
            }
        }
    }

    LoginContent(
        modifier = modifier,
        state = state,
        snackbarHostState = snackbarHostState,
        onGoogleSignInClick = {
            viewModel.onGoogleSignInStarted()
            coroutineScope.launch {
                val idTokenResult = try {
                    signInLauncher.launch()
                } catch (cancellation: CancellationException) {
                    // The Activity was recreated while the picker was open: report it, or the
                    // surviving ViewModel stays "Signing in…" for good.
                    viewModel.onGoogleSignInInterrupted()
                    throw cancellation
                }
                viewModel.onGoogleSignInResult(idTokenResult)
            }
        },
    )
}

@Composable
private fun LoginContent(
    modifier: Modifier = Modifier,
    state: LoginScreenState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onGoogleSignInClick: () -> Unit,
) {
    // The button waits for the logo the splash screen hands over to land, then fades and rises in.
    // It stays at 1f while signing in, so that only changes the label and the enabled state. Once
    // signed in it fades and sinks out, so it never shows enabled again while the screen leaves.
    val buttonReveal = remember { Animatable(0f) }
    // Read once it flips, not on every frame of the reveal.
    val hasButtonStartedRevealing by remember { derivedStateOf { buttonReveal.value > 0f } }
    val isInspecting = LocalInspectionMode.current
    val isSignedIn = state.phase == SignedIn
    LaunchedEffect(isSignedIn) {
        when {
            // Also covers a recreated screen that is already signed in: the button starts hidden.
            isSignedIn -> buttonReveal.animateTo(0f, tween(durationMillis = BUTTON_HIDE_MS, easing = FastOutSlowInEasing))
            // A static @Preview renders the first frame only, which would hide the button.
            isInspecting -> buttonReveal.snapTo(1f)
            else -> {
                delay(SHARED_ELEMENT_DURATION_MS.milliseconds)
                buttonReveal.animateTo(1f, tween(durationMillis = BUTTON_REVEAL_MS, easing = FastOutSlowInEasing))
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.brandColors.screenGradient),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        // Offsets rather than weighted spacers, because only an offset can centre an element on a
        // fraction of the height: the logo on one third, the button on two thirds.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val buttonRiseDistance = MaterialTheme.spacing.medium
            Image(
                painter = painterResource(CoreUiR.drawable.flashcards_white),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = maxHeight / 3 - LogoHeight / 2)
                    .sharedElementByKey(SharedElementKey.APP_LOGO)
                    .size(width = LogoWidth, height = LogoHeight),
            )
            FlashcardsFilledButton(
                text = stringResource(
                    when (state.phase) {
                        Idle -> R.string.login_google_signin_button
                        SigningIn, SignedIn -> R.string.login_signing_in_label
                    },
                ),
                onClick = onGoogleSignInClick,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    // Normal size has the fixed height of buttonHeightNormal, which this centring relies on.
                    .offset(y = maxHeight * 2 / 3 - MaterialTheme.sizes.buttonHeightNormal / 2)
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.spacing.large)
                    .graphicsLayer {
                        alpha = buttonReveal.value
                        translationY = (1f - buttonReveal.value) * buttonRiseDistance.toPx()
                    },
                size = FlashcardsComponentSize.Normal,
                // Disabled until it starts to appear, so an invisible button can't be tapped.
                enabled = state.phase == Idle && hasButtonStartedRevealing,
                style = FlashcardsComponentStyle.OnGradient,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Composable
private fun LoginContentPreview() {
    FlashcardsTheme {
        LoginContent(state = LoginScreenState(), onGoogleSignInClick = {})
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Composable
private fun LoginContentSigningInPreview() {
    FlashcardsTheme {
        LoginContent(state = LoginScreenState(phase = SigningIn), onGoogleSignInClick = {})
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Composable
private fun LoginContentSignedInPreview() {
    FlashcardsTheme {
        LoginContent(state = LoginScreenState(phase = SignedIn), onGoogleSignInClick = {})
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 800, fontScale = 1.5f)
@Composable
private fun LoginContentLargeFontPreview() {
    FlashcardsTheme {
        LoginContent(state = LoginScreenState(), onGoogleSignInClick = {})
    }
}
