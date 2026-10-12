package com.rossomak.flashcards.core.domain.model

import kotlinx.serialization.Serializable

/**
 * The Study Creation entry point that started a Study Session, not its Subcategory count: a one-Subcategory
 * Quick sample stays [Quick], a one-Subcategory Custom selection is [SingleSubcategory]. `name` is persisted.
 */
@Serializable
enum class SessionSourceType {
    SingleSubcategory,
    Quick,
    Custom,
}
