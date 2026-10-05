package com.rossomak.flashcards.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.domain.usecase.GetAppVersionUseCase
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Confirm
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Dismiss
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.DraftChange
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Open
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val getAppVersion: GetAppVersionUseCase,
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
    }

    /** Single entry point for every dialog on this screen. */
    fun onDialogEvent(event: AccountDialogEvent) {
        when (event) {
            is Open -> _state.update { it.copy(activeDialog = event.dialog) }
            is DraftChange -> _state.update { it.copy(activeDialog = event.dialog) }
            // No dialog commits anything yet: each variant added to AccountDialog adds its own confirm.
            Confirm -> Unit
            Dismiss -> _state.update { it.copy(activeDialog = null) }
        }
    }
}
