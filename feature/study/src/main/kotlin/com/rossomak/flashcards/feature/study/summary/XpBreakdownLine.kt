package com.rossomak.flashcards.feature.study.summary

/**
 * [XpAwardSource.NewCards] to [XpAwardSource.TimeStudied] carry [count] and [rate]; [XpAwardSource.Streak]
 * carries the Streak as [count]; the other bonuses carry neither. Tone comes from [isLoss], never from the
 * sign of [amount].
 */
data class XpBreakdownLine(
    val source: XpAwardSource,
    val count: Int?,
    val rate: Int?,
    val amount: Int,
    val isLoss: Boolean = source == XpAwardSource.MasteryLost,
)

/** Declared in the order the breakdown lists its lines. */
enum class XpAwardSource {
    NewCards,
    Mastered,
    Partial,
    MasteryDefended,
    MasteryLost,
    TimeStudied,
    Streak,
    SessionCompleted,
    DailyGoal,
}
