package com.rossomak.flashcards.feature.study.rated

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
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
import androidx.compose.ui.layout.LookaheadScope
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
private enum class RatedVoiceSheetGroup { Transport, VoiceRound }

private val RatedVoiceSheetMode.group: RatedVoiceSheetGroup
    get() = when (this) {
        Transport -> RatedVoiceSheetGroup.Transport
        Listening, Pending, is GradingWithTranscript, is Graded -> RatedVoiceSheetGroup.VoiceRound
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
 * across the grading modes, and only the slot animates its bounds when it moves to the left.
 */
@Composable
private fun RatedVoiceRoundSheet(voiceSheetMode: RatedVoiceSheetMode, voiceBarsLevels: StateFlow<ImmutableList<Float>>) {
    // Collected here, not at the screen root: a new level every wave interval recomposes only the round.
    val currentVoiceBarsLevels by voiceBarsLevels.collectAsStateWithLifecycle()
    val isListening = voiceSheetMode == Listening
    val isBadgeCentered = voiceSheetMode !is GradingWithTranscript && voiceSheetMode !is Graded
    val roundDescription = ratedVoiceRoundDescription(voiceSheetMode)
    // Straight from Pending to Graded no transcript has moved the badge yet, so its slide to the
    // left runs first and the text fades in once it ends. Every other change runs them together.
    val modeTransition = updateTransition(targetState = voiceSheetMode, label = "ratedVoiceRoundMode")
    val isBadgeSlideFirst = modeTransition.currentState == Pending && modeTransition.targetState is Graded
    val textEnterDelayMillis = if (isBadgeSlideFirst) BADGE_SLIDE_DURATION_MS else 0
    LookaheadScope {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clearAndSetSemantics {
                    roundDescription?.let { contentDescription = it }
                    liveRegion = LiveRegionMode.Polite
                },
        ) {
            // Listening to Pending happens in place: the indicator folds its bars into its disc
            // and fades out while the progress disc fades in on the same center, all starting
            // together. The indicator is symmetric, so its disc center is the centered slot's.
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
                lookaheadScope = this@LookaheadScope,
                labelEnterDelayMillis = textEnterDelayMillis,
                modifier = Modifier.align(if (isBadgeCentered) Alignment.Center else Alignment.CenterStart),
            )
            RatedVoiceRoundText(
                voiceSheetMode = voiceSheetMode,
                textEnterDelayMillis = textEnterDelayMillis,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = FlashcardsVoiceCaptureIndicatorDefaults.discSize + MaterialTheme.spacing.normal),
            )
        }
    }
}

/**
 * Swaps one text for another without the two ever overlapping: the old text fades out first, then
 * the new one fades in, after an extra [enterDelayMillis] when the badge slides first.
 */
private fun sequentialTextFade(enterDelayMillis: Int): ContentTransform = ContentTransform(
    targetContentEnter = fadeIn(tween(FlashcardsMotion.DURATION_MEDIUM_MS, delayMillis = FlashcardsMotion.DURATION_SHORT_MS + enterDelayMillis)),
    initialContentExit = fadeOut(tween(FlashcardsMotion.DURATION_SHORT_MS)),
    sizeTransform = null,
)

/** Position only: every disc in the slot shares one size, so the slot never resizes. */
private val badgeSlideBoundsTransform = BoundsTransform { _, _ ->
    tween(BADGE_SLIDE_DURATION_MS, easing = FlashcardsMotion.StandardEasing)
}

private const val BADGE_SLIDE_DURATION_MS = FlashcardsMotion.DURATION_LONG_MS

/**
 * What TalkBack announces for the voice round. The user's transcript is display-only and never
 * part of it; the rationale is left out too, because the voice notice already reads it aloud.
 */
@Composable
private fun ratedVoiceRoundDescription(voiceSheetMode: RatedVoiceSheetMode): String? = when (voiceSheetMode) {
    Listening -> stringResource(CoreUiR.string.common_voice_capture_listening_cd)
    Pending, is GradingWithTranscript -> stringResource(R.string.study_session_voice_answer_grading_label)
    is Graded -> stringResource(voiceSheetMode.rating.labelRes)
    Transport -> null
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
        Transport, Listening -> null
    }

private val RatedVoiceSheetMode.badgeLabelRes: Int?
    get() = when (this) {
        is GradingWithTranscript -> R.string.study_session_voice_answer_grading_label
        is Graded -> rating.labelRes
        Transport, Listening, Pending -> null
    }

@Composable
private fun RatedVoiceBadge(voiceSheetMode: RatedVoiceSheetMode, lookaheadScope: LookaheadScope, labelEnterDelayMillis: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The badge slot: one invisible node for the whole voice round, so moving it between the
        // center and the left keeps its identity and animates its position. Its contents only
        // crossfade, which also carries the change to the Rating color on Graded.
        Box(
            modifier = Modifier
                .animateBounds(lookaheadScope = lookaheadScope, boundsTransform = badgeSlideBoundsTransform)
                .size(FlashcardsVoiceCaptureIndicatorDefaults.discSize),
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
        AnimatedContent(
            targetState = voiceSheetMode.badgeLabelRes,
            transitionSpec = { sequentialTextFade(labelEnterDelayMillis) },
            contentAlignment = Alignment.Center,
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
private fun RatedVoiceRoundText(voiceSheetMode: RatedVoiceSheetMode, textEnterDelayMillis: Int, modifier: Modifier = Modifier) {
    val titleRes = when (voiceSheetMode) {
        is GradingWithTranscript -> R.string.study_session_voice_answer_transcript_title
        is Graded -> R.string.study_session_voice_answer_rating_title
        Transport, Listening, Pending -> null
    }
    val paragraph = when (voiceSheetMode) {
        is GradingWithTranscript -> voiceSheetMode.transcript
        is Graded -> voiceSheetMode.rationale
        Transport, Listening, Pending -> null
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
    ) {
        AnimatedContent(
            targetState = titleRes,
            transitionSpec = { sequentialTextFade(textEnterDelayMillis) },
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
        AnimatedContent(
            targetState = paragraph,
            modifier = Modifier.weight(1f, fill = false),
            transitionSpec = { sequentialTextFade(textEnterDelayMillis) },
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
