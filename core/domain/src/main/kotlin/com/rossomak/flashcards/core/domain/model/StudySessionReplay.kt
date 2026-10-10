package com.rossomak.flashcards.core.domain.model

import com.rossomak.flashcards.core.domain.model.SessionSourceType.Custom
import com.rossomak.flashcards.core.domain.model.SessionSourceType.Quick
import com.rossomak.flashcards.core.domain.model.SessionSourceType.SingleSubcategory

/**
 * What the Preview Study Session Screen needs to study a finished session's thing again: its scope, Study
 * Mode and delivery. Carries no filters, sort or length: Preview takes those from the saved defaults, and the
 * past session's mode and delivery are starting values only, never saved as defaults.
 *
 * Quick carries no Subcategories, so Preview samples the Category afresh (ADR-0056). Only the past mode's
 * delivery flag is set: [voiceAnsweringEnabled] for Rated, [readAloudEnabled] for Fast, the other `null`.
 */
data class StudySessionReplay(
    val categoryId: String,
    val categoryName: String,
    val sourceType: SessionSourceType,
    val subcategoryIds: List<String>,
    val subcategoryNames: List<String>,
    val studyMode: StudyMode,
    val voiceAnsweringEnabled: Boolean?,
    val readAloudEnabled: Boolean?,
) {
    companion object {
        /** Applies the Quick rule and the delivery split to a finished session's identity. */
        @Suppress("LongParameterList") // one primitive per field: callers hold the parts of a route or a Recent, not a replay.
        fun of(
            categoryId: String,
            categoryName: String,
            sourceType: SessionSourceType,
            subcategoryIds: List<String>,
            subcategoryNames: List<String>,
            studyMode: StudyMode,
            voiceAnsweringEnabled: Boolean?,
            readAloudEnabled: Boolean?,
        ): StudySessionReplay {
            val keepsSubcategories = when (sourceType) {
                SingleSubcategory, Custom -> true
                Quick -> false
            }
            return StudySessionReplay(
                categoryId = categoryId,
                categoryName = categoryName,
                sourceType = sourceType,
                subcategoryIds = if (keepsSubcategories) subcategoryIds else emptyList(),
                subcategoryNames = if (keepsSubcategories) subcategoryNames else emptyList(),
                studyMode = studyMode,
                voiceAnsweringEnabled = voiceAnsweringEnabled.takeIf { studyMode == StudyMode.Rated },
                readAloudEnabled = readAloudEnabled.takeIf { studyMode == StudyMode.Fast },
            )
        }
    }
}

/** The replay of this Recent: its stored scope, Study Mode and delivery. */
fun RecentSession.toReplay(): StudySessionReplay = StudySessionReplay.of(
    categoryId = categoryId,
    categoryName = categoryName,
    sourceType = sourceType,
    subcategoryIds = subcategoryIds,
    subcategoryNames = subcategoryNames,
    studyMode = mode,
    voiceAnsweringEnabled = (this as? RecentSession.Rated)?.voiceAnsweringEnabled,
    readAloudEnabled = (this as? RecentSession.Fast)?.readAloudEnabled,
)
