package com.rossomak.flashcards.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.usecase.GetAppVersionUseCase
import com.rossomak.flashcards.core.domain.usecase.GetCurrentAuthUserUseCase
import com.rossomak.flashcards.core.domain.usecase.GetInstallationInfoUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveAuthUserUseCase
import com.rossomak.flashcards.core.domain.usecase.SignOutUseCase
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Confirm
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Dismiss
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.DraftChange
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Open
import com.rossomak.flashcards.feature.account.AccountDestination.ContactSupport
import com.rossomak.flashcards.feature.account.AccountDestination.Login
import com.rossomak.flashcards.feature.account.AccountDestination.OpenSourceLicenses
import com.rossomak.flashcards.feature.account.AccountDestination.ReportBug
import com.rossomak.flashcards.feature.account.AccountDialog.SignOut
import com.rossomak.flashcards.feature.account.AccountMessage.NoEmailApp
import com.rossomak.flashcards.feature.account.AccountMessage.OpenLinkFailed
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val observeAuthUser: ObserveAuthUserUseCase,
    private val getAppVersion: GetAppVersionUseCase,
    private val getInstallationInfo: GetInstallationInfoUseCase,
    private val getCurrentAuthUser: GetCurrentAuthUserUseCase,
    private val signOut: SignOutUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountScreenState())
    val state: StateFlow<AccountScreenState> = _state.asStateFlow()

    private val eventChannel = Channel<AccountDestination>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private val _messages = MutableSharedFlow<AccountMessage>(extraBufferCapacity = 1)
    val messages: SharedFlow<AccountMessage> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            val appVersion = getAppVersion()
            _state.update { it.copy(appVersion = appVersion) }
        }
        viewModelScope.launch {
            // A null emission only comes while the screen is on its way out (sign-out), so the last
            // user stays rather than blanking the header during the exit transition.
            observeAuthUser().filterNotNull().collect(::applyUser)
        }
    }

    private fun applyUser(user: AuthUser) {
        _state.update {
            it.copy(
                displayName = user.displayName,
                email = user.email,
                photoUrl = user.photoUrl,
            )
        }
    }

    /** The system found no app to open a link with. */
    fun onOpenLinkFailed() {
        _messages.tryEmit(OpenLinkFailed)
    }

    fun onContactSupportClick() {
        viewModelScope.launch {
            val installationInfo = getInstallationInfo()
            val uid = getCurrentAuthUser()?.uid
            eventChannel.send(ContactSupport(installationInfo, uid))
        }
    }

    fun onReportBugClick() {
        viewModelScope.launch { eventChannel.send(ReportBug) }
    }

    fun onOpenSourceLicensesClick() {
        eventChannel.trySend(OpenSourceLicenses)
    }

    /** The system found no email app to open the support draft with. */
    fun onNoEmailAppFound() {
        _messages.tryEmit(NoEmailApp)
    }

    /** Single entry point for every dialog on this screen. */
    fun onDialogEvent(event: AccountDialogEvent) {
        when (event) {
            is Open -> _state.update { it.copy(activeDialog = event.dialog) }
            is DraftChange -> _state.update { it.copy(activeDialog = event.dialog) }
            Confirm -> onDialogConfirm()
            Dismiss -> _state.update { it.copy(activeDialog = null) }
        }
    }

    /** The only commit path: each dialog variant answers with its own action. */
    private fun onDialogConfirm() {
        when (_state.value.activeDialog) {
            SignOut -> {
                _state.update { it.copy(activeDialog = null) }
                performSignOut()
            }
            null -> Unit
        }
    }

    private fun performSignOut() {
        viewModelScope.launch {
            try {
                signOut()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Navigate to Login even if sign-out throws, so the user is never stuck signed in.
            } finally {
                eventChannel.send(Login)
            }
        }
    }
}
