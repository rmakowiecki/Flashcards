package com.rossomak.flashcards.core.domain.session

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
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

/** The read-aloud pause between a card's question, read in full, and its answer. */
val QUESTION_TO_ANSWER_PAUSE: Duration = 1500.milliseconds

/** The read-aloud pause between a card's answer, read in full, and the next card. */
val ANSWER_TO_NEXT_PAUSE: Duration = 2500.milliseconds

/** Shortest time the recognized answer stays on screen before the grade or a failure replaces it. */
val MIN_TRANSCRIPT_DISPLAY: Duration = 1.seconds

/** Within this time of a card starting, "previous" goes to the previous card instead of restarting it. */
val REWIND_THRESHOLD: Duration = 3.seconds

/** How long a session whose microphone permission was revoked keeps its screen, so the user can read why, before it ends. */
val MIC_REVOKED_END_DELAY: Duration = 4.seconds

/**
 * How long a session released from a hold stays on the held card before moving on, so closing a
 * dialog never cuts straight to the next card.
 */
val RELEASE_LINGER: Duration = 500.milliseconds

/** Silence timeouts in a row that pause voice answering. */
const val CONSECUTIVE_SILENCE_PAUSE_THRESHOLD = 3

/** Grading failures in a row that pause voice answering. */
const val CONSECUTIVE_GRADING_FAILURE_PAUSE_THRESHOLD = 3
