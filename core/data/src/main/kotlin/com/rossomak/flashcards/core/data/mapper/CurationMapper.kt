package com.rossomak.flashcards.core.data.mapper

import com.rossomak.flashcards.core.data.model.CurationRequestDto
import com.rossomak.flashcards.core.domain.model.CurationAction
import com.rossomak.flashcards.core.domain.model.CurationRequest

fun CurationRequestDto.toDomain(cardId: String): CurationRequest {
    val domainActions = actions.mapNotNull { (key, entry) ->
        val action = runCatching { CurationAction.valueOf(key) }.getOrNull() ?: return@mapNotNull null
        val instant = entry.flaggedAt?.toInstant() ?: return@mapNotNull null
        action to instant
    }.toMap()
    return CurationRequest(
        cardId = cardId,
        subcategoryId = subcategoryId,
        actions = domainActions,
    )
}
