package com.rossomak.flashcards.core.domain.session

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How long a listening window waits for speech, from the moment the microphone records, before the round counts as a silence. */
val SILENCE_TIMEOUT: Duration = 8.seconds

/** How long a listening window waits for the microphone to be prepared, such as a Bluetooth headset connecting. */
val ROUTE_READY_TIMEOUT: Duration = 5.seconds

/**
 * How long a listening window waits, once the capture route is ready, for the microphone to report
 * it records. A guard only: the capture normally opens well within it or reports its own failure.
 */
val MICROPHONE_OPEN_TIMEOUT: Duration = 3.seconds

/** The pause after an advancing notice finishes, before the next question is read (ADR-0025). */
val NOTICE_TAIL: Duration = 1.seconds

/** Shortest time the recognized answer stays on screen before the grade or a failure replaces it. */
val MIN_TRANSCRIPT_DISPLAY: Duration = 1.seconds

/** Within this time of a card starting, "previous" goes to the previous card instead of restarting it. */
val REWIND_THRESHOLD: Duration = 3.seconds

/** Silence timeouts in a row that pause voice answering. */
const val CONSECUTIVE_SILENCE_PAUSE_THRESHOLD = 3

/** Grading failures in a row that pause voice answering. */
const val CONSECUTIVE_GRADING_FAILURE_PAUSE_THRESHOLD = 3
