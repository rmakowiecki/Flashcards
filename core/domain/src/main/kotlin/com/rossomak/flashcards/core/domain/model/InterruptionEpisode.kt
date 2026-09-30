package com.rossomak.flashcards.core.domain.model

import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.AudioModeChanged
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.FocusChanged
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.MicSilenced
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.MicUnsilenced
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.OutputDisconnected
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** How severely another app's audio interrupts a voice session. Later values include the earlier ones. */
enum class InterruptionTier {
    /** A notification sound or a navigation prompt: short, and no reason to lose the user's answer. */
    Blip,

    /** The Assistant, an alarm, a ringing call or a microphone taken by another app: the session holds and resumes on its own soon after. */
    Interruption,

    /** A picked-up call: the session stays paused until the user resumes it, whatever the call's length. */
    Call,

    /** Another media app took over for good: the session stays paused until the user resumes it. */
    Takeover,
}

/** Why an [InterruptionEpisode] ended. */
enum class EpisodeEnd {
    /** A [InterruptionTier.Blip] ended before it escalated. */
    BlipPassed,

    /** An [InterruptionTier.Interruption] ended inside [InterruptionEpisode.AUTO_RESUME_WINDOW] and nothing cancelled the resume. */
    AutoResume,

    /** An [InterruptionTier.Interruption] ended after [InterruptionEpisode.AUTO_RESUME_WINDOW]. */
    Expired,

    /** An [InterruptionTier.Interruption] ended after the user, a disconnect or the session cancelled its resume. */
    Cancelled,

    /** A picked-up call ended. The session never resumes on its own after one. */
    CallEnded,
}

/** What an [InterruptionEpisode] step meant for the session. */
sealed interface EpisodeChange {
    data class Started(val tier: InterruptionTier) : EpisodeChange
    data class Escalated(val from: InterruptionTier, val to: InterruptionTier) : EpisodeChange
    data class Ended(val end: EpisodeEnd) : EpisodeChange
}

/** An [InterruptionEpisode] after one signal, and what that signal meant, if anything. */
data class InterruptionStep(val episode: InterruptionEpisode, val change: EpisodeChange? = null)

/**
 * The audio interruption a voice session lives through, as pure state: it runs from the first focus
 * loss (or microphone taken by another app) to the gain, and its [tier] is the highest severity
 * seen. Both study session reducers hold one in their state and react to the [EpisodeChange] a
 * signal produces; the episode itself decides nothing about playback.
 *
 * - **Calls.** A call is picked up once the audio mode reaches an in-call mode, or a communication
 *   mode of another app, at any moment of the episode. That is sticky: the episode ends as a
 *   [InterruptionTier.Call] even after the call ends. A ring alone stays an
 *   [InterruptionTier.Interruption], so a declined or missed call resumes on its own.
 * - **Takeover.** Focus does not come back by itself after a [FocusChange.Loss], so the episode ends
 *   only when the session clears it (a play, a pause, or the end of the session).
 * - **Auto-resume.** Only an [InterruptionTier.Interruption] that ends within [AUTO_RESUME_WINDOW] of
 *   its start, with the resume not cancelled, resumes on its own. The window is checked lazily at
 *   the end from the marks the signals carry, so there is no timer for it.
 * - **Blips.** A [InterruptionTier.Blip] that outlasts [BLIP_SPEECH_THRESHOLD] escalates: the
 *   reducer calls [escalateBlip] when its timer fires.
 *
 * [mode] outlives the episode: it is the latest known audio mode, and while it is not
 * [AudioMode.Normal] nothing may start playing ([isCallBlocking]).
 *
 * @param tier the highest severity seen; `null` while no episode runs.
 * @param startedAt when the episode's first loss arrived.
 * @param focusLoss the latest focus loss still open; `null` while the session holds focus.
 * @param isMicSilenced another app took the microphone and the session's capture receives silence.
 * @param hasPickedUpCall a call was picked up during this episode.
 * @param isBlipLong the blip outlasted [BLIP_SPEECH_THRESHOLD] while nothing was speaking, so the
 * next thing to start speaking treats it as an interruption.
 * @param isResumeCancelled something else took over the session (a user pause, an output
 * disconnect, a voice-answer pause, a dialog), so the end of the episode resumes nothing.
 */
data class InterruptionEpisode(
    val mode: AudioMode = AudioMode.Normal,
    val tier: InterruptionTier? = null,
    val startedAt: ComparableTimeMark? = null,
    val focusLoss: FocusChange? = null,
    val isMicSilenced: Boolean = false,
    val hasPickedUpCall: Boolean = false,
    val isBlipLong: Boolean = false,
    val isResumeCancelled: Boolean = false,
) {
    val isActive: Boolean get() = tier != null

    /** The episode is severe enough to hold the session: everything above a [InterruptionTier.Blip]. */
    val isHolding: Boolean get() = tier != null && tier >= InterruptionTier.Interruption

    /** A call rings or runs, so no play may start anything. */
    val isCallBlocking: Boolean get() = mode != AudioMode.Normal

    /** A blip that lasted past the speech threshold, waiting to interrupt whatever speaks next. */
    val isLongBlip: Boolean get() = tier == InterruptionTier.Blip && isBlipLong

    /** Folds one [signal], stamped [at], into the episode. */
    fun onSignal(signal: AudioEnvironmentSignal, at: ComparableTimeMark): InterruptionStep = when (signal) {
        is AudioModeChanged -> copy(mode = signal.mode).settle(this, at)
        is FocusChanged -> onFocusChanged(signal.change, at)
        MicSilenced -> copy(isMicSilenced = true).settle(this, at)
        MicUnsilenced -> copy(isMicSilenced = false).settle(this, at)
        OutputDisconnected -> InterruptionStep(this)
    }

    /** The end of the episode resumes nothing from now on. A no-op while no episode runs. */
    fun cancelResume(): InterruptionEpisode = if (isActive) copy(isResumeCancelled = true) else this

    /** The blip outlasted [BLIP_SPEECH_THRESHOLD] while nothing was speaking. */
    fun markBlipLong(): InterruptionEpisode = if (tier == InterruptionTier.Blip) copy(isBlipLong = true) else this

    /** A [InterruptionTier.Blip] that lasted too long becomes an [InterruptionTier.Interruption]; anything else stays. */
    fun escalateBlip(): InterruptionStep {
        if (tier != InterruptionTier.Blip) return InterruptionStep(this)
        val change = EpisodeChange.Escalated(from = InterruptionTier.Blip, to = InterruptionTier.Interruption)
        return InterruptionStep(copy(tier = InterruptionTier.Interruption), change)
    }

    /** Ends the episode from outside, such as by a play after a takeover. The latest [mode] stays known. */
    fun cleared(): InterruptionEpisode = InterruptionEpisode(mode = mode)

    private fun onFocusChanged(change: FocusChange, at: ComparableTimeMark): InterruptionStep = when {
        change != FocusChange.Gain -> copy(focusLoss = change).settle(this, at)
        // Focus does not come back after a takeover; a gain with no episode is a delayed grant.
        tier == null || tier == InterruptionTier.Takeover -> InterruptionStep(this)
        else -> copy(focusLoss = null).settle(this, at)
    }

    /** `this` is the episode with the signal applied; [previous] is the episode before it. */
    private fun settle(previous: InterruptionEpisode, at: ComparableTimeMark): InterruptionStep {
        val isHeld = focusLoss != null || isMicSilenced
        val previousTier = previous.tier
        if (previousTier == null && !isHeld) return InterruptionStep(this)
        val isCallPickedUp = hasPickedUpCall || mode == AudioMode.InCall || mode == AudioMode.InCommunication
        val raised = listOfNotNull(
            previousTier,
            focusLoss?.tier,
            InterruptionTier.Interruption.takeIf { isMicSilenced || mode == AudioMode.Ringtone },
            InterruptionTier.Call.takeIf { isCallPickedUp },
        ).max()
        val settled = copy(tier = raised, hasPickedUpCall = isCallPickedUp, startedAt = previous.startedAt ?: at)
        return when {
            previousTier == null -> InterruptionStep(settled, EpisodeChange.Started(raised))
            !isHeld && raised != InterruptionTier.Takeover -> InterruptionStep(cleared(), EpisodeChange.Ended(settled.endAt(at)))
            raised > previousTier -> InterruptionStep(settled, EpisodeChange.Escalated(previousTier, raised))
            else -> InterruptionStep(settled)
        }
    }

    private fun endAt(at: ComparableTimeMark): EpisodeEnd = when (tier) {
        InterruptionTier.Blip -> EpisodeEnd.BlipPassed
        InterruptionTier.Interruption -> when {
            isResumeCancelled -> EpisodeEnd.Cancelled
            at - requireNotNull(startedAt) <= AUTO_RESUME_WINDOW -> EpisodeEnd.AutoResume
            else -> EpisodeEnd.Expired
        }
        InterruptionTier.Call, InterruptionTier.Takeover, null -> EpisodeEnd.CallEnded
    }

    companion object {
        /** A blip that lasts longer while something speaks interrupts it. */
        val BLIP_SPEECH_THRESHOLD: Duration = 1500.milliseconds

        /** The capture gate stays closed this long after a blip ends, while its reverb and the capture buffer drain. */
        val CAPTURE_GATE_TAIL: Duration = 300.milliseconds

        /** An interruption resumes on its own only when it ends within this long of its start. */
        val AUTO_RESUME_WINDOW: Duration = 60.seconds

        /** A blip that gates a listening window for this long cancels the round instead. */
        val LISTENING_BLIP_ESCALATION: Duration = 60.seconds
    }
}

private val FocusChange.tier: InterruptionTier?
    get() = when (this) {
        FocusChange.Gain -> null
        FocusChange.LossCanDuck -> InterruptionTier.Blip
        FocusChange.LossTransient -> InterruptionTier.Interruption
        FocusChange.Loss -> InterruptionTier.Takeover
    }
