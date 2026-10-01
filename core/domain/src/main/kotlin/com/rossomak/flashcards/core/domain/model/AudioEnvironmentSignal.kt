package com.rossomak.flashcards.core.domain.model

/** What the audio focus of the study session did, as the platform reports it. */
enum class FocusChange {
    /** The session holds focus again. */
    Gain,

    /** Another app asked for focus for a moment and would like the session to lower its volume: a notification sound, a navigation prompt. */
    LossCanDuck,

    /** Another app took focus for a while and will give it back: the Assistant, a voice message, an alarm, a ringing call. */
    LossTransient,

    /** Another app took focus for good, such as a media app that started playing. Focus does not come back by itself. */
    Loss,
}

/** The device's audio mode, reduced to what the study session tells apart. */
enum class AudioMode {
    /** No call. Includes the call mode the session sets itself on a Bluetooth headset while it listens. */
    Normal,

    /** An incoming call rings. */
    Ringtone,

    /** A phone call is in progress, or being screened or redirected. */
    InCall,

    /** Another app, such as a VoIP call, runs in communication mode. */
    InCommunication,
}

/**
 * Something that changed in the audio environment around a voice session, translated from the
 * platform by an [com.rossomak.flashcards.core.domain.repository.AudioInterruptionGateway]. Carries
 * no time: the coordinator stamps each signal when it arrives.
 */
sealed interface AudioEnvironmentSignal {

    data class FocusChanged(val change: FocusChange) : AudioEnvironmentSignal

    /** The live audio mode, reported before any focus change that arrives with it. */
    data class AudioModeChanged(val mode: AudioMode) : AudioEnvironmentSignal

    /** Another app started capturing and the session's microphone now receives silence. */
    data object MicSilenced : AudioEnvironmentSignal

    data object MicUnsilenced : AudioEnvironmentSignal

    /** A headset, wired or Bluetooth, was disconnected from the output the session plays on. */
    data object OutputDisconnected : AudioEnvironmentSignal
}
