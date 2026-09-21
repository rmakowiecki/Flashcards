package com.rossomak.flashcards.feature.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.ui.animation.SHARED_ELEMENT_DURATION_MS
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledButton
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsTextButton
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.composables.progress.FlashcardsSegmentedProgressBar
import com.rossomak.flashcards.core.ui.navigation.observeAsEvents
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.brandColors
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.onboarding.step.AllSetStep
import com.rossomak.flashcards.feature.onboarding.step.DailyGoalStep
import com.rossomak.flashcards.feature.onboarding.step.FavoritesStep
import com.rossomak.flashcards.feature.onboarding.step.MasteryStep
import com.rossomak.flashcards.feature.onboarding.step.SessionModesStep
import com.rossomak.flashcards.feature.onboarding.step.StructureStep
import com.rossomak.flashcards.feature.onboarding.step.VoicePrivacyStep
import com.rossomak.flashcards.feature.onboarding.step.WelcomeStep
import com.rossomak.flashcards.feature.onboarding.voice.VoiceDemoState
import kotlinx.coroutines.delay
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

    observeAsEvents(viewModel.events) { destination ->
        when (destination) {
            OnboardingDestination.Main -> onNavigateToMain()
            OnboardingDestination.Login -> onNavigateToLogin()
        }
    }

    OnboardingContent(
        modifier = modifier,
        state = state,
        actions = OnboardingActions(
            onStudyModeSelect = viewModel::onStudyModeSelect,
            onDailyGoalDecrement = viewModel::onDailyGoalDecrement,
            onDailyGoalIncrement = viewModel::onDailyGoalIncrement,
            onFavoriteSubcategoryToggle = viewModel::onFavoriteSubcategoryToggle,
            onFavoritesStepEntered = viewModel::onFavoritesStepEntered,
            onFavoriteSubcategoriesRetry = viewModel::onFavoriteSubcategoriesRetry,
            onVoiceDemoStart = viewModel::onVoiceDemoStart,
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
    state: OnboardingScreenState,
    actions: OnboardingActions,
    modifier: Modifier = Modifier,
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
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) actions.onVoiceDemoStop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
        delay(SHARED_ELEMENT_DURATION_MS.toLong())
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
                    copyRevealProgress = copyReveal.value,
                    actions = actions,
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
    copyRevealProgress: Float,
    actions: OnboardingActions,
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
            onTestVoice = actions.onVoiceDemoStart,
            onPlay = actions.onVoiceDemoPlay,
            onVoiceDemoStop = actions.onVoiceDemoStop,
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
 * Owns the RECORD_AUDIO check/request for the voice demo — the ViewModel and its voice gateway
 * assume the permission is already granted (docs/temp/to-grill/mic-permission-check-platform-layer.md).
 * [onTestVoice] doubles as Retry: both a first tap and a retry start a fresh listening attempt.
 */
@Composable
private fun VoicePrivacyStepRoute(
    voiceDemoState: VoiceDemoState,
    onTestVoice: () -> Unit,
    onPlay: () -> Unit,
    onVoiceDemoStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // shouldShowRequestPermissionRationale is false both before the first-ever request and
        // after a "don't ask again" denial; checking it right after this callback (rather than
        // before launch()) disambiguates: a standard denial still returns true here.
        val canAskAgain = granted ||
            ActivityCompat.shouldShowRequestPermissionRationale(
                context.findActivity(),
                Manifest.permission.RECORD_AUDIO,
            )
        permissionDenied = !granted && !canAskAgain
        if (granted) onTestVoice()
    }

    // Catches the case the launcher callback above can't: user denies, leaves to the system
    // Settings app, grants it there, and comes back. That round-trip never fires the launcher
    // callback, so only a resume-time recheck notices the permission actually changed.
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestPermissionDenied by rememberUpdatedState(permissionDenied)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val isGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
                val wasDenied = latestPermissionDenied
                if (wasDenied) {
                    permissionDenied = !isGranted
                    if (isGranted) onVoiceDemoStop()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    VoicePrivacyStep(
        voiceDemoState = voiceDemoState,
        permissionDenied = permissionDenied,
        onTestVoice = {
            val isGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
            if (isGranted) {
                permissionDenied = false
                onTestVoice()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        onPlay = onPlay,
        onOpenSettings = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        },
        modifier = modifier,
    )
}

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("Permission rationale check requires an Activity context")
}

@Preview(showBackground = true, widthDp = 400, heightDp = 860)
@Composable
private fun OnboardingContentPreview() {
    FlashcardsTheme {
        OnboardingContent(
            state = remember { OnboardingScreenState(userName = "Radek") },
            actions = OnboardingActions(
                onStudyModeSelect = {},
                onDailyGoalDecrement = {},
                onDailyGoalIncrement = {},
                onFavoriteSubcategoryToggle = {},
                onFavoritesStepEntered = {},
                onFavoriteSubcategoriesRetry = {},
                onVoiceDemoStart = {},
                onVoiceDemoPlay = {},
                onVoiceDemoStop = {},
                onFinish = {},
            ),
        )
    }
}
