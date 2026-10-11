package com.rossomak.flashcards.feature.study.summary

import com.rossomak.flashcards.core.domain.model.LevelProgress
import com.rossomak.flashcards.core.domain.model.ScoringState
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.ui.composables.level.FlashcardsLevelCardState
import com.rossomak.flashcards.core.ui.composables.level.FlashcardsLevelCardState.Content as LevelCardContent
import com.rossomak.flashcards.core.ui.composables.level.FlashcardsLevelCardState.Loading as LevelCardLoading
import com.rossomak.flashcards.core.ui.composables.level.FlashcardsLevelCardState.Unavailable as LevelCardUnavailable

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
 * [levelCard] is the one state the Level card renders, the same type Home's card renders. A
 * [StudySessionSummaryScoreStatus.Scored] Summary draws the score's own Level position, frozen once it
 * lands: the score is this session's payoff, so a later stream value never moves it. Without a score the
 * card follows the live Level stream like Home's: [FlashcardsLevelCardState.Loading] until [liveLevel]
 * and the auth user have both emitted, then content, and [FlashcardsLevelCardState.Unavailable] when
 * [isLiveLevelUnavailable] is set before that. Only a Summary with a settled score status shows the card.
 *
 * [liveLevel] is the latest value of the live Level stream, the one Home's card reads; `null` until it
 * emits. It keeps its last value if the stream later fails. [isLiveLevelUnavailable] is set when the
 * stream or the auth user fails, or the stream ends, before the card reached content.
 *
 * [photoUrl]/[displayName] are the signed-in User's avatar source for the Level card. Both are null
 * until the auth user arrives ([hasAuthUserResolved] says whether it has), and again whenever there is
 * no signed-in User.
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
    val liveLevel: LevelProgress? = null,
    val isLiveLevelUnavailable: Boolean = false,
    val hasAuthUserResolved: Boolean = false,
    val photoUrl: String? = null,
    val displayName: String? = null,
    val activeDialog: StudySessionSummaryDialog? = null,
) {
    val levelCard: FlashcardsLevelCardState
        get() = when {
            scoreStatus == StudySessionSummaryScoreStatus.Scored ->
                LevelCardContent(level, xpIntoCurrentLevel, xpForNextLevel, photoUrl, displayName)
            liveLevel != null && hasAuthUserResolved ->
                LevelCardContent(liveLevel.level, liveLevel.xpIntoCurrentLevel, liveLevel.xpForNextLevel, photoUrl, displayName)
            isLiveLevelUnavailable -> LevelCardUnavailable
            else -> LevelCardLoading
        }

    /**
     * The card a scored Summary shows before its pour: the score's Level position before the session.
     * Without a score there is no pour, so this is [levelCard].
     */
    val levelCardBefore: FlashcardsLevelCardState
        get() = when (scoreStatus) {
            StudySessionSummaryScoreStatus.Scored ->
                LevelCardContent(levelBefore, xpIntoCurrentLevelBefore, xpForNextLevelBefore, photoUrl, displayName)
            else -> levelCard
        }
}
