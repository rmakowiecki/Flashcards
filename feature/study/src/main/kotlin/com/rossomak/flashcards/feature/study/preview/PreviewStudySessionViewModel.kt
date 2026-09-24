package com.rossomak.flashcards.feature.study.preview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.common.logw
import com.rossomak.flashcards.core.domain.model.AppPermission
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.PermissionStatus
import com.rossomak.flashcards.core.domain.model.StudySessionConfig
import com.rossomak.flashcards.core.domain.model.UserPreference.HasSeenVoiceAnsweringInfo
import com.rossomak.flashcards.core.domain.model.VoiceOption
import com.rossomak.flashcards.core.domain.model.orderedBy
import com.rossomak.flashcards.core.domain.usecase.ObservePermissionStatusUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveStudySessionPreferencesUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveUserPreferencesUseCase
import com.rossomak.flashcards.core.domain.usecase.RequestPermissionUseCase
import com.rossomak.flashcards.core.domain.usecase.SampleQuickSessionSubcategoriesUseCase
import com.rossomak.flashcards.core.domain.usecase.SaveStudySessionPreferenceUseCase
import com.rossomak.flashcards.core.domain.usecase.SaveUserPreferenceUseCase
import com.rossomak.flashcards.core.domain.usecase.SelectSessionFlashcardsUseCase
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Confirm
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Dismiss
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.DraftChange
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Open
import com.rossomak.flashcards.core.ui.navigation.decodeRoute
import com.rossomak.flashcards.core.ui.voice.VoiceSettingsController
import com.rossomak.flashcards.feature.study.PreviewStudySessionRoute
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.SessionCardsSortingOrder
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.SessionVoiceSettings
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.VoiceAnsweringInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
@Suppress("LongParameterList") // one UseCase per collaborator; a holder class would only rename the sprawl.
class PreviewStudySessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val selectSessionFlashcards: SelectSessionFlashcardsUseCase,
    private val sampleQuickSessionSubcategories: SampleQuickSessionSubcategoriesUseCase,
    private val observeStudySessionPreferences: ObserveStudySessionPreferencesUseCase,
    private val saveStudySessionPreference: SaveStudySessionPreferenceUseCase,
    private val observeUserPreferences: ObserveUserPreferencesUseCase,
    private val saveUserPreference: SaveUserPreferenceUseCase,
    private val observePermissionStatus: ObservePermissionStatusUseCase,
    private val requestPermission: RequestPermissionUseCase,
    private val voiceSettingsController: VoiceSettingsController,
) : ViewModel() {

    private val route = savedStateHandle.decodeRoute<PreviewStudySessionRoute>()

    /** `route`'s parallel id/name lists, indexed once rather than re-scanned per sampled id. */
    private val candidateSubcategoryNamesById: Map<String, String> =
        route.subcategoryIds.zip(route.subcategoryNames).toMap()

    private val _state = MutableStateFlow(
        PreviewStudySessionScreenState(
            categoryName = route.categoryName,
            subcategoryNames = route.subcategoryNames,
            isQuickSession = route.isQuickSession,
            config = StudySessionConfig(
                subcategoryIds = route.subcategoryIds,
                tagIds = route.filterTagIds.toSet(),
                difficultyRange = route.difficultyRange,
            ),
        )
    )
    val state: StateFlow<PreviewStudySessionScreenState> = _state.asStateFlow()

    private val eventChannel = Channel<PreviewStudySessionDestination>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private val _messages = MutableSharedFlow<PreviewStudySessionMessage>(extraBufferCapacity = 1)
    val messages: SharedFlow<PreviewStudySessionMessage> = _messages.asSharedFlow()

    private var sessionStartInFlight = false

    /**
     * Seeded once in `init` before the first selection, so it is known before Start can ever be
     * enabled. Held in memory afterwards: acknowledging the info dialog must take effect even when
     * persisting it fails.
     */
    private var hasSeenVoiceAnsweringInfo = false

    private var permissionStatusJob: Job? = null

    /**
     * The in-flight [selectCards] job, if any. A new call cancels whatever's still running so a
     * slower, stale resolution can never land after a newer one — `subcategoryIds` and
     * [selectedCardIds] would otherwise pair up across two different resolutions.
     */
    private var selectionJob: Job? = null

    internal var selectedCardIds: List<String> = emptyList()
        private set

    /**
     * The last drawn set, held so [onDialogConfirm]'s Sort case can reorder it in place instead of
     * redrawing through [selectSessionFlashcards] — a fresh draw there would silently swap which
     * cards are in the session on a sort-only change (SYSTEMDESIGN.md:107,377). Order doesn't
     * matter here: [resolveSelectedCards] fully re-sorts before use.
     */
    private var lastDrawnCards: List<Flashcard> = emptyList()

    /**
     * Seeds the config from the user's saved defaults **before** the first [selectCards] — a
     * snapshot via [first], not a live collect: [sessionLength][StudySessionConfig.length] and
     * [sortOrder][StudySessionConfig.sortOrder] change the selection, so seeding after would
     * select twice and flash the card count, and a live collect would let a "keep as my default"
     * write from this same screen clobber the session edits the user just made. Route- and
     * session-scoped fields (subcategoryIds, tagIds, difficultyRange) are left untouched — filters
     * are exempt from defaults entirely (ADR-0030).
     *
     * Sort is the one seeded field the route can override: arriving from a browsed list, the order
     * the user was just looking at wins over the saved default (ADR-0038).
     */
    init {
        viewModelScope.launch {
            val defaults = observeStudySessionPreferences().first()
            hasSeenVoiceAnsweringInfo = observeUserPreferences().first().hasSeenVoiceAnsweringInfo
            _state.update { state ->
                state.copy(
                    config = state.config.copy(
                        mode = defaults.defaultStudyMode,
                        voiceAnsweringEnabled = defaults.voiceAnsweringEnabled,
                        ratedAttempts = defaults.ratedAttempts,
                        readAloudEnabled = defaults.readAloudEnabled,
                        partialRatingCardRequeueingEnabled = defaults.partialRatingCardRequeueingEnabled,
                        length = defaults.sessionLength,
                        // The route wins when it carries an order: the user already saw a list in
                        // it. Null means nothing upstream chose one, so the saved default applies.
                        sortOrder = route.sortOrder ?: defaults.sortOrder,
                        voiceSettings = defaults.voiceSettings,
                        subcategoryCountRange = defaults.subcategoryCountRange,
                    ),
                )
            }
            if (route.isQuickSession) {
                resampleSubcategories()
            }
            selectCards()
        }
        // Warms the process-wide voice cache so the voice dialogs, here and in the session, open complete.
        voiceSettingsController.loadVoices(viewModelScope, ::onVoicesLoaded)
    }

    /**
     * Restarts the microphone status collection. The status flow is cold and never polls, so each
     * resume — first composition, return from system Settings, or the end of a system prompt — is
     * what picks up a change made outside the app.
     */
    fun onResume() {
        permissionStatusJob?.cancel()
        permissionStatusJob = viewModelScope.launch {
            observePermissionStatus(AppPermission.RecordAudio).collect { status ->
                _state.update { it.copy(micPermissionStatus = status) }
            }
        }
    }

    /**
     * Turns voice answering off for this session only — the saved default is left alone, and the
     * card draw is unaffected, so there is nothing to reselect.
     */
    fun onSwitchToManualAnswering() {
        _state.update { it.copy(config = it.config.copy(voiceAnsweringEnabled = false)) }
    }

    fun onRetry() {
        selectCards()
    }

    /**
     * Re-randomise: rerolls the Quick Session's subcategory sample and then the card draw within
     * it. Every other selection reuses the held sample instead of re-rolling it (ADR-0040).
     */
    fun onReshuffleSubcategories() {
        viewModelScope.launch {
            resampleSubcategories()
            selectCards()
        }
    }

    /**
     * Single entry point for every dialog on this screen.
     *
     * Voice settings is the one dialog with a side effect on open and on every edit — its draftState
     * comes from [VoiceSettingsController], not from screen state, and each edit previews — so it
     * gets its own [onDialogOpen]/[onDraftChange] rather than the flat assignment every other
     * dialog on this screen uses.
     */
    fun onDialogEvent(event: PreviewDialogEvent) {
        when (event) {
            is Open -> onDialogOpen(event.dialog)
            is DraftChange -> onDraftChange(event.dialog)
            Confirm -> onDialogConfirm()
            Dismiss -> onDialogDismiss()
        }
    }

    private fun onDialogOpen(dialog: PreviewDialog) {
        when (dialog) {
            is SessionVoiceSettings -> onVoiceSettingsOpen()
            else -> _state.update { it.copy(activeDialog = dialog) }
        }
    }

    private fun onVoiceSettingsOpen() {
        val draftState = voiceSettingsController.seedDraft(_state.value.config.voiceSettings)
        _state.update { it.copy(activeDialog = SessionVoiceSettings(draftState)) }
        voiceSettingsController.loadVoices(viewModelScope, ::onVoicesLoaded)
    }

    /** Fills the voice dialog if it is open; a dismissal in the meantime drops the list. */
    private fun onVoicesLoaded(voices: List<VoiceOption>) {
        _state.update { state ->
            val dialog = state.activeDialog as? SessionVoiceSettings ?: return@update state
            state.copy(
                activeDialog = dialog.copy(
                    draftState = dialog.draftState.copy(
                        availableVoices = voices,
                        draftVoiceId = dialog.draftState.draftVoiceId ?: voices.firstOrNull()?.id,
                    ),
                ),
            )
        }
    }

    /**
     * Stores the draftState the host built, then previews the edit when it is the voice dialog — every
     * other dialog on this screen is silent.
     */
    private fun onDraftChange(dialog: PreviewDialog) {
        val previous = _state.value.activeDialog
        _state.update { it.copy(activeDialog = dialog) }
        if (previous is SessionVoiceSettings && dialog is SessionVoiceSettings && dialog.draftState != previous.draftState) {
            voiceSettingsController.preview(dialog.draftState)
        }
    }

    /**
     * Dismissal is the discard path: the draftState dies with the field, so nothing is applied. Preview
     * playback is stopped only when it could have been started — every other dialog is silent, and
     * stopping the shared player from one of those could cut off audio this screen never began.
     *
     * The voice answering info dialog has nothing to discard, but it has still been shown, so
     * dismissing it marks it seen — without continuing the Start that opened it.
     */
    private fun onDialogDismiss() {
        if (_state.value.activeDialog == VoiceAnsweringInfo) {
            markVoiceAnsweringInfoSeen()
            return
        }
        if (_state.value.activeDialog is SessionVoiceSettings) {
            voiceSettingsController.stopPreview()
        }
        _state.update { it.copy(activeDialog = null) }
    }

    /**
     * The only dialog state commit path. Every dialog does the same three things: fold the draftState
     * into the session config, persist it as a global default when the user checked "keep as my
     * default", and close.
     *
     * Selection re-runs on every confirm regardless of the checkbox — the header reads
     * "18 cards · ~12 min", and length, filters and sort all move it.
     */
    private fun onDialogConfirm() {
        val dialog = _state.value.activeDialog ?: return
        if (dialog == VoiceAnsweringInfo) {
            markVoiceAnsweringInfoSeen()
            onStartSession()
            return
        }
        val updatedConfig = _state.value.config.foldInDialog(dialog)
        dialog.toStudySessionPreferenceIfKept()?.let { preference ->
            viewModelScope.launch { saveStudySessionPreference(preference) }
        }
        if (dialog is SessionVoiceSettings) {
            voiceSettingsController.stopPreview()
        }
        _state.update { it.copy(config = updatedConfig, activeDialog = null) }
        resolveSelectedCards(dialog)
    }

    /**
     * Sort is the one dialog that never needs a redraw: it reorders the already-drawn set (see
     * [lastDrawnCards]) instead of going through [selectCards] — a redraw there could silently
     * swap which cards are in the session on a sort-only change (SYSTEMDESIGN.md:107,377). Cast
     * size and pool are untouched by a sort change, so the Sort branch never needs to touch
     * `isLoading`, `selectedCardCount` or `estimatedMinutes`. Its own function purely to keep
     * [onDialogConfirm]'s cyclomatic complexity under detekt's threshold.
     */
    private fun resolveSelectedCards(dialog: PreviewDialog) {
        if (dialog is SessionCardsSortingOrder) {
            val reordered = lastDrawnCards.orderedBy(dialog.draftState)
            lastDrawnCards = reordered
            selectedCardIds = reordered.map { it.id }
        } else {
            selectCards()
        }
    }

    /**
     * Restores the filters the screen was originally handed — `route.filterTagIds`/
     * `route.difficultyRange` — discarding any in-screen narrowing. Does not open the Filters
     * dialog: this is a direct reset, not a shortcut into editing.
     */
    fun onResetFilters() {
        _state.update {
            it.copy(
                config = it.config.copy(
                    tagIds = route.filterTagIds.toSet(),
                    difficultyRange = route.difficultyRange,
                ),
            )
        }
        selectCards()
    }

    /**
     * Closes the info dialog; OK then continues the Start that opened it, dismissal does not. The
     * seen flag takes effect in memory straight away; a failed write only means the notice shows
     * again on a later launch.
     */
    private fun markVoiceAnsweringInfoSeen() {
        hasSeenVoiceAnsweringInfo = true
        _state.update { it.copy(activeDialog = null) }
        viewModelScope.launch {
            saveUserPreference(HasSeenVoiceAnsweringInfo(true))
                .onFailure { error -> logw(error) { "Failed to persist voice answering info seen flag" } }
        }
    }

    /**
     * A Rated session with voice answering only leaves this screen with the microphone granted —
     * anything else keeps the user here with voice answering still selected.
     */
    fun onStartSession() {
        if (selectedCardIds.isEmpty() || sessionStartInFlight) return
        sessionStartInFlight = true
        viewModelScope.launch {
            if (_state.value.isMicPermissionNeeded && !ensureMicPermission()) {
                sessionStartInFlight = false
                return@launch
            }
            eventChannel.send(_state.value.toSessionDestination(categoryId = route.categoryId, cardIds = selectedCardIds))
        }
    }

    /**
     * The privacy notice comes first, once ever; only then the system prompt, which returns
     * without prompting when the microphone is already granted.
     *
     * The result only steers this tap; [PreviewStudySessionScreenState.micPermissionStatus] is
     * written solely by the observed status, which the gateway re-emits after every request. A
     * refusal that was already permanent before this tap shows no prompt at all, so it gets a
     * snackbar instead of a silent no-op.
     */
    private suspend fun ensureMicPermission(): Boolean {
        if (!hasSeenVoiceAnsweringInfo) {
            _state.update { it.copy(activeDialog = VoiceAnsweringInfo) }
            return false
        }
        val statusBeforeRequest = _state.value.micPermissionStatus
        val status = requestPermission(AppPermission.RecordAudio)
        if (statusBeforeRequest == PermissionStatus.PermanentlyDenied && status == PermissionStatus.PermanentlyDenied) {
            _messages.tryEmit(PreviewStudySessionMessage.MicPermissionStillDenied)
        }
        return status == PermissionStatus.Granted
    }

    /**
     * `isLoading` is set unconditionally, not just on the initial load: [canStart] gates the Start
     * button on it, and a resample or filter change needs that same gate — otherwise Start stays
     * clickable against a [selectedCardIds] that hasn't caught up with the Subcategory set just
     * written to `config.subcategoryIds` below.
     */
    private fun selectCards() {
        selectionJob?.cancel()
        selectionJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val resolved = resolveSubcategories()
            _state.update { state ->
                state.copy(
                    subcategoryNames = resolved.names,
                    config = state.config.copy(
                        subcategoryIds = resolved.ids,
                        // A changed Subcategory set invalidates any tags carried from the
                        // previous one — tags belong to a single Subcategory (ADR-0030).
                        tagIds = if (resolved.ids != state.config.subcategoryIds) {
                            emptySet()
                        } else {
                            state.config.tagIds
                        },
                    ),
                )
            }
            val selectionConfig = _state.value.config.forSelection(isSingleSubcategory = _state.value.isSingleSubcategory)
            selectSessionFlashcards(selectionConfig)
                .onSuccess { plan ->
                    lastDrawnCards = plan.cards
                    selectedCardIds = plan.cards.map { it.id }
                    _state.update { state ->
                        // Tags belong to one subcategory, so a multi-subcategory session has no
                        // coherent tag vocabulary to offer (ADR-0030).
                        val availableTags = if (state.isSingleSubcategory) plan.poolTags else emptyList()
                        state.copy(
                            isLoading = false,
                            error = null,
                            selectedCardCount = plan.cards.size,
                            estimatedMinutes = plan.estimatedMinutes,
                            availableTags = availableTags,
                            // Materializes "no tag filter" into every tag actually selected, the
                            // same seed SubcategoryDetails applies on its own first load — mirrored
                            // here rather than left as a dialog-open-only translation, since
                            // config.tagIds is the same field a session is drawn from (ADR-0038).
                            // Idempotent once seeded: a route-carried or user-chosen tagIds is
                            // already non-empty and is left alone.
                            config = if (state.config.tagIds.isEmpty() && availableTags.isNotEmpty()) {
                                state.config.copy(tagIds = availableTags.toSet())
                            } else {
                                state.config
                            },
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(isLoading = false, error = "Could not load flashcards") }
                }
        }
    }

    /** A resolved Subcategory selection: ids plus the names they display under, kept together. */
    private data class ResolvedSubcategories(val ids: List<String>, val names: List<String>)

    /**
     * Every session type but Quick hands [SelectSessionFlashcardsUseCase] the fixed Subcategory
     * list the route carries. Quick is the only scenario where the Subcategory *set itself* can
     * change between resolutions, and it does so from the sample [resampleSubcategories] already
     * put in state — this never re-samples itself (ADR-0040). Sampled ids are mapped back to names
     * through [candidateSubcategoryNamesById] — the pool the sample was drawn from.
     */
    private fun resolveSubcategories(): ResolvedSubcategories {
        if (!route.isQuickSession) {
            return ResolvedSubcategories(route.subcategoryIds, route.subcategoryNames)
        }
        val sampledIds = _state.value.quickSessionSampledSubcategoryIds.orEmpty()
        val sampledNames = sampledIds.map { id -> candidateSubcategoryNamesById.getValue(id) }
        return ResolvedSubcategories(sampledIds, sampledNames)
    }

    /**
     * Samples a fresh Quick Session subcategory subset and holds it in state. Called on load and
     * on Re-randomise only — every other selection reuses what's already there, which is what
     * keeps the sample stable while the user adjusts a filter, the length or the sort (ADR-0040).
     */
    private suspend fun resampleSubcategories() {
        val sampledIds = sampleQuickSessionSubcategories(
            SampleQuickSessionSubcategoriesUseCase.Params(
                candidateSubcategoryIds = route.subcategoryIds,
                countRange = _state.value.config.subcategoryCountRange,
            )
        )
        _state.update { it.copy(quickSessionSampledSubcategoryIds = sampledIds) }
    }
}
