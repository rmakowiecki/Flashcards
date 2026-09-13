package com.rossomak.flashcards.feature.study.summary

/**
 * One row of the plain itemised breakdown put on screen — count, rate and the
 * resulting product, so the row reads as arithmetic rather than a bare number. [isLoss] flags
 * [XpAwardSource.MasteryLost] for the error-colour treatment on
 * [com.rossomak.flashcards.core.ui.composables.banners.FlashcardsXpBreakdownRow]'s `tone` — derived
 * from [source] by default, but exposed as its own field rather than inferred from [amount] being
 * negative, per the animated summary's own rule (never infer tone from the sign of a number).
 *
 * A presentation-only type, deliberately not [com.rossomak.flashcards.core.domain.model.XpBreakdown]
 * itself: the domain type's fields are already-multiplied totals matching the persisted document
 * 1:1, and never omit a zero source (every field always present, matching that document's own Firestore
 * shape). This list is built from that same total plus the count and rate behind it, and drops a
 * source entirely once its [amount] is zero — a screen concern, not a storage one.
 */
data class XpBreakdownLine(
    val source: XpAwardSource,
    val count: Int,
    val rate: Int,
    val amount: Int,
    val isLoss: Boolean = source == XpAwardSource.MasteryLost,
)

enum class XpAwardSource {
    NewCards,
    Mastered,
    Partial,
    MasteryDefended,
    MasteryLost,
    TimeStudied,
    SessionCompleted,
}
