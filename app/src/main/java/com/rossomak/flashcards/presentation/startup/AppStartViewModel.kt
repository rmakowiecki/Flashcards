package com.rossomak.flashcards.presentation.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.domain.usecase.ClearDynamicShortcutsUseCase
import com.rossomak.flashcards.core.domain.usecase.IsSignedInUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveAuthUserUseCase
import com.rossomak.flashcards.core.domain.usecase.SyncDynamicShortcutsUseCase
import com.rossomak.flashcards.core.domain.usecase.SyncFlashcardCacheGenerationUseCase
import com.rossomak.flashcards.presentation.shortcut.ShortcutDestination
import com.rossomak.flashcards.presentation.shortcut.ShortcutDestination.ToCategoryDetails
import com.rossomak.flashcards.presentation.shortcut.ShortcutDestination.ToSubcategoryDetails
import com.rossomak.flashcards.presentation.shortcut.ShortcutRouteResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppStartViewModel @Inject constructor(
    private val isSignedIn: IsSignedInUseCase,
    private val observeAuthUser: ObserveAuthUserUseCase,
    private val syncFlashcardCacheGeneration: SyncFlashcardCacheGenerationUseCase,
    private val syncDynamicShortcuts: SyncDynamicShortcutsUseCase,
    private val clearDynamicShortcuts: ClearDynamicShortcutsUseCase,
    private val shortcutRouteResolver: ShortcutRouteResolver,
) : ViewModel() {

    val startupState: StateFlow<AppStartupState> = flow {
        emit(AppStartupState.Ready(authenticated = isSignedIn()))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppStartupState.Loading
    )

    private val navigationState = MutableStateFlow(AppNavigationState())
    private var shortcutTapJob: Job? = null

    private val shortcutEventChannel = Channel<ShortcutDestination>(Channel.BUFFERED)

    /** Details screens a warm shortcut tap opens on top of whatever is showing. */
    val shortcutEvents: Flow<ShortcutDestination> = shortcutEventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            syncFlashcardCacheGeneration()
        }
        viewModelScope.launch {
            // Dynamic shortcuts mirror per-user favorites, so this tracks live auth changes rather than startupState's one-shot check
            // a sign-out mid-session must clear shortcuts from the previous session immediately, not just on the next cold start
            // collectLatest cancels syncDynamicShortcuts()'s never-completing favorites collection as soon as the uid flips, before the next branch runs.
            observeAuthUser()
                .map { authUser -> authUser?.takeUnless { it.isAnonymous }?.uid }
                .distinctUntilChanged()
                .collectLatest { authenticatedUid ->
                    if (authenticatedUid != null) {
                        syncDynamicShortcuts()
                    } else {
                        clearDynamicShortcuts()
                    }
                }
        }
    }

    fun onNavigationStateChange(state: AppNavigationState) {
        navigationState.value = state
    }

    /** A shortcut tapped while the app is running; see `docs/design/launcher-shortcuts.md` for when it is dropped. A newer tap replaces one still resolving. */
    fun onShortcutTap(route: String) {
        shortcutTapJob?.cancel()
        shortcutTapJob = viewModelScope.launch {
            if (!isSignedIn() || !navigationState.value.allowsShortcut()) return@launch
            val destination = shortcutRouteResolver.resolve(route) ?: return@launch
            // Checked again: a Study Session may have started while the route resolved.
            val state = navigationState.value
            if (state.allowsShortcut() && !destination.isShownBy(state.top)) {
                shortcutEventChannel.send(destination)
            }
        }
    }

    private fun AppNavigationState.allowsShortcut(): Boolean = top != TopDestination.Launch && !isStudySessionInBackStack

    private fun ShortcutDestination.isShownBy(top: TopDestination): Boolean = when (this) {
        is ToCategoryDetails -> top == TopDestination.CategoryDetails(route.categoryId)
        is ToSubcategoryDetails -> top == TopDestination.SubcategoryDetails(route.subcategoryId)
    }
}
