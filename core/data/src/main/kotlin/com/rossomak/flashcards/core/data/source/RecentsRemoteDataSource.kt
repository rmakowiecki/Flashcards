package com.rossomak.flashcards.core.data.source

import com.rossomak.flashcards.core.data.model.RecentsStateDto
import kotlinx.coroutines.flow.Flow

interface RecentsRemoteDataSource {

    fun observeRecents(): Flow<RecentsStateDto>
}
