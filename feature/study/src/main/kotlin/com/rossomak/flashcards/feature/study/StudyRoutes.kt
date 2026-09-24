package com.rossomak.flashcards.feature.study

import com.rossomak.flashcards.core.domain.model.FlashcardSortOrder
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.model.StudySessionConfig
import com.rossomak.flashcards.core.domain.model.VoiceLabel
import com.rossomak.flashcards.core.domain.model.VoiceSettings
import com.rossomak.flashcards.core.domain.model.XpConfig
import kotlinx.serialization.Serializable

/**
 * @param difficultyMin lower bound of the difficulty filter, flattened out of an `IntRange` because
 * androidx.navigation only derives a NavType for primitives and enums — the same reason
 * [FastStudySessionRoute] and [RatedStudySessionRoute] flatten their voice settings.
 * @param difficultyMax upper bound, paired with [difficultyMin].
 * @param sortOrder **null means "nothing upstream chose an order, use my saved default"**. The
 * nullability is load-bearing: a non-null field could not tell a deliberate
 * [FlashcardSortOrder.Default] apart from an absent choice, and the Quick Session path from Category
 * Details genuinely has no list behind it to inherit an order from (ADR-0038).
 */
@Serializable
data class PreviewStudySessionRoute(
    val categoryId: String,
    val categoryName: String,
    val subcategoryIds: List<String>,
    val subcategoryNames: List<String>,
    val filterTagIds: List<String> = emptyList(),
    val difficultyMin: Int = StudySessionConfig.MIN_DIFFICULTY,
    val difficultyMax: Int = StudySessionConfig.MAX_DIFFICULTY,
    val sortOrder: FlashcardSortOrder? = null,
    val isQuickSession: Boolean = false,
) {
    val difficultyRange: IntRange get() = difficultyMin..difficultyMax
}

/**
 * Everything a Fast Study Session consumes, and nothing else
 * ([ADR-0045](../../../docs/adr/0045-separate-fast-and-rated-session-screens.md)). Rated concepts —
 * attempts, voice answering — do not appear; Fast has no path to either.
 *
 * @param readAloudEnabled the Preview screen's confirmed choice. Auto-start is conditional on this
 * flag as well as on having cards, so a session with it off never starts text-to-speech.
 * @param speechRate the Preview screen's confirmed `VoiceSettings.speechRate`, flattened onto the
 * route for the same reason as [RatedStudySessionRoute.speechRate] — androidx.navigation's typesafe
 * routes only derive a NavType for primitives and enums.
 * @param voiceId the Preview screen's confirmed `VoiceSettings.voiceId`, flattened for the same
 * reason as [speechRate].
 * @param voiceCountryCode and [voiceVariantIndex]: the confirmed voice's `VoiceLabel`, flattened the
 * same way, so the voice dialog can name the voice before the voice list loads.
 * @param categoryName and [subcategoryNames]: not used inside the session itself, only carried so
 * termination can build a complete `SessionResult` without a second lookup —
 * the same denormalize-alongside-the-id idiom `Subcategory`/`Category` already use
 * ([ADR-0014](../../../docs/adr/0014-session-stats-written-at-summary-screen.md)), and the same
 * reason [RatedStudySessionRoute] carries them.
 */
@Serializable
data class FastStudySessionRoute(
    val categoryId: String,
    val sessionTitle: String,
    val subcategoryIds: List<String>,
    val cardIds: List<String>,
    val readAloudEnabled: Boolean = false,
    val speechRate: Float = VoiceSettings().speechRate,
    val voiceId: String? = VoiceSettings().voiceId,
    val voiceCountryCode: String? = null,
    val voiceVariantIndex: Int? = null,
    val categoryName: String,
    val subcategoryNames: List<String>,
) {
    val voiceSettings: VoiceSettings
        get() = VoiceSettings(speechRate = speechRate, voiceId = voiceId, voiceLabel = voiceLabel(voiceCountryCode, voiceVariantIndex))
}

/**
 * Everything a Rated Study Session consumes, and nothing else
 * ([ADR-0045](../../../docs/adr/0045-separate-fast-and-rated-session-screens.md)). Read-aloud does
 * not appear — it is a Fast concept.
 *
 * @param voiceAnsweringEnabled the Preview screen's choice (ADR-0030). Honoured on entry, reading
 * consent as a one-shot rather than from the observed flag — the collector may not have emitted by
 * the time the cards land.
 * @param ratedAttempts the Preview screen's confirmed choice. Bounds `RatedSessionState`'s Attempts
 * limit — how many times a card may be rated before it resolves to a Terminal State on Attempts
 * exhausted.
 * @param partialRatingCardRequeueingEnabled the Preview screen's confirmed choice, read by
 * `RatedSessionState`. `true` (the default) means a Partial rating re-queues the card; `false`
 * means it finishes the card on the spot, recording Terminal Partial rather than Mastered
 * (ADR-0044).
 * @param speechRate the Preview screen's confirmed `VoiceSettings.speechRate`, session-scoped from
 * here on: a mid-session change updates only this running session (unless the user keeps it as
 * default). Flattened onto the route for the same reason as [FastStudySessionRoute.speechRate] —
 * androidx.navigation's typesafe routes only derive a NavType for primitives and enums.
 * @param voiceId the Preview screen's confirmed `VoiceSettings.voiceId`, flattened for the same
 * reason as [speechRate].
 * @param voiceCountryCode and [voiceVariantIndex]: flattened like [FastStudySessionRoute.voiceCountryCode].
 * @param categoryName and [subcategoryNames]: not used inside the session itself, only carried so
 * termination can build a complete `SessionResult` without a second lookup —
 * the same denormalize-alongside-the-id idiom `Subcategory`/`Category` already use
 * ([ADR-0014](../../../docs/adr/0014-session-stats-written-at-summary-screen.md)).
 */
@Serializable
data class RatedStudySessionRoute(
    val categoryId: String,
    val sessionTitle: String,
    val subcategoryIds: List<String>,
    val cardIds: List<String>,
    val voiceAnsweringEnabled: Boolean = false,
    val ratedAttempts: Int = StudySessionConfig.DEFAULT_RATED_ATTEMPTS,
    val partialRatingCardRequeueingEnabled: Boolean = true,
    val speechRate: Float = VoiceSettings().speechRate,
    val voiceId: String? = VoiceSettings().voiceId,
    val voiceCountryCode: String? = null,
    val voiceVariantIndex: Int? = null,
    val categoryName: String,
    val subcategoryNames: List<String>,
) {
    val voiceSettings: VoiceSettings
        get() = VoiceSettings(speechRate = speechRate, voiceId = voiceId, voiceLabel = voiceLabel(voiceCountryCode, voiceVariantIndex))
}

/**
 * The whole `SessionResult`, flattened into primitives and parallel lists — the
 * same convention [RatedStudySessionRoute]/[FastStudySessionRoute] already use for [VoiceSettings]
 * and `IntRange`. `androidx.navigation`'s typesafe routes only derive a `NavType` for primitives,
 * enums and lists of those, so `SessionResult.cardResults` becomes one parallel list per field, all
 * indexed together: [cardIds], [cardSubcategoryIds], [cardStates], [cardAttemptsUsed],
 * [cardWasPreviouslyMastered]. The `card` prefix on those five is deliberate, not decorative: a
 * `SessionResult` already has its own session-scope [subcategoryIds]/[subcategoryNames] — the
 * Subcategories the session drew from — and that is a different thing from the one Subcategory each
 * individual *card* belongs to; without the prefix the two would collide on the same field name.
 *
 * [cardIds], [cardSubcategoryIds] and [cardStates] are always present, for both modes.
 * [cardAttemptsUsed] and [cardWasPreviouslyMastered] are Rated-only — mirroring the persisted
 * document shape (ADR-0014) — and `null` for a Fast route, not lists of zeroes and falses for cards
 * that have neither concept.
 *
 * [startedAtEpochSecond] flattens `SessionResult.startedAt` (a `java.time.Instant`, not itself a
 * primitive `androidx.navigation` can carry) to the one `Long` that reconstructs it.
 *
 * [studyDateUtcOffsetMinutes] is captured once, at session start (`startStudyClock()` in
 * `FastStudySessionViewModel`/`RatedStudySessionViewModel`) — carrying it on the route, rather than
 * having the Summary ViewModel call `ZoneId.systemDefault()` fresh at submission time, keeps the
 * derived study date tied to the zone the session actually ran in even if the device's zone changes
 * before the user reaches this screen.
 *
 * `SessionResult.xpConfig` (ADR-0047's snapshot rule) is flattened the same
 * way, one `xp`-prefixed field per [XpConfig] property — [xpConfig] reassembles them. Each field
 * defaults to [XpConfig]'s own default so a call site with no scoring stake in the route
 * (existing tests construct this route directly) does not need to spell
 * every value out.
 *
 * This route is fresh-session egress only
 * ([ADR-0014](../../../docs/adr/0014-session-stats-written-at-summary-screen.md)) — the mandatory
 * exit for both Study Modes, natural end or premature exit, and nothing else. It is never used to
 * view a past session, so it carries no `sessionId`-only variant and no transcript field: neither is
 * persisted or carried past the session itself.
 */
@Serializable
data class StudySessionSummaryRoute(
    val sessionId: String,
    val mode: StudyMode,
    val startedAtEpochSecond: Long,
    val studyDateUtcOffsetMinutes: Int,
    val durationSeconds: Int,
    val abandoned: Boolean,
    val categoryId: String,
    val categoryName: String,
    val subcategoryIds: List<String>,
    val subcategoryNames: List<String>,
    val cardIds: List<String>,
    val cardSubcategoryIds: List<String>,
    val cardStates: List<FlashcardStudyProgressState>,
    val cardAttemptsUsed: List<Int>?,
    val cardWasPreviouslyMastered: List<Boolean>?,
    val xpNewCardStudied: Int = XpConfig().newCardStudied,
    val xpCardMastered: Int = XpConfig().cardMastered,
    val xpCardPartial: Int = XpConfig().cardPartial,
    val xpMasteryDefended: Int = XpConfig().masteryDefended,
    val xpCardDemastered: Int = XpConfig().cardDemastered,
    val xpSessionCompleted: Int = XpConfig().sessionCompleted,
    val xpDailyGoalMet: Int = XpConfig().dailyGoalMet,
    val xpStreakPerDay: Int = XpConfig().streakPerDay,
    val xpStreakMaxPerDay: Int = XpConfig().streakMaxPerDay,
    val xpMinuteStudied: Int = XpConfig().minuteStudied,
    val xpLevelCurveBase: Double = XpConfig().levelCurveBase,
    val xpLevelCurveExponent: Double = XpConfig().levelCurveExponent,
) {
    val xpConfig: XpConfig
        get() = XpConfig(
            newCardStudied = xpNewCardStudied,
            cardMastered = xpCardMastered,
            cardPartial = xpCardPartial,
            masteryDefended = xpMasteryDefended,
            cardDemastered = xpCardDemastered,
            sessionCompleted = xpSessionCompleted,
            dailyGoalMet = xpDailyGoalMet,
            streakPerDay = xpStreakPerDay,
            streakMaxPerDay = xpStreakMaxPerDay,
            minuteStudied = xpMinuteStudied,
            levelCurveBase = xpLevelCurveBase,
            levelCurveExponent = xpLevelCurveExponent,
        )
}

private fun voiceLabel(countryCode: String?, variantIndex: Int?): VoiceLabel? =
    if (countryCode != null && variantIndex != null) VoiceLabel(countryCode = countryCode, variantIndex = variantIndex) else null
