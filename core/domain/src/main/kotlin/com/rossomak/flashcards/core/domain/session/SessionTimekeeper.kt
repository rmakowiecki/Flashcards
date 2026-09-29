package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.model.SessionClock
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.sealSessionResult
import com.rossomak.flashcards.core.domain.model.startClock
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * A session's identity and wall-clock time, shared by both study session coordinators.
 *
 * The clock starts once, when the first card is on screen, and never pauses: v1 counts the wall
 * time from the first card shown to the end, whatever the backgrounding or playback state.
 */
internal class SessionTimekeeper(private val clock: Clock) {

    // Nothing restores a session after an app kill (no in-progress persistence, by design), so the
    // id only has to outlive the coordinator's own ViewModel.
    val sessionId: String = UUID.randomUUID().toString()

    // null while the clock never started: a session whose cards failed to load banks no time.
    var startedAt: Instant? = null
        private set

    // Captured once at the start instant: a timezone change mid-session must not shift the
    // streak/daily-goal study date the server derives from it.
    var utcOffsetMinutes: Int = 0
        private set

    private var sessionClock = SessionClock()

    fun start() {
        val instant = clock.instant()
        startedAt = instant
        utcOffsetMinutes = ZoneId.systemDefault().rules.getOffset(instant).totalSeconds / SECONDS_PER_MINUTE
        sessionClock = startClock(sessionClock, instant)
    }

    /**
     * Stamps the real duration onto the result [build] makes from the end instant. A session that
     * never started its clock reports that instant as its start and zero duration.
     */
    fun seal(build: (at: Instant) -> SessionResult): SessionResult {
        val at = clock.instant()
        return sealSessionResult(result = build(at), clock = sessionClock, at = at)
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60
    }
}
