package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.XpConfig

/**
 * Serves the XP configuration. The source of truth is a server-owned document that the
 * `submitStudySession` Cloud Function scores every session with; the client keeps the last copy it
 * fetched so its local XP preview uses the same rates, offline included.
 */
interface XpConfigRepository {

    /**
     * The last fetched server configuration, or [XpConfig]'s bundled defaults when none has been
     * fetched yet. Local only: never touches the network. The implementation never fails; the [Result]
     * stays so [com.rossomak.flashcards.core.domain.usecase.GetXpConfigUseCase]'s fallback keeps a
     * session startable even if a future source could.
     */
    suspend fun getXpConfig(): Result<XpConfig>

    /**
     * Fetches the server configuration and, when it is valid, keeps it as the copy [getXpConfig]
     * serves. Best-effort: a failed or invalid fetch is logged and leaves the kept copy unchanged. Never
     * throws, except for cancellation.
     */
    suspend fun refreshXpConfig()
}
