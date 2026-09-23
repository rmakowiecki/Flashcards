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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rossomak.flashcards.core.domain.model.VoiceDemoState
import com.rossomak.flashcards.core.domain.model.VoiceDemoState.Failed
import com.rossomak.flashcards.core.domain.model.VoiceDemoState.Idle
import com.rossomak.flashcards.core.domain.model.VoiceDemoState.Listening
import com.rossomak.flashcards.core.domain.model.VoiceDemoState.Playing
import com.rossomak.flashcards.core.domain.model.VoiceDemoState.Ready
import com.rossomak.flashcards.core.domain.model.VoiceDemoState.SpeechDetected
import com.rossomak.flashcards.core.ui.composables.banners.FlashcardsInfoBanner
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledButton
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsTextButton
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.cornerRadius
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.onboarding.R
import com.rossomak.flashcards.feature.onboarding.component.OnboardingContentColors
import com.rossomak.flashcards.feature.onboarding.component.OnboardingStepColumn
import com.rossomak.flashcards.feature.onboarding.component.OnboardingStepHeader

/**
 * Introduces Voice Answering and the on-device privacy transform.
 *
 * The free/premium split is deliberate: capture and the obfuscation transform are free for
 * everyone, and only the AI grading of the spoken answer is gated — hence a premium line scoped to
 * grading rather than a badge over the whole screen.
 *
 * The ViewModel requests the microphone through the shared permission layer whenever [onTestVoice]
 * fires; [permissionDenied] (a permanent refusal) is the only signal this composable gets about it,
 * and its row keeps a Retry next to Open settings so a false "permanently denied" can still heal.
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
        modifier = modifier
            .fillMaxWidth()
            .height(cardHeight),
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
            // message + button row), so the card never visibly resizes as the voice demo state changes.
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
    val listeningContentDescription = stringResource(R.string.voice_privacy_listening_cd)
    Surface(
        modifier = modifier.size(MaterialTheme.sizes.ratingButton),
        shape = RoundedCornerShape(MaterialTheme.cornerRadius.full),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (voiceDemoState is Listening || voiceDemoState is SpeechDetected) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(MaterialTheme.sizes.metadataBadgeIcon)
                        .semantics { contentDescription = listeningContentDescription },
                )
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
    if (permissionDenied) {
        Column(
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
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                FlashcardsFilledButton(
                    text = stringResource(R.string.voice_privacy_open_settings_button),
                    onClick = onOpenSettings,
                )
                FlashcardsTextButton(
                    text = stringResource(R.string.voice_privacy_retry_button),
                    onClick = onTestVoice,
                    icon = Icons.Default.Replay,
                )
            }
        }
    } else {
        when (voiceDemoState) {
            is Idle -> VoiceTestHint(
                text = stringResource(R.string.voice_privacy_try_hint),
                buttonText = stringResource(R.string.voice_privacy_test_button),
                onClick = onTestVoice,
            )
            is Listening -> VoiceTestStatus(
                text = stringResource(R.string.voice_privacy_listening_hint),
            )
            is SpeechDetected -> VoiceTestStatus(
                text = stringResource(R.string.voice_privacy_speech_detected_hint),
            )
            Ready, Playing -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                VoiceTestStatus(
                    text = stringResource(
                        if (voiceDemoState is Playing) {
                            R.string.voice_privacy_playing_hint
                        } else {
                            R.string.voice_privacy_ready_hint
                        },
                    ),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                ) {
                    FlashcardsFilledButton(
                        text = stringResource(R.string.voice_privacy_play_button),
                        onClick = onPlay,
                        icon = Icons.Default.PlayArrow,
                        enabled = voiceDemoState !is Playing,
                    )
                    FlashcardsTextButton(
                        text = stringResource(R.string.voice_privacy_retry_button),
                        onClick = onTestVoice,
                        icon = Icons.Default.Replay,
                    )
                }
            }
            is Failed -> VoiceTestHint(
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

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun VoicePrivacyStepPermissionDeniedPreview() {
    FlashcardsTheme {
        VoicePrivacyStep(
            voiceDemoState = Idle,
            permissionDenied = true,
            onTestVoice = {},
            onPlay = {},
            onOpenSettings = {},
        )
    }
}
