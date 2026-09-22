package com.rossomak.flashcards.feature.study.fast

import androidx.annotation.StringRes
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog
import com.rossomak.flashcards.feature.study.voice.VoicePlaybackState

/**
 * Everything a Fast Study Session screen renders. No Study Mode field — the type itself is the
 * mode
 * ([ADR-0045](../../../../../../../../docs/adr/0045-separate-fast-and-rated-session-screens.md)).
 *
 * Deliberately duplicates the shape of `RatedStudySessionScreenState` rather than sharing a base
 * type with it: Rated gains an attempt counter and a per-card ledger in the next spec in the
 * sequence, and a shared base would need a `when` on mode to stay useful — exactly the branching
 * this split exists to remove.
 */
data class FastStudySessionScreenState(
    val sessionTitle: String = "",
    val isLoading: Boolean = false,
    val flashcards: List<Flashcard> = emptyList(),
    val currentCardIndex: Int = 0,
    val isAnswerRevealed: Boolean = false,
    @param:StringRes val error: Int? = null,
    // Routed at session start (FastStudySessionRoute.readAloudEnabled) — known synchronously,
    // unlike isVoiceActive below, which only flips once the voice engine finishes binding. The
    // sheet must never show the manual-mode perspective for a read-aloud session, even for the
    // moment between entry and that bind completing, so it branches on this flag instead of
    // isVoiceActive.
    val isReadAloudMode: Boolean = false,
    val isVoiceActive: Boolean = false,
    val isVoicePlaying: Boolean = false,
    val isVoiceAutoStartPending: Boolean = false,
    val speechRate: Float = VoicePlaybackState.DEFAULT_SPEECH_RATE,
    // Deferred: one-shot snackbar trigger held as screen state — violates AGENTS.md's SharedFlow-
    // for-transient-events rule. Migrate to a SharedFlow<FastStudySessionMessage>.
    val voiceError: String? = null,
    // Deferred: same violation as voiceError above — migrate together.
    @param:StringRes val curationError: Int? = null,
    val activeDialog: StudySessionDialog? = null,
) {
    val currentCard: Flashcard? get() = flashcards.getOrNull(currentCardIndex)
}
