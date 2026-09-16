package com.rossomak.flashcards.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.domain.usecase.ObserveFavoriteItemsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
                .collect { favoriteItems ->
                    _state.value = _state.value.copy(favoriteItems = favoriteItems)
                }
        }
    }
}
