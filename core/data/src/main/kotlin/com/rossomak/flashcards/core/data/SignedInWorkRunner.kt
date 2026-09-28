package com.rossomak.flashcards.core.data

import com.rossomak.flashcards.core.common.logd
import com.rossomak.flashcards.core.data.di.ApplicationScope
import com.rossomak.flashcards.core.domain.repository.AuthRepository
import com.rossomak.flashcards.core.domain.repository.XpConfigRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The one place for work that runs whenever a user becomes signed in: a fresh sign-in (Google or a
 * Guest's anonymous session), or the session Firebase restores at app start. Add such work to
 * [onSignedIn], not to the login flow, so login never waits for it.
 *
 * [start] runs once per process, from [com.rossomak.flashcards.FlashcardsApplication]. It observes the
 * auth state for the whole process and reacts to each change of signed-in uid, so a restored session
 * triggers the work once at app start, and a sign-out followed by a sign-in triggers it again. A Guest
 * linking to a real account keeps the same uid and does not trigger it.
 *
 * Every task is fire-and-forget in [applicationScope]: it never blocks another task or the UI, and
 * each task handles its own failures.
 */
@Singleton
class SignedInWorkRunner @Inject constructor(
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val authRepository: AuthRepository,
    private val xpConfigRepository: XpConfigRepository,
) {

    fun start() {
        applicationScope.launch {
            authRepository.observeAuthUser()
                .map { user -> user?.uid }
                .distinctUntilChanged()
                .filterNotNull()
                .collect { onSignedIn() }
        }
    }

    private fun onSignedIn() {
        logd { "Signed in: refreshing the XP configuration" }
        applicationScope.launch { xpConfigRepository.refreshXpConfig() }
    }
}
