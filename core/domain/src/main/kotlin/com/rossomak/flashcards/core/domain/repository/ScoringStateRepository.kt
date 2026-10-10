package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.ScoringState

/**
 * Reads the User's account-wide [ScoringState] singleton, `progress/user-stats`, including the User's
 * sessions that are finished but not yet delivered to the server, so XP and Level count a session
 * studied offline at once; how that happens is the implementation's concern, not the caller's.
 *
 * Writing is not exposed here, nor anywhere else on the client: the server-authoritative
 * `submitStudySession` Cloud Function is the sole writer of this document, computing and overwriting
 * the whole next [ScoringState] inside its own Firestore transaction.
 *
 * A one-shot read, for [com.rossomak.flashcards.core.domain.usecase.SubmitStudySessionUseCase]'s
 * fallback preview. A screen that shows the Level live reads [LevelProgressRepository] instead.
 *
 * @return `Result.success(null)` for an account with no scoring state document and nothing pending — a
 * genuinely new user, not a failure — leaving it to the caller to start from [ScoringState]'s own
 * defaults. `Result.failure` for a real read failure, which the fallback preview must never paper over
 * with a default: guessing a low starting state would show a misleadingly small number.
 */
interface ScoringStateRepository {

    suspend fun getScoringState(): Result<ScoringState?>
}
