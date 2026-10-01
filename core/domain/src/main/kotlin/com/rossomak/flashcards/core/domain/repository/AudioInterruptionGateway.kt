package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal
import kotlinx.coroutines.flow.Flow

/**
 * What other apps and the device do to the audio around a voice session: focus changes, calls, a
 * microphone taken by another app and a headset disconnect. It translates and reports; it never
 * pauses or resumes anything, because the study session coordinators decide.
 */
interface AudioInterruptionGateway {

    /**
     * Unconflated, in the order things happened, so the end of an interruption can never be lost.
     * Single collector: a signal is delivered once, including one reported before collection started.
     * The live audio mode is always reported before the focus change that arrives with it, and the mode
     * the session opens in comes first of all, so a call already ringing is known from the start.
     */
    val signals: Flow<AudioEnvironmentSignal>
}
