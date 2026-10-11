package com.rossomak.flashcards.feature.study.summary

/**
 * One row of the itemised XP breakdown, shown in the breakdown dialog.
 *
 * What a row carries depends on [source]:
 * - the multiplied sources ([XpAwardSource.NewCards] to [XpAwardSource.TimeStudied]) carry [count]
 *   and [rate], and [amount] is their product;
 * - [XpAwardSource.Streak] carries the current Streak as [count] and no [rate];
 * - [XpAwardSource.SessionCompleted] and [XpAwardSource.DailyGoal] carry neither.
 *
 * [isLoss] flags [XpAwardSource.MasteryLost] for the error-colour treatment, derived from [source]
 * by default but exposed as its own field rather than inferred from [amount] being negative: tone is
 * never read off the sign of a number.
 *
 * A presentation-only type, deliberately not [com.rossomak.flashcards.core.domain.model.XpBreakdown]
 * itself: the domain type's fields are already-multiplied totals matching the persisted document
 * 1:1, and never omit a zero source. This list is built from that same total plus the count and rate
 * behind it, and drops a source entirely once its [amount] is zero, a screen concern and not a
 * storage one.
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
