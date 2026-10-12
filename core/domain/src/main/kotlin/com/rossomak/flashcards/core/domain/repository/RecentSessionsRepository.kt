package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.RecentSession
import kotlinx.coroutines.flow.Flow

interface RecentSessionsRepository {

    /**
     * The signed-in User's latest Recents, newest first, at most 15. The User's Pending Sessions are
     * included with preview XP until the server records them, so a session finished offline shows at
     * once. Re-emits whenever the server's list or the Pending Sessions change; emits nothing while
     * signed out.
     */
    fun observeRecentSessions(): Flow<List<RecentSession>>
}
