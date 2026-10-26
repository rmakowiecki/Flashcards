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
     * A one-shot read, for [com.rossomak.flashcards.core.domain.usecase.SubmitStudySessionUseCase]'s
     * score. A screen that follows the scoring state live collects [observeScoringState]
     * instead.
     *
     * [excludedSessionId] names a finished session to leave out, so the score of that session never
     * builds on itself. When the cached server state already includes that session, it cannot be
     * subtracted, so the read fails.
     *
     * @return `Result.success(null)` for an account with no scoring state document and nothing pending —
     * a genuinely new user, not a failure — leaving it to the caller to start from [ScoringState]'s own
     * defaults. `Result.failure` for a real read failure, which the score must never paper
     * over with a default: guessing a low starting state would show a misleadingly small number.
     */
    suspend fun getScoringState(excludedSessionId: String? = null): Result<ScoringState?>

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
