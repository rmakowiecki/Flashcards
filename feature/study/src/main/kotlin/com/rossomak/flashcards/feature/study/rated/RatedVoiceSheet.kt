package com.rossomak.flashcards.feature.study.rated

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating
import com.rossomak.flashcards.core.ui.R as CoreUiR
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledIconButton
import com.rossomak.flashcards.core.ui.composables.rating.FlashcardsRatingButton
import com.rossomak.flashcards.core.ui.composables.rating.labelRes
import com.rossomak.flashcards.core.ui.composables.voice.FlashcardsVoiceCaptureIndicator
import com.rossomak.flashcards.core.ui.composables.voice.FlashcardsVoiceCaptureIndicatorDefaults
import com.rossomak.flashcards.core.ui.composables.voice.FlashcardsVoiceProgressDisc
import com.rossomak.flashcards.core.ui.theme.FlashcardsMotion
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.study.R
import com.rossomak.flashcards.feature.study.rated.RatedVoiceSheetMode.Graded
import com.rossomak.flashcards.feature.study.rated.RatedVoiceSheetMode.GradingWithTranscript
import com.rossomak.flashcards.feature.study.rated.RatedVoiceSheetMode.Legacy
import com.rossomak.flashcards.feature.study.rated.RatedVoiceSheetMode.Listening
import com.rossomak.flashcards.feature.study.rated.RatedVoiceSheetMode.Pending
import com.rossomak.flashcards.feature.study.rated.RatedVoiceSheetMode.Transport
import com.rossomak.flashcards.feature.study.voice.VoiceAnswerPhase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow

/**
 * Groups the sheet modes that share one layout. Switching modes inside a group keeps that layout's
 * nodes alive, so only a group change swaps the sheet's content.
 */
private enum class RatedVoiceSheetGroup { Transport, VoiceRound, Legacy }

private val RatedVoiceSheetMode.group: RatedVoiceSheetGroup
    get() = when (this) {
        Transport -> RatedVoiceSheetGroup.Transport
        Listening, Pending, is GradingWithTranscript, is Graded -> RatedVoiceSheetGroup.VoiceRound
        Legacy -> RatedVoiceSheetGroup.Legacy
    }

@Composable
internal fun RatedVoiceSheetContent(
    state: RatedStudySessionScreenState,
    voiceBarsLevels: StateFlow<ImmutableList<Float>>,
    onShowAnswer: () -> Unit,
    onVoicePlayPause: () -> Unit,
    onVoiceNext: () -> Unit,
    onVoicePrevious: () -> Unit,
    onVoiceSettingsCogClick: () -> Unit,
) {
    val voiceSheetMode = state.voiceSheetMode
    AnimatedContent(
        targetState = voiceSheetMode.group,
        modifier = Modifier
            .fillMaxWidth()
            .height(SHEET_PEEK_HEIGHT_VOICE)
            .padding(horizontal = MaterialTheme.spacing.normal)
            .padding(top = MaterialTheme.spacing.normal, bottom = MaterialTheme.spacing.medium),
        transitionSpec = {
            fadeIn(tween(FlashcardsMotion.DURATION_MEDIUM_MS)) togetherWith fadeOut(tween(FlashcardsMotion.DURATION_MEDIUM_MS))
        },
        contentAlignment = Alignment.Center,
        label = "ratedVoiceSheetGroup",
    ) { group ->
        when (group) {
            RatedVoiceSheetGroup.Transport -> RatedVoiceTransportSheet(
                state = state,
                onShowAnswer = onShowAnswer,
                onVoicePlayPause = onVoicePlayPause,
                onVoiceNext = onVoiceNext,
                onVoicePrevious = onVoicePrevious,
                onVoiceSettingsCogClick = onVoiceSettingsCogClick,
            )
            // Reads the live mode rather than the group: while this group fades out, the indicator
            // folds its bars in instead of freezing on the last level.
            RatedVoiceSheetGroup.VoiceRound -> RatedVoiceRoundSheet(
                voiceSheetMode = voiceSheetMode,
                voiceBarsLevels = voiceBarsLevels,
            )
            RatedVoiceSheetGroup.Legacy -> RatedVoiceLegacySheet(
                state = state,
                onShowAnswer = onShowAnswer,
                onVoicePlayPause = onVoicePlayPause,
                onVoiceNext = onVoiceNext,
                onVoicePrevious = onVoicePrevious,
            )
        }
    }
}

@Composable
private fun RatedVoiceTransportSheet(
    state: RatedStudySessionScreenState,
    onShowAnswer: () -> Unit,
    onVoicePlayPause: () -> Unit,
    onVoiceNext: () -> Unit,
    onVoicePrevious: () -> Unit,
    onVoiceSettingsCogClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        RatedVoiceTransportRow(
            state = state,
            onShowAnswer = onShowAnswer,
            onVoicePlayPause = onVoicePlayPause,
            onVoiceNext = onVoiceNext,
            onVoicePrevious = onVoicePrevious,
            modifier = Modifier.align(Alignment.Center),
        )
        IconButton(
            onClick = onVoiceSettingsCogClick,
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.study_session_voice_settings_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The voice round: the microphone indicator while listening, then the badge slot holding the
 * progress disc or the Rating circle, with the label under it and the title and paragraph beside
 * it once there is text to show. The three discs stay separate composables; only the slot is kept
 * across the grading modes.
 */
@Composable
private fun RatedVoiceRoundSheet(voiceSheetMode: RatedVoiceSheetMode, voiceBarsLevels: StateFlow<ImmutableList<Float>>) {
    // Collected here, not at the screen root: a new level every wave interval recomposes only the round.
    val currentVoiceBarsLevels by voiceBarsLevels.collectAsStateWithLifecycle()
    val isListening = voiceSheetMode == Listening
    val isBadgeCentered = voiceSheetMode !is GradingWithTranscript && voiceSheetMode !is Graded
    val roundDescription = ratedVoiceRoundDescription(voiceSheetMode)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clearAndSetSemantics {
                roundDescription?.let { contentDescription = it }
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        AnimatedVisibility(
            visible = isListening,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(FlashcardsMotion.DURATION_MEDIUM_MS)),
            exit = fadeOut(tween(FlashcardsMotion.DURATION_MEDIUM_MS)),
        ) {
            FlashcardsVoiceCaptureIndicator(
                levels = currentVoiceBarsLevels,
                contentDescription = stringResource(CoreUiR.string.common_voice_capture_listening_cd),
                isActive = isListening,
            )
        }
        RatedVoiceBadge(
            voiceSheetMode = voiceSheetMode,
            modifier = Modifier.align(if (isBadgeCentered) Alignment.Center else Alignment.CenterStart),
        )
        RatedVoiceRoundText(
            voiceSheetMode = voiceSheetMode,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = FlashcardsVoiceCaptureIndicatorDefaults.discSize + MaterialTheme.spacing.normal),
        )
    }
}

/**
 * What TalkBack announces for the voice round. The user's transcript is display-only and never
 * part of it; the rationale is left out too, because the voice notice already reads it aloud.
 */
@Composable
private fun ratedVoiceRoundDescription(voiceSheetMode: RatedVoiceSheetMode): String? = when (voiceSheetMode) {
    Listening -> stringResource(CoreUiR.string.common_voice_capture_listening_cd)
    Pending, is GradingWithTranscript -> stringResource(R.string.study_session_voice_answer_grading_label)
    is Graded -> stringResource(voiceSheetMode.rating.labelRes)
    Transport, Legacy -> null
}

/** What the badge slot holds; `null` leaves it empty. */
private sealed interface RatedVoiceBadgeContent {
    data object Progress : RatedVoiceBadgeContent
    data class Rating(val rating: FlashcardAttemptRating) : RatedVoiceBadgeContent
}

private val RatedVoiceSheetMode.badgeContent: RatedVoiceBadgeContent?
    get() = when (this) {
        Pending, is GradingWithTranscript -> RatedVoiceBadgeContent.Progress
        is Graded -> RatedVoiceBadgeContent.Rating(rating)
        Transport, Listening, Legacy -> null
    }

private val RatedVoiceSheetMode.badgeLabelRes: Int?
    get() = when (this) {
        is GradingWithTranscript -> R.string.study_session_voice_answer_grading_label
        is Graded -> rating.labelRes
        Transport, Listening, Pending, Legacy -> null
    }

@Composable
private fun RatedVoiceBadge(voiceSheetMode: RatedVoiceSheetMode, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The badge slot: one invisible node for the whole voice round, so moving it between the
        // center and the left keeps its identity.
        Box(
            modifier = Modifier.size(FlashcardsVoiceCaptureIndicatorDefaults.discSize),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(
                targetState = voiceSheetMode.badgeContent,
                animationSpec = tween(FlashcardsMotion.DURATION_MEDIUM_MS),
                label = "ratedVoiceBadgeContent",
            ) { badgeContent ->
                when (badgeContent) {
                    RatedVoiceBadgeContent.Progress -> FlashcardsVoiceProgressDisc()
                    is RatedVoiceBadgeContent.Rating -> FlashcardsRatingButton(rating = badgeContent.rating, showLabel = false, onClick = null)
                    null -> Unit
                }
            }
        }
        Crossfade(
            targetState = voiceSheetMode.badgeLabelRes,
            animationSpec = tween(FlashcardsMotion.DURATION_MEDIUM_MS),
            label = "ratedVoiceBadgeLabel",
        ) { labelRes ->
            if (labelRes != null) {
                Text(
                    text = stringResource(labelRes),
                    modifier = Modifier.padding(top = MaterialTheme.spacing.xxsmall),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun RatedVoiceRoundText(voiceSheetMode: RatedVoiceSheetMode, modifier: Modifier = Modifier) {
    val titleRes = when (voiceSheetMode) {
        is GradingWithTranscript -> R.string.study_session_voice_answer_transcript_title
        is Graded -> R.string.study_session_voice_answer_rating_title
        Transport, Listening, Pending, Legacy -> null
    }
    val paragraph = when (voiceSheetMode) {
        is GradingWithTranscript -> voiceSheetMode.transcript
        is Graded -> voiceSheetMode.rationale
        Transport, Listening, Pending, Legacy -> null
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
    ) {
        Crossfade(
            targetState = titleRes,
            animationSpec = tween(FlashcardsMotion.DURATION_MEDIUM_MS),
            label = "ratedVoiceRoundTitle",
        ) { shownTitleRes ->
            if (shownTitleRes != null) {
                Text(
                    text = stringResource(shownTitleRes),
                    modifier = Modifier.padding(bottom = MaterialTheme.spacing.xxsmall),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        // Scrolls inside the fixed-height sheet instead of truncating a long transcript or rationale.
        Crossfade(
            targetState = paragraph,
            modifier = Modifier.weight(1f, fill = false),
            animationSpec = tween(FlashcardsMotion.DURATION_MEDIUM_MS),
            label = "ratedVoiceRoundParagraph",
        ) { shownParagraph ->
            if (shownParagraph != null) {
                Text(
                    text = shownParagraph,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun RatedVoiceLegacySheet(
    state: RatedStudySessionScreenState,
    onShowAnswer: () -> Unit,
    onVoicePlayPause: () -> Unit,
    onVoiceNext: () -> Unit,
    onVoicePrevious: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        RatedVoiceTranscript(state = state)
        RatedVoiceTransportRow(
            state = state,
            onShowAnswer = onShowAnswer,
            onVoicePlayPause = onVoicePlayPause,
            onVoiceNext = onVoiceNext,
            onVoicePrevious = onVoicePrevious,
            modifier = Modifier.padding(top = MaterialTheme.spacing.normal),
        )
    }
}

// Shown as soon as the sanitized transcript streams in (ADR-0028) — screen-on is a first-class
// case, not just a background/audio-only fallback, so the transcript should be readable the
// moment it arrives rather than waiting for the grade.
@Composable
private fun RatedVoiceTranscript(state: RatedStudySessionScreenState) {
    if (state.isVoiceAnswerEnabled &&
        !state.voiceAnswerSanitizedTranscript.isNullOrBlank() &&
        (state.voiceAnswerPhase == VoiceAnswerPhase.Grading || state.voiceAnswerPhase == VoiceAnswerPhase.SpeakingNotice)
    ) {
        Text(
            text = state.voiceAnswerSanitizedTranscript,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MaterialTheme.spacing.xxsmall),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun RatedVoiceTransportRow(
    state: RatedStudySessionScreenState,
    onShowAnswer: () -> Unit,
    onVoicePlayPause: () -> Unit,
    onVoiceNext: () -> Unit,
    onVoicePrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // While voice-answering is actively listening/grading/speaking feedback, manual skip
    // controls must stay disabled: skipping to the answer here would start TtsPlayer reading
    // the answer aloud while VoiceAnswerController's mic is still hot (grading the TTS's own
    // voice), and skipping during SPEAKING_NOTICE would start the next question on the main
    // TTS engine while VoiceAnswerController's separate notice engine is still talking — two
    // overlapping voices.
    val busyStateSet = setOf(VoiceAnswerPhase.Listening, VoiceAnswerPhase.SpeechDetected, VoiceAnswerPhase.Grading, VoiceAnswerPhase.SpeakingNotice)
    val isVoiceAnswerBusy = state.isVoiceAnswerEnabled && state.voiceAnswerPhase in busyStateSet
    // Pause only needs to stay disabled for the narrower "answer listening" window — it
    // toggles the main TtsPlayer, which is a no-op while the mic is what's actually capturing
    // (LISTENING/SPEECH_DETECTED); re-enables the moment the answer (or its absence) has been
    // noted and GRADING/SPEAKING_NOTICE takes over.
    val isVoiceAnswerListening = state.isVoiceAnswerEnabled && state.voiceAnswerPhase in setOf(VoiceAnswerPhase.Listening, VoiceAnswerPhase.SpeechDetected)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onVoicePrevious,
            enabled = state.isVoiceActive && state.currentCardIndex > 0 && !isVoiceAnswerBusy && !state.isVoiceAnswerPaused,
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = stringResource(R.string.study_session_previous_card_cd),
            )
        }
        Spacer(modifier = Modifier.size(MaterialTheme.spacing.normal))
        FlashcardsFilledIconButton(
            icon = if (state.isVoicePlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = stringResource(if (state.isVoicePlaying) R.string.study_session_voice_pause_cd else R.string.study_session_voice_play_cd),
            onClick = onVoicePlayPause,
            enabled = state.isVoiceActive && !isVoiceAnswerListening,
        )
        Spacer(modifier = Modifier.size(MaterialTheme.spacing.normal))
        IconButton(
            onClick = if (state.isVoiceAnswerEnabled || state.isAnswerRevealed) onVoiceNext else onShowAnswer,
            enabled = state.isVoiceActive && !isVoiceAnswerBusy && !state.isVoiceAnswerPaused,
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = stringResource(
                    if (state.isVoiceAnswerEnabled || state.isAnswerRevealed) R.string.study_session_next_flashcard_cd else R.string.study_session_show_answer_cd
                )
            )
        }
    }
}
