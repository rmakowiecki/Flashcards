package com.rossomak.flashcards.feature.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rossomak.flashcards.core.domain.model.AuthProvider
import com.rossomak.flashcards.core.domain.model.AuthProvider.GitHub
import com.rossomak.flashcards.core.domain.model.AuthProvider.Google
import com.rossomak.flashcards.core.ui.R as CoreUiR
import com.rossomak.flashcards.core.ui.animation.SHARED_ELEMENT_DURATION_MS
import com.rossomak.flashcards.core.ui.animation.SharedElementKey
import com.rossomak.flashcards.core.ui.animation.sharedElementByKey
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledButton
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsOutlinedButton
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.navigation.observeAsEvents
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.brandColors
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.auth.LoginDestination.Main
import com.rossomak.flashcards.feature.auth.LoginDestination.Onboarding
import com.rossomak.flashcards.feature.auth.LoginFailureReason.AccountExistsWithDifferentProvider
import com.rossomak.flashcards.feature.auth.LoginFailureReason.NoConnection
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

private val LogoWidth = 200.dp

private const val BUTTON_REVEAL_MS = 450

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

    val noConnectionMessage = stringResource(R.string.login_no_connection_error)
    val noCredentialMessage = stringResource(R.string.login_no_credential_error)
    val signInFailedMessage = stringResource(R.string.login_signin_error)
    val accountExistsWithGoogleMessage = stringResource(R.string.login_account_exists_google_error)
    val accountExistsWithGitHubMessage = stringResource(R.string.login_account_exists_github_error)
    val snackbarScope = rememberCoroutineScope()
    observeAsEvents(viewModel.messages) { message ->
        when (message) {
            is SignInFailed -> {
                val (text, duration) = when (message.reason) {
                    NoConnection -> noConnectionMessage to SnackbarDuration.Short
                    NoCredentialAvailable -> noCredentialMessage to SnackbarDuration.Long
                    Unknown -> signInFailedMessage to SnackbarDuration.Short
                    is AccountExistsWithDifferentProvider -> when (message.reason.existingProvider) {
                        Google -> accountExistsWithGoogleMessage
                        GitHub -> accountExistsWithGitHubMessage
                    } to SnackbarDuration.Long
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
                    // The Activity was recreated while the picker was open: report it, or the surviving ViewModel stays "Signing in…" for good.
                    viewModel.onGoogleSignInInterrupted()
                    throw cancellation
                }
                viewModel.onGoogleSignInResult(idTokenResult)
            }
        },
        onGitHubSignInClick = viewModel::onGitHubSignInClick,
    )
}

@Composable
private fun LoginContent(
    modifier: Modifier = Modifier,
    state: LoginScreenState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onGoogleSignInClick: () -> Unit,
    onGitHubSignInClick: () -> Unit,
) {
    // The buttons wait for the logo the splash screen hands over to land, then fade and rise in.
    // They stay at 1f while signing in, so that only changes the labels and the enabled state. Once
    // signed in they fade and sink out, so they never show enabled again while the screen leaves.
    val buttonReveal = remember { Animatable(0f) }
    val hasButtonStartedRevealing by remember { derivedStateOf { buttonReveal.value > 0f } }
    val isInspecting = LocalInspectionMode.current
    val isSignedIn = state.phase is SignedIn
    LaunchedEffect(isSignedIn) {
        when {
            isSignedIn -> buttonReveal.animateTo(0f, tween(durationMillis = BUTTON_HIDE_MS, easing = FastOutSlowInEasing))
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val buttonRiseDistance = MaterialTheme.spacing.medium
            Image(
                painter = painterResource(CoreUiR.drawable.flashcards_white),
                contentDescription = null,
                modifier = Modifier
                    .sharedElementByKey(SharedElementKey.APP_LOGO)
                    .width(LogoWidth),
            )
            LoginButtons(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.spacing.large)
                    .graphicsLayer {
                        alpha = buttonReveal.value
                        translationY = (1f - buttonReveal.value) * buttonRiseDistance.toPx()
                    },
                phase = state.phase,
                // Disabled until they start to appear, so an invisible button can't be tapped.
                enabled = state.phase == Idle && hasButtonStartedRevealing,
                onGoogleSignInClick = onGoogleSignInClick,
                onGitHubSignInClick = onGitHubSignInClick,
            )
        }
    }
}

/** One button per sign-in provider, revealed and hidden together by [modifier]. */
@Composable
private fun LoginButtons(
    modifier: Modifier = Modifier,
    phase: LoginPhase,
    enabled: Boolean,
    onGoogleSignInClick: () -> Unit,
    onGitHubSignInClick: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        FlashcardsFilledButton(
            text = stringResource(
                if (phase.isSigningInWith(Google)) R.string.login_signing_in_label else R.string.login_google_signin_button,
            ),
            onClick = onGoogleSignInClick,
            modifier = Modifier.fillMaxWidth(),
            size = FlashcardsComponentSize.Normal,
            enabled = enabled,
            style = FlashcardsComponentStyle.OnGradient,
        )
        FlashcardsOutlinedButton(
            text = stringResource(
                if (phase.isSigningInWith(GitHub)) R.string.login_signing_in_label else R.string.login_github_signin_button,
            ),
            onClick = onGitHubSignInClick,
            modifier = Modifier.fillMaxWidth(),
            size = FlashcardsComponentSize.Normal,
            enabled = enabled,
            icon = ImageVector.vectorResource(R.drawable.ic_github_mark),
            style = FlashcardsComponentStyle.OnGradient,
        )
    }
}

/** Whether [provider]'s button shows the signing-in label: its sign-in is running, or it succeeded and the screen is leaving. */
private fun LoginPhase.isSigningInWith(provider: AuthProvider): Boolean = when (this) {
    Idle -> false
    is SigningIn -> this.provider == provider
    is SignedIn -> this.provider == provider
}

@Preview
@Composable
private fun LoginContentPreview() {
    FlashcardsTheme {
        LoginContent(state = LoginScreenState(), onGoogleSignInClick = {}, onGitHubSignInClick = {})
    }
}

@Preview
@Composable
private fun LoginContentSigningInPreview() {
    FlashcardsTheme {
        LoginContent(state = LoginScreenState(phase = SigningIn(Google)), onGoogleSignInClick = {}, onGitHubSignInClick = {})
    }
}

@Preview
@Composable
private fun LoginContentSigningInWithGitHubPreview() {
    FlashcardsTheme {
        LoginContent(state = LoginScreenState(phase = SigningIn(GitHub)), onGoogleSignInClick = {}, onGitHubSignInClick = {})
    }
}
