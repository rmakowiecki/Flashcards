package com.rossomak.flashcards.core.data.worker

import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.WorkInfo.State.BLOCKED
import androidx.work.WorkInfo.State.CANCELLED
import androidx.work.WorkInfo.State.ENQUEUED
import androidx.work.WorkInfo.State.FAILED
import androidx.work.WorkInfo.State.RUNNING
import androidx.work.WorkInfo.State.SUCCEEDED
import androidx.work.workDataOf
import com.rossomak.flashcards.core.common.logw
import com.rossomak.flashcards.core.data.mapper.toDomain
import com.rossomak.flashcards.core.data.model.DeliveredSessionDto
import com.rossomak.flashcards.core.domain.model.SessionDeliveryStatus
import com.rossomak.flashcards.core.domain.model.SessionDeliveryStatus.NotDelivered
import com.rossomak.flashcards.core.domain.model.SessionDeliveryStatus.Rejected
import com.rossomak.flashcards.core.domain.model.SessionDeliveryStatus.Scored
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The report a [SessionSubmissionDeliveryWorker] run publishes about the sessions it resolved: a JSON
 * map of session id to [DeliveredSessionDto], stored under [KEY] in the run's progress while it runs and
 * in its output once it succeeds. WorkManager caps both at [Data.MAX_DATA_BYTES], so [toData] stops
 * adding entries once the next one would not fit, and logs how many it left out. A session left out
 * reads as "run ended without this session".
 */
internal object SessionDeliveryReport {

    const val KEY = "deliveredSessions"

    // Room for Data's own framing around the one JSON string: the key, type tags and length prefixes.
    private const val DATA_FRAMING_BYTES = 512
    private const val MAX_REPORT_BYTES = Data.MAX_DATA_BYTES - DATA_FRAMING_BYTES

    // Re-encodes the whole map for each added entry on purpose: one run holds only a few sessions, and
    // measuring the real encoded size keeps the cap exact without hand-counting JSON separators.
    fun toData(deliveredSessions: Map<String, DeliveredSessionDto>): Data {
        val included = LinkedHashMap<String, DeliveredSessionDto>()
        var json = Json.encodeToString<Map<String, DeliveredSessionDto>>(included)
        for ((sessionId, deliveredSession) in deliveredSessions) {
            included[sessionId] = deliveredSession
            val candidate = Json.encodeToString<Map<String, DeliveredSessionDto>>(included)
            if (candidate.encodeToByteArray().size > MAX_REPORT_BYTES) {
                included.remove(sessionId)
                logw { "Delivery report full: left out ${deliveredSessions.size - included.size} of ${deliveredSessions.size} sessions" }
                break
            }
            json = candidate
        }
        return workDataOf(KEY to json)
    }

    fun read(data: Data): Map<String, DeliveredSessionDto> {
        val json = data.getString(KEY) ?: return emptyMap()
        return try {
            Json.decodeFromString<Map<String, DeliveredSessionDto>>(json)
        } catch (exception: SerializationException) {
            logw(exception) { "Unreadable delivery report, treating it as empty" }
            emptyMap()
        }
    }
}

/**
 * Reads [sessionId]'s delivery status off the drain run's [workInfo], or `null` while the run gives no
 * answer yet. A run that finished, was cancelled or went back to waiting after an attempt, without
 * reporting [sessionId], is [NotDelivered]: WorkManager discards the report of a run that retries. A
 * missing [workInfo] (the request was pruned) is [NotDelivered] too.
 */
internal fun sessionDeliveryStatusOf(workInfo: WorkInfo?, sessionId: String): SessionDeliveryStatus? {
    if (workInfo == null) return NotDelivered
    return when (workInfo.state) {
        RUNNING -> reportedStatus(workInfo.progress, sessionId)
        SUCCEEDED -> reportedStatus(workInfo.outputData, sessionId) ?: NotDelivered
        ENQUEUED -> if (workInfo.runAttemptCount > 0) NotDelivered else null
        BLOCKED -> null
        FAILED, CANCELLED -> NotDelivered
    }
}

private fun reportedStatus(data: Data, sessionId: String): SessionDeliveryStatus? =
    when (val deliveredSession = SessionDeliveryReport.read(data)[sessionId]) {
        is DeliveredSessionDto.Scored -> Scored(deliveredSession.score.toDomain())
        DeliveredSessionDto.Rejected -> Rejected
        null -> null
    }
