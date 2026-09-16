package com.rossomak.flashcards.core.data.mapper

import com.google.firebase.Timestamp
import java.time.Instant

/** Reads [Timestamp.seconds]/[Timestamp.nanoseconds] directly, preserving sub-millisecond precision that [Timestamp.toDate] would truncate. */
fun Timestamp.toInstant(): Instant = Instant.ofEpochSecond(seconds, nanoseconds.toLong())
