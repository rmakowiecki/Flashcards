package com.rossomak.flashcards.feature.study.preview

import com.rossomak.flashcards.core.domain.model.PermissionStatus
import com.rossomak.flashcards.core.domain.model.SessionSourceType
import com.rossomak.flashcards.core.domain.model.SessionSourceType.Quick
import com.rossomak.flashcards.core.domain.model.SessionSourceType.SingleSubcategory
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.model.StudySessionConfig

/**
 * [config] is what the user asked for and updates on every edit. The drawn session —
 * [subcategoryNames], [selectedCardCount], [estimatedMinutes] and [availableTags] — is published in
 * one update when a draw lands, so the hero's numbers change once per edit rather than half before
 * the draw and half after it.
 */
data class PreviewStudySessionScreenState(
    val categoryName: String = "",
    val categoryIconSvg: String? = null,
    val subcategoryNames: List<String> = emptyList(),
    val sourceType: SessionSourceType = SingleSubcategory,
    val isLoading: Boolean = true,
    val isLoadFailed: Boolean = false, // A read failed, most often offline; Retry can recover.
    val config: StudySessionConfig = StudySessionConfig(subcategoryIds = emptyList()),
    /**
     * A drawn session is on screen to keep during a redraw. False before the first draw lands and
     * after a failed one, which is when a load shows the loading indicator instead.
     */
    val hasDrawnSession: Boolean = false,
    val selectedCardCount: Int = 0,
    val estimatedMinutes: Int = 0,
    /**
     * Tag vocabulary of the pool, offered by the Filters dialog. Empty for multi-subcategory
     * sessions, which filter by difficulty only (ADR-0030).
     */
    val availableTags: List<String> = emptyList(),
    /**
     * Quick Session's sampled subcategory ids, held here rather than re-derived per selection:
     * sampling runs on load and on Re-randomise only, and every other selection reuses this
     * (ADR-0040). Null for a non-Quick session, and before the first sample completes.
     */
    val quickSessionSampledSubcategoryIds: List<String>? = null,
    val activeDialog: PreviewDialog? = null,
    /** Re-read on every resume; only consulted when [isMicPermissionNeeded]. */
    val micPermissionStatus: PermissionStatus = PermissionStatus.Denied,
    /** Followed live, null until known. Start never trusts it and asks the server again. */
    val isPremium: Boolean? = null,
    val isOnline: Boolean = true,
    val isCheckingPremium: Boolean = false,
) {
    val isSingleSubcategory: Boolean get() = subcategoryNames.size == 1
    val subcategoryCount: Int get() = subcategoryNames.size
    val isQuickSession: Boolean get() = sourceType == Quick

    // Quick only: its subcategories were auto-sampled, so a fresh draw is meaningful. Custom's are
    // hand-picked by the user — nothing to reshuffle, so Custom never offers it, single-subcategory
    // or not.
    val canReshuffleSubcategories: Boolean get() = isQuickSession

    /** Only a Rated session with voice answering listens through the microphone. */
    val isMicPermissionNeeded: Boolean get() = config.mode == StudyMode.Rated && config.voiceAnsweringEnabled

    /**
     * The last request came back refused with no prompt left to show, so the hero points at
     * Settings or switching this session to manual answering. Start stays enabled and asks again:
     * that request either shows the real prompt (and clears this) or confirms the refusal.
     */
    val isMicPermissionRejected: Boolean get() = isMicPermissionNeeded && micPermissionStatus == PermissionStatus.PermanentlyDenied

    val isVoiceAnsweringOffline: Boolean get() = isMicPermissionNeeded && !isOnline

    /**
     * Not gated on [isLoading], so a redraw served from cache doesn't blink Start disabled; a tap
     * during a redraw is ignored by the ViewModel instead.
     */
    val canStart: Boolean get() = !isLoadFailed && selectedCardCount > 0
}
