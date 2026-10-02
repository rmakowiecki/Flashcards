package com.rossomak.flashcards.core.data.model

import kotlinx.serialization.Serializable

/**
 * Full-fidelity, lossless mirror of a domain
 * [com.rossomak.flashcards.core.domain.model.SessionResult] for the local durable delivery queue
 * — carries every field the domain type carries, both `Rated`/`Fast` variants.
 * **Independent of** [com.rossomak.flashcards.core.data.source.FirebaseSessionSubmissionRemoteDataSource]'s
 * own wire payload: this DTO's job is a lossless round trip through an app restart, not matching
 * what the network call sends.
 *
 * [mode] and [startedAtEpochMillis] use the same string/epoch-millis encoding as the network payload
 * (`StudyMode.name`, `Instant.toEpochMilli()`) rather than relying on kotlinx.serialization's default
 * enum/`Instant` handling, for the same reason: no contextual serializer to wire up, and one
 * unsurprising encoding this whole data layer already uses elsewhere. [sourceType] likewise holds
 * `SessionSourceType.name`, and has no default: a line without it fails to decode.
 *
 * [voiceAnsweringEnabled] is present only for a `Rated` entry and [readAloudEnabled] only for a `Fast`
 * one, each `null` for the other mode, the same split as [PendingFlashcardResultDto.attemptsUsed].
 *
 * [uid] is the User who finished the session, stamped at append time. It is queue metadata, not part
 * of the domain [com.rossomak.flashcards.core.domain.model.SessionResult]:
 * [com.rossomak.flashcards.core.data.worker.SessionSubmissionDeliveryWorker] delivers an entry only
 * while that same User is signed in. It defaults to blank only so an entry queued before this field
 * existed still decodes; [PendingSessionSubmissionMapper.toDomain] rejects a blank [uid] as malformed.
 *
 * See [PendingSessionSubmissionMapper] for the `toDto()`/`toDomain()` conversions, and
 * [com.rossomak.flashcards.core.data.source.FilePendingSessionSubmissionLocalDataSource] for where
 * these get persisted, one JSON-encoded entry per line.
 *
 * **A field added to [com.rossomak.flashcards.core.domain.model.SessionResult] needs updating in two
 * independent places, not just one**: here (plus [PendingSessionSubmissionMapper]) for the durable
 * queue, and separately in [com.rossomak.flashcards.core.data.source.FirebaseSessionSubmissionRemoteDataSource]'s
 * own `toPayload()` for the network wire shape — the two are deliberately independent (this one is
 * full-fidelity, that one is whatever the function accepts), so neither can be derived from the other,
 * and nothing enforces they stay in sync beyond this note.
 */
@Serializable
data class PendingSessionSubmissionDto(
    val id: String,
    val uid: String = "",
    val mode: String,
    val startedAtEpochMillis: Long,
    val durationSeconds: Int,
    val abandoned: Boolean,
    val categoryId: String,
    val categoryName: String,
    val subcategoryIds: List<String>,
    val subcategoryNames: List<String>,
    val sourceType: String,
    val cardResults: List<PendingFlashcardResultDto>,
    val studyDate: String,
    val dailyGoalMinutes: Int,
    val studyDateUtcOffsetMinutes: Int,
    val voiceAnsweringEnabled: Boolean? = null,
    val readAloudEnabled: Boolean? = null,
)

/**
 * Mirrors [com.rossomak.flashcards.core.domain.model.FlashcardResult]'s two variants in one shape:
 * [attemptsUsed] and [wasPreviouslyMastered] are `null` for a `Fast` entry, present for a `Rated`
 * one — genuinely absent, not zeroed/falsed, matching the domain type's own sealed split.
 */
@Serializable
data class PendingFlashcardResultDto(
    val cardId: String,
    val subcategoryId: String,
    val state: String,
    val attemptsUsed: Int? = null,
    val wasPreviouslyMastered: Boolean? = null,
)
