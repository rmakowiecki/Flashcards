package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.ScoringState
import kotlinx.coroutines.flow.Flow

/**
 * Reads the User's account-wide [ScoringState] singleton, `progress/user-stats`, including the User's
 * sessions that are finished but not yet delivered to the server, so XP, Level, Streak and Daily Goal
 * count a session studied offline at once; how that happens is the implementation's concern, not the
 * caller's.
 *
 * Writing is not exposed here, nor anywhere else on the client: the server-authoritative
 * `submitStudySession` Cloud Function is the sole writer of this document, computing and overwriting
 * the whole next [ScoringState] inside its own Firestore transaction.
 */
interface ScoringStateRepository {

    /**
     * A one-shot read for [com.rossomak.flashcards.core.domain.usecase.SubmitStudySessionUseCase]'s
     * score; [observeScoringState] follows it live.
     *
     * [excludedSessionId] leaves that session out, and fails the read when the cached state already
     * includes it. [cacheOnly] reads only what the device holds, never the server: a scoring state it holds
     * nothing for fails the read.
     *
     * @return `null` for a new account with nothing pending. A failure is never replaced by a default,
     * since a guessed low starting state would show a misleading number.
     */
    suspend fun getScoringState(excludedSessionId: String? = null, cacheOnly: Boolean = false): Result<ScoringState?>

    /**
     * Follows the signed-in User's [ScoringState] live, emitting only when it changes.
     *
     * Never emits `null`: an account with no scoring state and nothing pending emits `ScoringState()`.
     * Emits nothing until the server has answered for an account the device has no copy of, so a
     * consumer shows its loading state rather than a false low Level.
     *
     * Completes without a further emission when the User signs out, and may complete before its first
     * emission (a signed-out start). A read failure that is not retried propagates to the collector.
     * Each collector follows the User on its own.
     */
    fun observeScoringState(): Flow<ScoringState>
}
