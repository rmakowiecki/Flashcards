package com.rossomak.flashcards.core.data.source

interface AccountDeletionRemoteDataSource {

    suspend fun deleteAccount(): Result<Unit>
}
