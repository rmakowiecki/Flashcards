package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.LevelProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class FakeLevelProgressRepository : LevelProgressRepository {
    private val levelProgressUpdates = MutableSharedFlow<LevelProgress>(replay = 1)

    /** When set, [observeLevelProgress] throws this before its first emission, like a failing snapshot listener. */
    var levelProgressReadFailure: Throwable? = null

    /** When `true`, [observeLevelProgress] completes without emitting, like a collection started while signed out. */
    var completesWithoutEmitting: Boolean = false

    /**
     * Emits [levelProgress] to every active collector and replays it to the next one, mirroring a
     * snapshot listener that re-fires after a change.
     */
    suspend fun emit(levelProgress: LevelProgress) {
        levelProgressUpdates.emit(levelProgress)
    }

    override fun observeLevelProgress(): Flow<LevelProgress> = flow {
        levelProgressReadFailure?.let { throw it }
        if (completesWithoutEmitting) return@flow
        emitAll(levelProgressUpdates)
    }
}
