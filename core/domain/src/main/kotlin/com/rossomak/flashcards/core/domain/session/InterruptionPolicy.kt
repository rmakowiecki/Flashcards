package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.logging.DomainLogger
import com.rossomak.flashcards.core.domain.model.EpisodeChange
import com.rossomak.flashcards.core.domain.model.EpisodeEnd
import com.rossomak.flashcards.core.domain.model.InterruptionEpisode
import com.rossomak.flashcards.core.domain.model.InterruptionTier

/** The prefix every audio interruption log line starts with, so a device check can find them all. */
private const val INTERRUPTION_LOG_PREFIX = "Audio interruption: "

/** Logs one audio interruption line. Logging never affects a reducer's state or effects. */
internal fun DomainLogger.interruption(text: () -> String) = info { INTERRUPTION_LOG_PREFIX + text() }

/**
 * What both study session reducers decide the same way about an audio interruption. Each reducer
 * maps these decisions to its own pause flags and effects; the reactions of its phases and steps
 * stay its own.
 */
internal object InterruptionPolicy {

    /** Why the end of an interruption resumes nothing. */
    enum class AutoResumeSkip(val text: String) {
        SessionComplete("the session is complete"),
        PausedForAnotherReason("the session is paused for another reason"),
        CallInProgress("a call is in progress"),
    }

    /** `null` when the end of an interruption may resume the session. */
    fun autoResumeSkip(isComplete: Boolean, isPausedForAnotherReason: Boolean, episode: InterruptionEpisode): AutoResumeSkip? = when {
        isComplete -> AutoResumeSkip.SessionComplete
        isPausedForAnotherReason -> AutoResumeSkip.PausedForAnotherReason
        episode.isCallBlocking -> AutoResumeSkip.CallInProgress
        else -> null
    }

    /** A dialog closing must not play over a call or another media app that took over: from a call on, only the user's play resumes. */
    fun dropsTemporaryPause(tier: InterruptionTier): Boolean = tier >= InterruptionTier.Call

    /** Nothing may start while a call rings or runs, and a play only matters to a session that has something to start. */
    fun ignoresPlay(episode: InterruptionEpisode, hasSomethingToPlay: Boolean): Boolean = episode.isCallBlocking && hasSomethingToPlay

    fun escalationLog(change: EpisodeChange.Escalated): String = "escalated from ${change.from} to ${change.to}"

    /** The log line of an end that resumes nothing; `null` for [EpisodeEnd.AutoResume], which logs its own outcome. */
    fun endLog(end: EpisodeEnd): String? = when (end) {
        EpisodeEnd.BlipPassed -> "blip passed"
        EpisodeEnd.AutoResume -> null
        EpisodeEnd.Expired -> "auto-resume expired, the session stays paused"
        EpisodeEnd.Cancelled -> "ended, nothing to resume"
        EpisodeEnd.CallEnded -> "call ended, the session stays paused"
    }
}
