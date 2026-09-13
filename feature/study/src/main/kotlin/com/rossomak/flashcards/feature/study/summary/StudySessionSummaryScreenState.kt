package com.rossomak.flashcards.feature.study.summary

import com.rossomak.flashcards.core.domain.model.ScoringState
import com.rossomak.flashcards.core.domain.model.StudyMode

/**
 * Everything the Session Summary screen renders — deliberately plain: mode,
 * duration, how many cards were studied, whether the deck was completed or the session was
 * abandoned, and the three Terminal State counts. [masteredCount]/[partialCount]/[failedCount] are
 * always zero for a Fast result — Fast never produces those outcomes — and the content chooses the
 * reduced Fast variant off [mode] rather than inferring it from the counts being zero.
 *
 * [xpLines]/[xpTotal]/[level]/[xpIntoCurrentLevel]/[xpForNextLevel] all stay at
 * their zero defaults until the session's commit resolves — the state is populated from the route
 * synchronously, before that I/O-dependent calculation can possibly have run. [xpLines] never
 * includes a zero-[XpBreakdownLine.amount] entry; a future redesign replaces this plain
 * list with the animated pour, against content this makes real for the first time.
 */
data class StudySessionSummaryScreenState(
    val mode: StudyMode = StudyMode.Rated,
    val durationSeconds: Int = 0,
    val studiedCount: Int = 0,
    val abandoned: Boolean = false,
    val masteredCount: Int = 0,
    val partialCount: Int = 0,
    val failedCount: Int = 0,
    val xpLines: List<XpBreakdownLine> = emptyList(),
    val isLoading: Boolean = true,
    val xpTotal: Int = 0,
    val level: Int = ScoringState.STARTING_LEVEL,
    val xpIntoCurrentLevel: Long = 0,
    val xpForNextLevel: Long = 0,
)
