package com.rossomak.flashcards.feature.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rossomak.flashcards.core.domain.model.PermissionStatus
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.model.VoiceCaptureFailureReason.AudioRecordInitFailed
import com.rossomak.flashcards.core.domain.model.VoiceCaptureFailureReason.BluetoothMicUnavailable
import com.rossomak.flashcards.core.domain.model.VoiceCaptureFailureReason.CaptureLoopError
import com.rossomak.flashcards.core.domain.model.VoiceCaptureFailureReason.CaptureNotRoutedToBluetooth
import com.rossomak.flashcards.core.domain.model.VoiceCaptureFailureReason.PermissionMissing
import com.rossomak.flashcards.core.domain.model.VoiceDemoFailureReason
import com.rossomak.flashcards.core.domain.model.VoiceDemoFailureReason.CaptureError
import com.rossomak.flashcards.core.domain.model.VoiceDemoFailureReason.RouteUnavailable
import com.rossomak.flashcards.core.domain.model.VoiceDemoState
import com.rossomak.flashcards.core.ui.R as CoreUiR
import com.rossomak.flashcards.core.ui.animation.SHARED_ELEMENT_DURATION_MS
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledButton
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsTextButton
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.composables.progress.FlashcardsSegmentedProgressBar
import com.rossomak.flashcards.core.ui.composables.voice.FlashcardsVoiceCaptureIndicatorDefaults
import com.rossomak.flashcards.core.ui.navigation.observeAsEvents
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.brandColors
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.onboarding.OnboardingMessage.MicPermissionStillDenied
import com.rossomak.flashcards.feature.onboarding.OnboardingMessage.NothingCaptured
import com.rossomak.flashcards.feature.onboarding.OnboardingMessage.VoiceDemoFailed
import com.rossomak.flashcards.feature.onboarding.step.AllSetStep
import com.rossomak.flashcards.feature.onboarding.step.DailyGoalStep
import com.rossomak.flashcards.feature.onboarding.step.FavoritesStep
import com.rossomak.flashcards.feature.onboarding.step.MasteryStep
import com.rossomak.flashcards.feature.onboarding.step.SessionModesStep
import com.rossomak.flashcards.feature.onboarding.step.StructureStep
import com.rossomak.flashcards.feature.onboarding.step.VoicePrivacyStep
import com.rossomak.flashcards.feature.onboarding.step.WelcomeStep
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Fade-and-rise of the cover's copy, once the shared logo has landed. */
private const val COPY_REVEAL_MS = 450

/** Fade-in of Skip and the CTA, the last thing to arrive. */
private const val CHROME_REVEAL_MS = 300

/**
 * Every callback [OnboardingContent] and [OnboardingStepPage] hand down into a step — bundled
 * rather than passed as individual lambdas so growing the flow by another step's callback doesn't
 * grow either function's own parameter list.
 */
private data class OnboardingActions(
    val onStudyModeSelect: (StudyMode) -> Unit,
    val onDailyGoalDecrement: () -> Unit,
    val onDailyGoalIncrement: () -> Unit,
    val onFavoriteSubcategoryToggle: (String) -> Unit,
    val onFavoritesStepEntered: () -> Unit,
    val onFavoriteSubcategoriesRetry: () -> Unit,
    val onVoiceDemoStart: () -> Unit,
    val onVoiceDemoFinish: () -> Unit,
    val onVoiceDemoPlay: () -> Unit,
    val onVoiceDemoStop: () -> Unit,
    val onFinish: () -> Unit,
)

@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
    onNavigateToMain: () -> Unit,
    onNavigateToLogin: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    observeAsEvents(viewModel.events) { destination ->
        when (destination) {
            OnboardingDestination.Main -> onNavigateToMain()
            OnboardingDestination.Login -> onNavigateToLogin()
        }
    }

    OnboardingContent(
        modifier = modifier,
        state = state,
        voiceBarsLevels = viewModel.voiceBarsLevels,
        messages = viewModel.messages,
        actions = OnboardingActions(
            onStudyModeSelect = viewModel::onStudyModeSelect,
            onDailyGoalDecrement = viewModel::onDailyGoalDecrement,
            onDailyGoalIncrement = viewModel::onDailyGoalIncrement,
            onFavoriteSubcategoryToggle = viewModel::onFavoriteSubcategoryToggle,
            onFavoritesStepEntered = viewModel::onFavoritesStepEntered,
            onFavoriteSubcategoriesRetry = viewModel::onFavoriteSubcategoriesRetry,
            onVoiceDemoStart = viewModel::onVoiceDemoStart,
            onVoiceDemoFinish = viewModel::onVoiceDemoFinish,
            onVoiceDemoPlay = viewModel::onVoiceDemoPlay,
            onVoiceDemoStop = viewModel::onVoiceDemoStop,
            onFinish = viewModel::onFinish,
        ),
    )
}

/**
 * All eight steps in one [HorizontalPager], with the gradient, progress bar, Skip and the CTA held
 * outside it as fixed chrome — so a swipe moves only the content, never the frame around it.
 *
 * Skip does not leave the flow: it jumps to the final step, which is the single place preferences
 * are committed. That keeps one exit door instead of two code paths that both have to remember to
 * save.
 */
@Composable
private fun OnboardingContent(
    modifier: Modifier = Modifier,
    state: OnboardingScreenState,
    voiceBarsLevels: StateFlow<ImmutableList<Float>>,
    actions: OnboardingActions,
    messages: SharedFlow<OnboardingMessage>,
) {
    val pagerState = rememberPagerState { OnboardingStep.entries.size }
    val coroutineScope = rememberCoroutineScope()
    val currentStep = OnboardingStep.atPage(pagerState.currentPage)

    // Fires the curated Favorites list fetch the moment that step is actually reached — never
    // prefetched, since the list is meaningless until the user gets there. The view model itself
    // no-ops any call past the first, so re-entering by swiping back and forth is harmless.
    LaunchedEffect(currentStep) {
        if (currentStep == OnboardingStep.Favorites) {
            actions.onFavoritesStepEntered()
        }
        // Hard-stops the voice demo the moment a swipe carries the pager off this step — leaving it
        // running unattended on another step is the one thing the design explicitly rules out.
        if (currentStep != OnboardingStep.VoicePrivacy) {
            actions.onVoiceDemoStop()
        }
    }

    // Same hard stop for the app leaving the foreground (recents, lock screen, backgrounding) while
    // still parked on the VoicePrivacy step — the mic must not keep listening once the screen isn't.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { actions.onVoiceDemoStop() }

    // The entrance runs once per visit to the flow, staged behind the logo the splash screen hands
    // over: the logo settles first, then the cover's copy rises into place, then the buttons appear.
    // Both progressions stay at 1f afterwards, so the chrome is simply visible on every later step.
    val copyReveal = remember { Animatable(0f) }
    val chromeReveal = remember { Animatable(0f) }
    val isInspecting = LocalInspectionMode.current
    LaunchedEffect(Unit) {
        if (isInspecting) {
            // A static @Preview renders the first frame only, which would be an empty screen.
            copyReveal.snapTo(1f)
            chromeReveal.snapTo(1f)
            return@LaunchedEffect
        }
        delay(SHARED_ELEMENT_DURATION_MS.toLong().milliseconds)
        copyReveal.animateTo(1f, tween(durationMillis = COPY_REVEAL_MS, easing = FastOutSlowInEasing))
        chromeReveal.animateTo(1f, tween(durationMillis = CHROME_REVEAL_MS, easing = LinearEasing))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.brandColors.screenGradient),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(vertical = MaterialTheme.spacing.small),
        ) {
            OnboardingTopBar(
                step = currentStep,
                revealProgress = chromeReveal.value,
                // Instant rather than animated: animating a jump of up to seven pages would fling
                // the user through every screen they just chose to skip.
                onSkip = { coroutineScope.launch { pagerState.scrollToPage(OnboardingStep.entries.lastIndex) } },
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                OnboardingStepPage(
                    step = OnboardingStep.atPage(page),
                    state = state,
                    voiceBarsLevels = voiceBarsLevels,
                    copyRevealProgress = copyReveal.value,
                    actions = actions,
                    messages = messages,
                )
            }
            OnboardingCta(
                step = currentStep,
                revealProgress = chromeReveal.value,
                // Invisible while the entrance is still running, so it cannot be tapped before it
                // has been shown.
                enabled = !state.isCommitting && chromeReveal.value > 0f,
                onClick = {
                    if (currentStep == OnboardingStep.LAST) {
                        actions.onFinish()
                    } else {
                        coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
            )
        }
    }
}

@Composable
private fun OnboardingTopBar(
    step: OnboardingStep,
    revealProgress: Float,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = revealProgress }
            .padding(horizontal = MaterialTheme.spacing.medium),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        // The Skip slot keeps its height on the final step, where there is nothing to skip to, so
        // the pager content below never shifts as the user moves between steps.
        Box(modifier = Modifier.height(MaterialTheme.sizes.buttonHeightSmall)) {
            if (step.showsSkip) {
                FlashcardsTextButton(
                    text = stringResource(R.string.onboarding_skip_button),
                    onClick = onSkip,
                    size = FlashcardsComponentSize.Small,
                    enabled = revealProgress > 0f,
                    style = FlashcardsComponentStyle.OnGradient,
                )
            }
        }
        // Same reasoning for the progress bar, which the two bookend steps do not show.
        Box(modifier = Modifier.height(MaterialTheme.sizes.progressBarThicknessNormal)) {
            val progressIndex = step.progressIndex
            if (progressIndex != null) {
                FlashcardsSegmentedProgressBar(
                    segmentCount = OnboardingStep.PROGRESS_SEGMENT_COUNT,
                    filledSegmentCount = progressIndex + 1,
                    style = FlashcardsComponentStyle.OnGradient,
                )
            }
        }
    }
}

@Composable
private fun OnboardingCta(
    step: OnboardingStep,
    revealProgress: Float,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = when (step) {
        OnboardingStep.Welcome -> stringResource(R.string.welcome_start_button)
        OnboardingStep.AllSet -> stringResource(R.string.all_set_start_button)
        else -> stringResource(R.string.onboarding_continue_button)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = revealProgress }
            .padding(
                horizontal = MaterialTheme.spacing.medium,
                vertical = MaterialTheme.spacing.small,
            ),
        contentAlignment = Alignment.CenterEnd,
    ) {
        FlashcardsFilledButton(
            text = label,
            onClick = onClick,
            enabled = enabled,
            style = FlashcardsComponentStyle.OnGradient,
        )
    }
}

@Composable
private fun OnboardingStepPage(
    step: OnboardingStep,
    state: OnboardingScreenState,
    voiceBarsLevels: StateFlow<ImmutableList<Float>>,
    copyRevealProgress: Float,
    actions: OnboardingActions,
    messages: SharedFlow<OnboardingMessage>,
    modifier: Modifier = Modifier,
) {
    when (step) {
        OnboardingStep.Welcome -> WelcomeStep(copyRevealProgress = copyRevealProgress, modifier = modifier)
        OnboardingStep.Structure -> StructureStep(modifier = modifier)
        OnboardingStep.Mastery -> MasteryStep(modifier = modifier)
        OnboardingStep.SessionModes -> SessionModesStep(
            selectedStudyMode = state.defaultStudyMode,
            onStudyModeSelect = actions.onStudyModeSelect,
            modifier = modifier,
        )
        OnboardingStep.DailyGoal -> DailyGoalStep(
            dailyGoalMinutes = state.dailyGoalMinutes,
            canDecrement = state.canDecrementDailyGoal,
            canIncrement = state.canIncrementDailyGoal,
            onDecrement = actions.onDailyGoalDecrement,
            onIncrement = actions.onDailyGoalIncrement,
            modifier = modifier,
        )
        OnboardingStep.VoicePrivacy -> VoicePrivacyStepRoute(
            voiceDemoState = state.voiceDemoState,
            voiceBarsLevels = voiceBarsLevels,
            micPermissionStatus = state.micPermissionStatus,
            messages = messages,
            onTestVoice = actions.onVoiceDemoStart,
            onStopRecording = actions.onVoiceDemoFinish,
            onPlay = actions.onVoiceDemoPlay,
            modifier = modifier,
        )
        OnboardingStep.Favorites -> FavoritesStep(
            options = state.favoriteSubcategoryOptions,
            selectedIds = state.selectedFavoriteSubcategoriesIds,
            isLoading = state.isFavoriteSubcategoriesLoading,
            loadFailed = state.favoriteSubcategoriesLoadingFailed,
            onSubcategoryToggle = actions.onFavoriteSubcategoryToggle,
            onRetry = actions.onFavoriteSubcategoriesRetry,
            modifier = modifier,
        )
        OnboardingStep.AllSet -> AllSetStep(
            userName = state.userName,
            defaultStudyMode = state.defaultStudyMode,
            dailyGoalMinutes = state.dailyGoalMinutes,
            favoriteCount = state.selectedFavoriteSubcategoriesIds.size,
            modifier = modifier,
        )
    }
}

/**
 * Hosts the voice demo's snackbars and the Open Settings intent. The microphone itself is requested
 * by the ViewModel through the shared permission layer when [onTestVoice] fires — a first tap and
 * every Retry alike — so this route only reflects the observed [micPermissionStatus].
 */
@Composable
private fun VoicePrivacyStepRoute(
    voiceDemoState: VoiceDemoState,
    voiceBarsLevels: StateFlow<ImmutableList<Float>>,
    micPermissionStatus: PermissionStatus,
    messages: SharedFlow<OnboardingMessage>,
    onTestVoice: () -> Unit,
    onStopRecording: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }
    val micPermissionStillDeniedText = stringResource(CoreUiR.string.common_mic_permission_still_denied_message)
    val nothingCapturedText = stringResource(R.string.voice_privacy_nothing_captured_message)
    val snackbarScope = rememberCoroutineScope()
    observeAsEvents(messages) { message ->
        val text = when (message) {
            is VoiceDemoFailed -> resolveVoiceDemoFailureMessage(context = context, reason = message.reason)
            MicPermissionStillDenied -> micPermissionStillDeniedText
            NothingCaptured -> nothingCapturedText
        }
        snackbarScope.launch { snackbarHostState.showSnackbar(message = text, duration = SnackbarDuration.Short) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        VoicePrivacyStep(
            voiceDemoState = voiceDemoState,
            voiceBarsLevels = voiceBarsLevels,
            permissionDenied = micPermissionStatus == PermissionStatus.PermanentlyDenied,
            onTestVoice = onTestVoice,
            onStopRecording = onStopRecording,
            onPlay = onPlay,
            onOpenSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            },
            modifier = Modifier.padding(innerPadding),
        )
    }
}

private fun resolveVoiceDemoFailureMessage(context: Context, reason: VoiceDemoFailureReason): String = when (reason) {
    RouteUnavailable -> context.getString(R.string.voice_privacy_route_unavailable_snackbar_message)
    is CaptureError -> when (reason.reason) {
        BluetoothMicUnavailable -> context.getString(CoreUiR.string.common_voice_capture_bluetooth_unavailable_message)
        AudioRecordInitFailed -> context.getString(CoreUiR.string.common_voice_capture_audio_record_init_failed_message)
        CaptureNotRoutedToBluetooth -> context.getString(CoreUiR.string.common_voice_capture_not_routed_bluetooth_message)
        is PermissionMissing -> context.getString(CoreUiR.string.common_voice_capture_permission_missing_message)
        is CaptureLoopError -> context.getString(CoreUiR.string.common_voice_capture_loop_error_message)
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 860)
@Composable
private fun OnboardingContentPreview() {
    FlashcardsTheme {
        OnboardingContent(
            state = remember { OnboardingScreenState(userName = "Radek") },
            voiceBarsLevels = remember { MutableStateFlow(FlashcardsVoiceCaptureIndicatorDefaults.restLevels) },
            messages = remember { MutableSharedFlow() },
            actions = OnboardingActions(
                onStudyModeSelect = {},
                onDailyGoalDecrement = {},
                onDailyGoalIncrement = {},
                onFavoriteSubcategoryToggle = {},
                onFavoritesStepEntered = {},
                onFavoriteSubcategoriesRetry = {},
                onVoiceDemoStart = {},
                onVoiceDemoFinish = {},
                onVoiceDemoPlay = {},
                onVoiceDemoStop = {},
                onFinish = {},
            ),
        )
    }
}
