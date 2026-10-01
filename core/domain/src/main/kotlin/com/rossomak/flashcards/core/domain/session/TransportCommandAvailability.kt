package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.model.FastSessionState
import com.rossomak.flashcards.core.domain.model.InterruptionEpisode
import com.rossomak.flashcards.core.domain.model.RatedSessionState
import com.rossomak.flashcards.core.domain.model.TransportCommandType
import com.rossomak.flashcards.core.domain.model.TransportCommandType.Next
import com.rossomak.flashcards.core.domain.model.TransportCommandType.Pause
import com.rossomak.flashcards.core.domain.model.TransportCommandType.Play
import com.rossomak.flashcards.core.domain.model.TransportCommandType.Previous
import com.rossomak.flashcards.core.domain.model.TransportCommandType.Stop
import com.rossomak.flashcards.core.domain.model.VoiceAnswerPhase

// What every transport surface (the in-app row, the notification, a headset) may offer right now:
// one set per session state, so the app and the system controls cannot drift apart.

private val PAUSE_ONLY = setOf(Pause, Stop)

/**
 * A Rated session's transport commands, by the phase of its voice round. Pause is on offer in every
 * phase that plays; while listening, grading or speaking a short notice it is the only command.
 * Jumping to a card is never offered: the queue decides the order.
 *
 * While a call rings or runs ([InterruptionEpisode.isCallBlocking]) no surface offers any command,
 * so the set is empty, exactly as it is once the session is complete. Screens that need to tell the
 * two apart look at whether cards remain.
 */
val RatedSessionState.availableTransportCommands: Set<TransportCommandType>
    get() = when {
        isComplete || episode.isCallBlocking -> emptySet()
        pauseReason != null -> setOf(Play)
        isShortNoticeSpeaking -> PAUSE_ONLY
        voiceAnswerPauseReason != null -> if (isPlaying) PAUSE_ONLY else setOf(Play)
        isHeldAtAdvancePoint -> setOf(Play, Pause, Stop, Next)
        isPausedAtAdvancePoint || isPausedAfterFeedback -> setOf(Play, Next)
        isPausedWhileGrading -> setOf(Play)
        isFeedbackPlaying -> setOf(Pause, Stop, Next)
        else -> when (round.phase) {
            VoiceAnswerPhase.Listening, VoiceAnswerPhase.SpeechDetected, VoiceAnswerPhase.Grading, VoiceAnswerPhase.SpeakingNotice -> PAUSE_ONLY
            VoiceAnswerPhase.Idle, VoiceAnswerPhase.WaitingForQuestion ->
                if (isPlaying) setOf(Pause, Stop, Next, Previous) else setOf(Play, Next, Previous)
        }
    }

/**
 * A Fast read-aloud session offers every command, except Next at the last card's answer: the
 * session ends only once that answer has been read in full. While a call rings or runs
 * ([InterruptionEpisode.isCallBlocking]) it offers none.
 */
val FastSessionState.availableTransportCommands: Set<TransportCommandType>
    get() = when {
        episode.isCallBlocking -> emptySet()
        isReadAloudNextAvailable -> TransportCommandType.entries.toSet()
        else -> TransportCommandType.entries.toSet() - Next
    }
