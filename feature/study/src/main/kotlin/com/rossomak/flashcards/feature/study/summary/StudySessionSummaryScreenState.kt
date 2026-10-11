package com.rossomak.flashcards.feature.study.summary

import com.rossomak.flashcards.core.domain.model.ScoringState
import com.rossomak.flashcards.core.domain.model.StudyMode

/**
 * Everything the Session Summary screen renders.
 *
 * [mode], [durationSeconds], [studiedCount], [abandoned], [masteredCount] and [headline] come from
 * the route and are set before the first frame. [masteredCount] is always zero for a Fast result,
 * which has no Terminal States, and the content chooses its Fast variant off [mode] rather than
 * inferring it from that zero.
 *
 * [scoreStatus] is [StudySessionSummaryScoreStatus.Loading] until the session's score resolves. The
 * XP fields ([xpLines], [xpTotal], the Level fields, [levelsCrossed], the before-session Level
 * fields and [currentStreak]) hold their zero defaults until it is
 * [StudySessionSummaryScoreStatus.Scored], and stay there when it is
 * [StudySessionSummaryScoreStatus.Unavailable]. [headline] starts from route data only and is
 * recomputed once a score lands, since a negative total changes it. [xpLines] never includes a
 * zero-[XpBreakdownLine.amount] entry.
 *
 * [photoUrl]/[displayName] are the signed-in User's avatar source for the Level card. Both are null
 * until the auth user arrives, and again whenever there is no signed-in User.
 *
 * [activeDialog] is the dialog on screen, if any. It lives here so a dialog survives rotation.
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
    val photoUrl: String? = null,
    val displayName: String? = null,
    val activeDialog: StudySessionSummaryDialog? = null,
)
