package com.rossomak.flashcards.feature.account

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.feature.account.OpenSourceLicensesMessage.OpenLinkFailed
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class OpenSourceLicensesViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val state: StateFlow<OpenSourceLicensesScreenState> = savedStateHandle
        .getStateFlow<String?>(SELECTED_LIBRARY_ID_KEY, null)
        .map { libraryId -> OpenSourceLicensesScreenState(selectedLibraryId = libraryId) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = OpenSourceLicensesScreenState(selectedLibraryId = savedStateHandle[SELECTED_LIBRARY_ID_KEY]),
        )

    private val _messages = MutableSharedFlow<OpenSourceLicensesMessage>(extraBufferCapacity = 1)
    val messages: SharedFlow<OpenSourceLicensesMessage> = _messages.asSharedFlow()

    fun onLibraryClick(libraryId: String) {
        savedStateHandle[SELECTED_LIBRARY_ID_KEY] = libraryId
    }

    fun onDetailDismiss() {
        savedStateHandle[SELECTED_LIBRARY_ID_KEY] = null
    }

    /** The system found no app to open a link with. */
    fun onOpenLinkFailed() {
        _messages.tryEmit(OpenLinkFailed)
    }

    private companion object {
        const val SELECTED_LIBRARY_ID_KEY = "selectedLibraryId"
    }
}
