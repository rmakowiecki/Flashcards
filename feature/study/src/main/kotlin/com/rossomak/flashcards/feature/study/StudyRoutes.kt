package com.rossomak.flashcards.feature.study

import com.rossomak.flashcards.core.domain.model.FlashcardSortOrder
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.SessionSourceType
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.model.StudySessionConfig
import com.rossomak.flashcards.core.domain.model.VoiceLabel
import com.rossomak.flashcards.core.domain.model.VoiceSettings
import kotlinx.serialization.Serializable

/**
 * [sourceType] and [subcategoryIds] together say how the Preview screen treats the Subcategories
 * ([ADR-0056](../../../docs/adr/0056-preview-resolves-quick-session-candidate-pool.md)):
 *
 * | sourceType | ids | Meaning |
 * |---|---|---|
 * | Quick | present | The caller's candidate pool; Preview samples it and makes no fetch |
 * | Quick | empty | Preview fetches the Category's Subcategories itself, then samples them |
 * | SingleSubcategory / Custom | present | Used literally |
 * | SingleSubcategory / Custom | empty | Nothing to study; Preview shows the unavailable message and its error state |
 *
 * For SingleSubcategory and Custom, Preview first checks that the ids still exist and drops any the
 * server confirms are gone. An id it cannot check, for example offline, is kept.
 *
 * Quick ids mean "the Category's complete Subcategory list, or none". A partial list is not
 * validated and would silently give a Quick Session over only that subset.
 *
 * @param subcategoryNames parallel to [subcategoryIds].
 * @param difficultyMin lower bound of the difficulty filter, flattened out of an `IntRange` because
 * androidx.navigation only derives a NavType for primitives and enums — the same reason
 * [FastStudySessionRoute] and [RatedStudySessionRoute] flatten their voice settings.
 * @param difficultyMax upper bound, paired with [difficultyMin].
 * @param sortOrder **null means "nothing upstream chose an order, use my saved default"**. The
 * nullability is load-bearing: a non-null field could not tell a deliberate
 * [FlashcardSortOrder.Default] apart from an absent choice, and the Quick Session path from Category
 * Details genuinely has no list behind it to inherit an order from (ADR-0038).
 * @param studyMode, [voiceAnsweringEnabled] and [readAloudEnabled]: starting values for this session
 * only, as when replaying a past session. Like [sortOrder], **null means "use my saved default"**, and
 * a value is never saved unless the User ticks "keep as default". Flattened to primitives and enums
 * for the same NavType reason as [difficultyMin]. A Rated replay leaves [readAloudEnabled] null, so
 * switching it to Fast takes read-aloud from the saved default.
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
    val sourceType: SessionSourceType,
    val studyMode: StudyMode? = null,
    val voiceAnsweringEnabled: Boolean? = null,
    val readAloudEnabled: Boolean? = null,
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
 * @param sourceType how the session was created (Study Creation entry point).
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
    val sourceType: SessionSourceType,
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
 * @param sourceType how the session was created (Study Creation entry point).
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
    val sourceType: SessionSourceType,
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
 * [sourceType] is always present. [voiceAnsweringEnabled] is Rated-only and [readAloudEnabled] is
 * Fast-only, each `null` on the other mode's route, the same split as [cardAttemptsUsed].
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
    val sourceType: SessionSourceType,
    val cardIds: List<String>,
    val cardSubcategoryIds: List<String>,
    val cardStates: List<FlashcardStudyProgressState>,
    val cardAttemptsUsed: List<Int>?,
    val cardWasPreviouslyMastered: List<Boolean>?,
    val voiceAnsweringEnabled: Boolean?,
    val readAloudEnabled: Boolean?,
)

private fun voiceLabel(countryCode: String?, variantIndex: Int?): VoiceLabel? =
    if (countryCode != null && variantIndex != null) VoiceLabel(countryCode = countryCode, variantIndex = variantIndex) else null
