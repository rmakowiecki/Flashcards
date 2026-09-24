package com.rossomak.flashcards.feature.study.preview

import com.rossomak.flashcards.core.domain.model.PermissionStatus
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.model.StudySessionConfig

data class PreviewStudySessionScreenState(
    val categoryName: String = "",
    val subcategoryNames: List<String> = emptyList(),
    val isQuickSession: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val config: StudySessionConfig = StudySessionConfig(subcategoryIds = emptyList()),
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
) {
    val isSingleSubcategory: Boolean get() = subcategoryNames.size == 1
    val subcategoryCount: Int get() = subcategoryNames.size

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

    val canStart: Boolean get() = !isLoading && error == null && selectedCardCount > 0
}
