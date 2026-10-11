package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.LevelProgress
import kotlinx.coroutines.flow.Flow

/**
 * Follows the signed-in User's [LevelProgress] live, including their sessions that are finished but
 * not yet delivered to the server, so XP and Level count a session studied offline as soon as it is
 * queued. How that happens is the implementation's concern, not the caller's.
 *
 * For a one-shot read of the whole scoring state, see [ScoringStateRepository].
 */
interface LevelProgressRepository {

    /**
     * Never emits `null`: an account with no scoring state and nothing pending emits the starting
     * state. Emits nothing until the server has answered for an account the device has no copy of,
     * so a consumer shows its loading state rather than a false low Level.
     *
     * Completes without a further emission when the User signs out, and may complete before its first
     * emission (a signed-out start). A read failure that is not retried propagates to the collector.
     * Each collector follows the User on its own.
     */
    fun observeLevelProgress(): Flow<LevelProgress>
}
