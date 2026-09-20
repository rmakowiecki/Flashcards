package com.rossomak.flashcards.feature.onboarding.step

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rossomak.flashcards.core.ui.composables.banners.FlashcardsInfoBanner
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledButton
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsTextButton
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.theme.cornerRadius
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.onboarding.R
import com.rossomak.flashcards.feature.onboarding.component.OnboardingContentColors
import com.rossomak.flashcards.feature.onboarding.component.OnboardingStepColumn
import com.rossomak.flashcards.feature.onboarding.component.OnboardingStepHeader
import com.rossomak.flashcards.feature.onboarding.voice.VoiceDemoState

/**
 * Introduces Voice Answering and the on-device privacy transform.
 *
 * The free/premium split is deliberate: capture and the obfuscation transform are free for
 * everyone, and only the AI grading of the spoken answer is gated — hence a premium line scoped to
 * grading rather than a badge over the whole screen.
 *
 * RECORD_AUDIO is requested and checked by the caller, not here: [permissionDenied] is the only
 * signal this composable gets about it (docs/temp/to-grill/mic-permission-check-platform-layer.md).
 */
@Composable
internal fun VoicePrivacyStep(
    voiceDemoState: VoiceDemoState,
    permissionDenied: Boolean,
    onTestVoice: () -> Unit,
    onPlay: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingStepColumn(modifier = modifier) {
        OnboardingStepHeader(
            eyebrow = stringResource(R.string.voice_privacy_eyebrow_label),
            headline = stringResource(R.string.voice_privacy_headline_title),
            message = stringResource(R.string.voice_privacy_intro_message),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.xsmall))
        PremiumNote()
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
        VoiceTestCard(
            voiceDemoState = voiceDemoState,
            permissionDenied = permissionDenied,
            onTestVoice = onTestVoice,
            onPlay = onPlay,
            onOpenSettings = onOpenSettings,
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.normal))
        FlashcardsInfoBanner(
            text = stringResource(R.string.voice_privacy_banner_message),
            icon = Icons.Default.Lock,
            modifier = Modifier.fillMaxWidth(),
            style = FlashcardsComponentStyle.OnGradient,
        )
    }
}

@Composable
private fun PremiumNote(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxsmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.WorkspacePremium,
            contentDescription = null,
            modifier = Modifier.size(MaterialTheme.sizes.metadataBadgeIcon),
            tint = OnboardingContentColors.secondary,
        )
        Text(
            text = stringResource(R.string.voice_privacy_premium_message),
            style = MaterialTheme.typography.labelMedium,
            color = OnboardingContentColors.secondary,
        )
    }
}

@Composable
private fun VoiceTestCard(
    voiceDemoState: VoiceDemoState,
    permissionDenied: Boolean,
    onTestVoice: () -> Unit,
    onPlay: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The Surface itself is pinned to an exact height (MicBadge + spacing + fixed body slot +
    // padding), not just the body slot inside it — belt-and-suspenders so the card's outer bounds
    // can never move, regardless of what the body's own height/scroll measurement does internally.
    val cardHeight = MaterialTheme.sizes.ratingButton +
        MaterialTheme.spacing.small +
        VOICE_TEST_CARD_BODY_HEIGHT +
        MaterialTheme.spacing.normal * 2
    Surface(
        modifier = modifier.fillMaxWidth().height(cardHeight),
        shape = RoundedCornerShape(MaterialTheme.cornerRadius.card),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.normal),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            MicBadge(voiceDemoState = voiceDemoState)
            // Fixed height, sized to the tallest of the states below (permission-denied's two-line
            // message + button), so the card never visibly resizes as the voice demo state changes.
            // verticalScroll is a safety net only, for oversized a11y font scale overflowing it.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(VOICE_TEST_CARD_BODY_HEIGHT)
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center,
            ) {
                VoiceTestCardBody(
                    voiceDemoState = voiceDemoState,
                    permissionDenied = permissionDenied,
                    onTestVoice = onTestVoice,
                    onPlay = onPlay,
                    onOpenSettings = onOpenSettings,
                )
            }
        }
    }
}

private val VOICE_TEST_CARD_BODY_HEIGHT = 120.dp

@Composable
private fun MicBadge(voiceDemoState: VoiceDemoState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(MaterialTheme.sizes.ratingButton),
        shape = RoundedCornerShape(MaterialTheme.cornerRadius.full),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (voiceDemoState is VoiceDemoState.Listening || voiceDemoState is VoiceDemoState.SpeechDetected) {
                CircularProgressIndicator(modifier = Modifier.size(MaterialTheme.sizes.metadataBadgeIcon))
            } else {
                Icon(imageVector = Icons.Default.Mic, contentDescription = null)
            }
        }
    }
}

@Composable
private fun VoiceTestCardBody(
    voiceDemoState: VoiceDemoState,
    permissionDenied: Boolean,
    onTestVoice: () -> Unit,
    onPlay: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        permissionDenied -> Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            Text(
                text = stringResource(R.string.voice_privacy_permission_denied_message),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            FlashcardsFilledButton(
                text = stringResource(R.string.voice_privacy_open_settings_button),
                onClick = onOpenSettings,
            )
        }

        else -> when (voiceDemoState) {
            is VoiceDemoState.Idle -> VoiceTestHint(
                text = stringResource(R.string.voice_privacy_try_hint),
                buttonText = stringResource(R.string.voice_privacy_test_button),
                onClick = onTestVoice,
            )

            is VoiceDemoState.Listening -> VoiceTestStatus(
                text = stringResource(R.string.voice_privacy_listening_hint),
            )

            is VoiceDemoState.SpeechDetected -> VoiceTestStatus(
                text = stringResource(R.string.voice_privacy_speech_detected_hint),
            )

            is VoiceDemoState.Ready, is VoiceDemoState.Playing -> Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                FlashcardsFilledButton(
                    text = stringResource(R.string.voice_privacy_play_button),
                    onClick = onPlay,
                    icon = Icons.Default.PlayArrow,
                    enabled = voiceDemoState !is VoiceDemoState.Playing,
                )
                FlashcardsTextButton(
                    text = stringResource(R.string.voice_privacy_retry_button),
                    onClick = onTestVoice,
                    icon = Icons.Default.Replay,
                )
            }

            is VoiceDemoState.Failed -> VoiceTestHint(
                text = stringResource(R.string.voice_privacy_capture_failed_message),
                buttonText = stringResource(R.string.voice_privacy_retry_button),
                onClick = onTestVoice,
            )
        }
    }
}

@Composable
private fun VoiceTestHint(
    text: String,
    buttonText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        FlashcardsFilledButton(
            text = buttonText,
            onClick = onClick,
            icon = Icons.Default.Mic,
        )
    }
}

@Composable
private fun VoiceTestStatus(text: String, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier.fillMaxWidth(),
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
