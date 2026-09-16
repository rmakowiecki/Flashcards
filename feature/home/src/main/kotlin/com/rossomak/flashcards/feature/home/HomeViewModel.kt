package com.rossomak.flashcards.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.domain.usecase.ObserveFavoriteItemsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val observeFavoriteItems: ObserveFavoriteItemsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeScreenState())
    val state: StateFlow<HomeScreenState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            observeFavoriteItems()
                // Firestore rejects the listener with PERMISSION_DENIED once sign-out clears
                // auth mid-collection; viewModelScope isn't tied to auth state, so swallow it
                // here instead of crashing — the screen is about to navigate away anyway.
                .catch { }
                .collect { favoriteItems ->
                    _state.value = _state.value.copy(favoriteItems = favoriteItems)
                }
        }
    }
}
