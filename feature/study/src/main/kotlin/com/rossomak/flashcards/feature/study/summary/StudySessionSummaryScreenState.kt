package com.rossomak.flashcards.feature.study.summary

import com.rossomak.flashcards.core.domain.model.LevelProgress
import com.rossomak.flashcards.core.domain.model.ScoringState
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.ui.composables.level.FlashcardsLevelCardState
import com.rossomak.flashcards.core.ui.composables.level.FlashcardsLevelCardState.Content as LevelCardContent
import com.rossomak.flashcards.core.ui.composables.level.FlashcardsLevelCardState.Loading as LevelCardLoading
import com.rossomak.flashcards.core.ui.composables.level.FlashcardsLevelCardState.Unavailable as LevelCardUnavailable

/**
 * XP and Level fields keep their defaults until [scoreStatus] is [StudySessionSummaryScoreStatus.Scored].
 * [masteredCount] is zero for Fast, so pick the Fast variant off [mode]. [xpLines] omits zero amounts.
 *
 * [levelCard] waits for the auth user, like Home's. Scored, it shows the score's own Level position, which
 * later [liveLevel] values never move; otherwise it follows [liveLevel].
 */
data class StudySessionSummaryScreenState(
    val mode: StudyMode = StudyMode.Rated,
    val durationSeconds: Int = 0,
    val studiedCount: Int = 0,
    val abandoned: Boolean = false,
    val masteredCount: Int = 0,
    val headline: StudySessionSummaryHeadline = StudySessionSummaryHeadline.GreatWork,
    val scoreStatus: StudySessionSummaryScoreStatus = StudySessionSummaryScoreStatus.Loading,
    val xpLines: List<XpBreakdownLine> = emptyList(),
    val xpTotal: Int = 0,
    val level: Int = ScoringState.STARTING_LEVEL,
    val xpIntoCurrentLevel: Long = 0,
    val xpForNextLevel: Long = 0,
    val levelsCrossed: List<Int> = emptyList(),
    val levelBefore: Int = ScoringState.STARTING_LEVEL,
    val xpIntoCurrentLevelBefore: Long = 0,
    val xpForNextLevelBefore: Long = 0,
    val currentStreak: Int = 0,
    val liveLevel: LevelProgress? = null,
    val isLiveLevelUnavailable: Boolean = false,
    val hasAuthUserResolved: Boolean = false,
    val photoUrl: String? = null,
    val displayName: String? = null,
    val activeDialog: StudySessionSummaryDialog? = null,
) {
    val levelCard: FlashcardsLevelCardState
        get() = when {
            scoreStatus == StudySessionSummaryScoreStatus.Scored -> when {
                hasAuthUserResolved -> LevelCardContent(level, xpIntoCurrentLevel, xpForNextLevel, photoUrl, displayName)
                else -> LevelCardLoading
            }
            liveLevel != null && hasAuthUserResolved ->
                LevelCardContent(liveLevel.level, liveLevel.xpIntoCurrentLevel, liveLevel.xpForNextLevel, photoUrl, displayName)
            isLiveLevelUnavailable -> LevelCardUnavailable
            else -> LevelCardLoading
        }

    /** The scored card before its pour; without a score there is no pour, so [levelCard]. */
    val levelCardBefore: FlashcardsLevelCardState
        get() = when (scoreStatus) {
            StudySessionSummaryScoreStatus.Scored -> when {
                hasAuthUserResolved -> LevelCardContent(levelBefore, xpIntoCurrentLevelBefore, xpForNextLevelBefore, photoUrl, displayName)
                else -> LevelCardLoading
            }
            else -> levelCard
        }
}
