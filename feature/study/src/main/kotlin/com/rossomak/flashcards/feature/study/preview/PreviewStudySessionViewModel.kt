package com.rossomak.flashcards.feature.study.preview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.common.loge
import com.rossomak.flashcards.core.common.logw
import com.rossomak.flashcards.core.domain.model.AppPermission
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.PermissionStatus
import com.rossomak.flashcards.core.domain.model.SessionSourceType.Quick
import com.rossomak.flashcards.core.domain.model.StudySessionConfig
import com.rossomak.flashcards.core.domain.model.UserPreference.HasSeenVoiceAnsweringInfo
import com.rossomak.flashcards.core.domain.model.VoiceOption
import com.rossomak.flashcards.core.domain.model.orderedBy
import com.rossomak.flashcards.core.domain.usecase.FetchPremiumUseCase
import com.rossomak.flashcards.core.domain.usecase.GetCategoryUseCase
import com.rossomak.flashcards.core.domain.usecase.GetSubcategoriesUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveInternetAvailabilityUseCase
import com.rossomak.flashcards.core.domain.usecase.ObservePermissionStatusUseCase
import com.rossomak.flashcards.core.domain.usecase.ObservePremiumUseCase
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
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.RatedSessionVoiceAnswering
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.SessionCardsSortingOrder
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.SessionVoiceSettings
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.VoiceAnsweringInfo
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.VoiceAnsweringOffline
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
    private val getCategory: GetCategoryUseCase,
    private val getSubcategories: GetSubcategoriesUseCase,
    private val selectSessionFlashcards: SelectSessionFlashcardsUseCase,
    private val sampleQuickSessionSubcategories: SampleQuickSessionSubcategoriesUseCase,
    private val observeStudySessionPreferences: ObserveStudySessionPreferencesUseCase,
    private val saveStudySessionPreference: SaveStudySessionPreferenceUseCase,
    private val observeUserPreferences: ObserveUserPreferencesUseCase,
    private val saveUserPreference: SaveUserPreferenceUseCase,
    private val observePermissionStatus: ObservePermissionStatusUseCase,
    private val requestPermission: RequestPermissionUseCase,
    private val observePremium: ObservePremiumUseCase,
    private val fetchPremium: FetchPremiumUseCase,
    private val observeInternetAvailability: ObserveInternetAvailabilityUseCase,
    private val voiceSettingsController: VoiceSettingsController,
) : ViewModel() {

    private val route = savedStateHandle.decodeRoute<PreviewStudySessionRoute>().also { decodedRoute ->
        require(decodedRoute.sourceType == Quick || decodedRoute.subcategoryIds.isNotEmpty()) {
            "A non-Quick Preview route must carry its Subcategory ids"
        }
    }

    /**
     * The Quick Session's candidate pool (id to name), held so every re-randomise samples the same
     * pool and a name lookup is indexed once rather than re-scanned per sampled id. Seeded from the
     * route's parallel id/name lists when the caller supplied them; otherwise null until
     * [resampleSubcategories] fetches it once (ADR-0056). Never fetched for a non-Quick session.
     */
    private var candidatePool: Map<String, String>? =
        route.subcategoryIds.zip(route.subcategoryNames).toMap().takeIf { it.isNotEmpty() }

    private val _state = MutableStateFlow(
        PreviewStudySessionScreenState(
            categoryName = route.categoryName,
            subcategoryNames = route.subcategoryNames,
            sourceType = route.sourceType,
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

    /** Active while Start waits on the microphone handshake, so a second tap can't stack a prompt. */
    private var sessionStartJob: Job? = null

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
     * cards are in the session on a sort-only change (docs/design/flashcard-selection.md). Order doesn't
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
     * The route can override Sort (from a browsed list, ADR-0038) and a replay's Study Mode, voice
     * answering and read-aloud, for this session only.
     */
    init {
        viewModelScope.launch {
            // Cosmetic: a failed lookup leaves the hero's fallback glyph and never blocks the session.
            getCategory(route.categoryId).onSuccess { category ->
                _state.update { it.copy(categoryIconSvg = category.iconSvg) }
            }
        }
        viewModelScope.launch {
            val defaults = observeStudySessionPreferences().first()
            hasSeenVoiceAnsweringInfo = observeUserPreferences().first().hasSeenVoiceAnsweringInfo
            _state.update { state ->
                state.copy(
                    config = state.config.copy(
                        mode = route.studyMode ?: defaults.defaultStudyMode,
                        voiceAnsweringEnabled = route.voiceAnsweringEnabled ?: defaults.voiceAnsweringEnabled,
                        ratedAttempts = defaults.ratedAttempts,
                        readAloudEnabled = route.readAloudEnabled ?: defaults.readAloudEnabled,
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
            selectCards(resampleQuickSession = route.sourceType == Quick)
        }
        viewModelScope.launch {
            observePremium().collect { isPremium -> _state.update { it.copy(isPremium = isPremium) } }
        }
        viewModelScope.launch {
            observeInternetAvailability().collect { isOnline -> _state.update { it.copy(isOnline = isOnline) } }
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
     * card draw is unaffected, so there is nothing to reselect. Closes the
     * offline dialog it was chosen from, and starts nothing: the user sees the change, then Starts.
     */
    fun onSwitchToManualAnswering() {
        _state.update { state ->
            state.copy(
                config = state.config.copy(voiceAnsweringEnabled = false),
                activeDialog = if (state.activeDialog == VoiceAnsweringOffline) null else state.activeDialog,
            )
        }
    }

    /**
     * A Quick Session whose pool never loaded has nothing to draw from, so it re-attempts the pool
     * fetch before drawing. Every other retry only redoes the draw.
     */
    fun onRetry() {
        selectCards(resampleQuickSession = route.sourceType == Quick && candidatePool == null)
    }

    /**
     * Re-randomise: rerolls the Quick Session's subcategory sample and then the card draw within
     * it. Every other selection reuses the held sample instead of re-rolling it (ADR-0040). Does
     * nothing while no pool is held: there is nothing to re-roll, and Retry owns the fetch.
     */
    fun onReshuffleSubcategories() {
        if (candidatePool == null) return
        selectCards(resampleQuickSession = true)
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
        when {
            dialog == VoiceAnsweringInfo -> {
                markVoiceAnsweringInfoSeen()
                onStartSession()
                return
            }
            dialog == VoiceAnsweringOffline -> {
                _state.update { it.copy(activeDialog = null) }
                onStartSession()
                return
            }
            dialog.turnsOnVoiceAnsweringWithoutPremium() -> {
                _state.update { it.copy(activeDialog = null) }
                _messages.tryEmit(PreviewStudySessionMessage.PremiumFeatureComingSoon)
                return
            }
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
     * Turning voice answering on while known not Premium closes the dialog, leaves the session
     * manual and says the feature is coming soon. An unknown Premium lets it through; Start asks the
     * server.
     */
    private fun PreviewDialog.turnsOnVoiceAnsweringWithoutPremium(): Boolean =
        this is RatedSessionVoiceAnswering && draftState && _state.value.isPremium == false

    /**
     * Sort is the one dialog that never needs a redraw: it reorders the already-drawn set (see
     * [lastDrawnCards]) instead of going through [selectCards] — a redraw there could silently
     * swap which cards are in the session on a sort-only change (docs/design/flashcard-selection.md). Cast
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
     *
     * No route tags means every tag, seeded here straight away rather than when the draw lands:
     * the Subcategory set is unchanged, so [PreviewStudySessionScreenState.availableTags] is
     * already the right vocabulary, and an open settings sheet goes to "All tags" in one step.
     */
    fun onResetFilters() {
        _state.update {
            it.copy(
                config = it.config.copy(
                    tagIds = route.filterTagIds.toSet().ifEmpty { it.availableTags.toSet() },
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
     * A Rated session with voice answering only leaves this screen online, with Premium confirmed
     * by the server and the microphone granted, in that order, so no one is asked for the
     * microphone for a session they can't start. Anything else keeps the user here with voice
     * answering still selected.
     *
     * Ignored while a draw runs: [selectedCardIds] hasn't caught up with the latest edit yet, and
     * the button stays enabled so a draw served from cache doesn't blink it.
     */
    fun onStartSession() {
        if (selectedCardIds.isEmpty() || sessionStartJob?.isActive == true || _state.value.isLoading) return
        sessionStartJob = viewModelScope.launch {
            if (_state.value.isMicPermissionNeeded) {
                if (!ensurePremium()) return@launch
                if (!ensureMicPermission()) return@launch
            }
            eventChannel.send(_state.value.toSessionDestination(categoryId = route.categoryId, cardIds = selectedCardIds))
        }
    }

    /**
     * Asks the server, never the followed value, which may be stale or unknown. Offline or unreachable
     * opens the offline dialog; not Premium turns voice answering off for this session and says the
     * feature is coming soon.
     */
    private suspend fun ensurePremium(): Boolean {
        if (!_state.value.isOnline) {
            _state.update { it.copy(activeDialog = VoiceAnsweringOffline) }
            return false
        }
        _state.update { it.copy(isCheckingPremium = true) }
        val result = fetchPremium()
        val isPremium = result.getOrDefault(false)
        val revertsToManual = result.isSuccess && !isPremium
        _state.update { state ->
            state.copy(
                isCheckingPremium = false,
                isPremium = result.getOrNull() ?: state.isPremium,
                config = if (revertsToManual) state.config.copy(voiceAnsweringEnabled = false) else state.config,
                activeDialog = if (result.isFailure) VoiceAnsweringOffline else state.activeDialog,
            )
        }
        if (revertsToManual) _messages.tryEmit(PreviewStudySessionMessage.PremiumFeatureComingSoon)
        return isPremium
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
     * `isLoading` is set unconditionally, not just on the initial load: [onStartSession] ignores
     * taps while it is set, since [selectedCardIds] hasn't caught up with the user's latest edit.
     *
     * The resolved Subcategories and everything drawn from them are published together when the
     * draw lands, never ahead of it: the hero keeps the previous drawn session until then, so its
     * numbers change once (see [PreviewStudySessionScreenState]).
     *
     * @param resampleQuickSession rerolls the Quick Session's Subcategory sample first, fetching the
     * pool if none is held yet. A pool that cannot be fetched sets [PreviewStudySessionScreenState.isLoadFailed].
     */
    private fun selectCards(resampleQuickSession: Boolean = false) {
        selectionJob?.cancel()
        selectionJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, isLoadFailed = false) }
            val resolved = resolveSubcategories(resampleQuickSession) ?: return@launch
            val isSingleSubcategory = resolved.names.size == 1
            val resolvedConfig = _state.value.config.let { config ->
                config.copy(
                    subcategoryIds = resolved.ids,
                    // A changed Subcategory set invalidates any tags carried from the
                    // previous one — tags belong to a single Subcategory (ADR-0030).
                    tagIds = if (resolved.ids != config.subcategoryIds) emptySet() else config.tagIds,
                )
            }
            selectSessionFlashcards(resolvedConfig.forSelection(isSingleSubcategory = isSingleSubcategory))
                .onSuccess { plan ->
                    lastDrawnCards = plan.cards
                    selectedCardIds = plan.cards.map { it.id }
                    // Tags belong to one subcategory, so a multi-subcategory session has no
                    // coherent tag vocabulary to offer (ADR-0030).
                    val availableTags = if (isSingleSubcategory) plan.poolTags else emptyList()
                    _state.update { state ->
                        // Laid over the live config, so an edit that needs no redraw (sort,
                        // switching to manual answering) made while this draw ran is kept.
                        val drawnConfig = state.config.copy(
                            subcategoryIds = resolvedConfig.subcategoryIds,
                            // Materializes "no tag filter" into every tag actually selected, the
                            // same seed SubcategoryDetails applies on its own first load — mirrored
                            // here rather than left as a dialog-open-only translation, since
                            // config.tagIds is the same field a session is drawn from (ADR-0038).
                            // Idempotent once seeded: a route-carried or user-chosen tagIds is
                            // already non-empty and is left alone.
                            tagIds = resolvedConfig.tagIds.ifEmpty { availableTags.toSet() },
                        )
                        state.copy(
                            isLoading = false,
                            isLoadFailed = false,
                            subcategoryNames = resolved.names,
                            config = drawnConfig,
                            hasDrawnSession = true,
                            selectedCardCount = plan.cards.size,
                            estimatedMinutes = plan.estimatedMinutes,
                            availableTags = availableTags,
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(isLoading = false, isLoadFailed = true, hasDrawnSession = false) }
                }
        }
    }

    /**
     * Non-Quick: the route's Subcategories as given. Quick: the sample [resampleSubcategories] holds in
     * state, rerolled only when [resampleQuickSession] asks (ADR-0040).
     *
     * @return null when the Quick pool could not be fetched, with the failure already in state.
     */
    private suspend fun resolveSubcategories(resampleQuickSession: Boolean): ResolvedSubcategories? {
        if (route.sourceType != Quick) return ResolvedSubcategories(route.subcategoryIds, route.subcategoryNames)
        if (resampleQuickSession && !resampleSubcategories()) return null
        val sampledIds = _state.value.quickSessionSampledSubcategoryIds.orEmpty()
        val pool = candidatePool.orEmpty()
        val sampledNames = sampledIds.map { id -> pool.getValue(id) }
        return ResolvedSubcategories(sampledIds, sampledNames)
    }

    /**
     * Samples a fresh Quick Session subcategory subset and holds it in state. Called on load, on
     * Re-randomise, and on Retry when no pool is held yet — every other selection reuses what's
     * already there, which is what keeps the sample stable while the user adjusts a filter, the
     * length or the sort (ADR-0040).
     *
     * Fetches the pool once when the route supplied none (ADR-0056).
     *
     * @return false when the pool could not be fetched or came back empty, with the failure in state.
     */
    private suspend fun resampleSubcategories(): Boolean {
        val pool = candidatePool ?: getSubcategories(route.categoryId)
            .onFailure { error -> loge(error) { "Failed to fetch Subcategories for the Quick Session pool" } }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?.associate { subcategory -> subcategory.id to subcategory.name }
            ?.also { fetchedPool -> candidatePool = fetchedPool }
        if (pool == null) {
            _state.update { it.copy(isLoading = false, isLoadFailed = true, hasDrawnSession = false) }
            return false
        }
        val sampledIds = sampleQuickSessionSubcategories(
            SampleQuickSessionSubcategoriesUseCase.Params(
                candidateSubcategoryIds = pool.keys.toList(),
                countRange = _state.value.config.subcategoryCountRange,
            )
        )
        _state.update { it.copy(quickSessionSampledSubcategoryIds = sampledIds) }
        return true
    }
}

/** A resolved Subcategory selection: ids plus the names they display under, kept together. */
private data class ResolvedSubcategories(val ids: List<String>, val names: List<String>)
