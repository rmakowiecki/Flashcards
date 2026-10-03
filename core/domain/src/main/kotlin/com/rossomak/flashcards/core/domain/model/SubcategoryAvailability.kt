package com.rossomak.flashcards.core.domain.model

/**
 * Whether a Subcategory, asked for by id, still exists.
 *
 * Only an authoritative server answer can prove a Subcategory is gone. A caller treats [Unknown] like
 * [Present], so being offline never makes a Subcategory disappear.
 */
enum class SubcategoryAvailability {
    /** Found, on the server or in the on-device cache. */
    Present,

    /** Absent from an answer the server itself gave. */
    Missing,

    /** Absent from an answer served only from the on-device cache, or the read failed. */
    Unknown,
}
