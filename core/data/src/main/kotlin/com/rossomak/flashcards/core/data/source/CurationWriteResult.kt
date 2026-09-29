package com.rossomak.flashcards.core.data.source

/** Whether the server acknowledged a curation write before the caller stopped waiting for it. */
enum class CurationWriteResult {
    Confirmed,

    /** Held in Firestore's offline queue; a later server rejection cannot reach the caller. */
    Queued,
}
