package com.rossomak.flashcards.feature.study.voice.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.rossomak.flashcards.core.domain.model.CaptureEvent
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.PlaybackEvent
import com.rossomak.flashcards.core.domain.model.SpokenNotice
import com.rossomak.flashcards.core.domain.model.VoicePlaybackState
import com.rossomak.flashcards.core.domain.repository.StudyVoicePlaybackGateway
import com.rossomak.flashcards.core.domain.repository.VoiceCaptureGateway
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * The study session's voice stack, as both [StudyVoicePlaybackGateway] and [VoiceCaptureGateway]:
 * one instance per ViewModel, over the [StudySessionVoiceService] binder. Commands issued before the
 * asynchronous bind completes are kept and replayed once it does.
 */
@UnstableApi
@ViewModelScoped
@Suppress("TooManyFunctions") // one method per command of the two gateways it implements.
class StudySessionVoiceGateway @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : StudyVoicePlaybackGateway, VoiceCaptureGateway {

    private val _state = MutableStateFlow(VoicePlaybackState())
    override val state: StateFlow<VoicePlaybackState> = _state.asStateFlow()

    // Outlive every bind: the coordinator subscribes once, for the whole session.
    private val playbackEventChannel = Channel<PlaybackEvent>(Channel.UNLIMITED)
    override val playbackEvents: Flow<PlaybackEvent> = playbackEventChannel.receiveAsFlow()

    private val captureEventChannel = Channel<CaptureEvent>(Channel.UNLIMITED)
    override val captureEvents: Flow<CaptureEvent> = captureEventChannel.receiveAsFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val voiceBinder = MutableStateFlow<StudySessionVoiceService.LocalBinder?>(null)

    // Declared after voiceBinder, which it reads at construction.
    @OptIn(ExperimentalCoroutinesApi::class)
    override val rawVoiceLevel: Flow<Float> = voiceBinder.flatMapLatest { binder -> binder?.rawVoiceLevel ?: flowOf(0f) }

    private var binderJobs: List<Job> = emptyList()
    private var isBound = false

    // The LocalBinder carries playback state and commands, but MediaSessionService only registers
    // its session (and thus shows the notification / lock-screen controls / goes foreground) once a
    // MediaController connects via onGetSession. This controller exists purely to activate that
    // system transport surface; commands and state still flow through the binder.
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private var pendingCards: List<VoiceFlashcard> = emptyList()
    private var pendingStartIndex: Int = 0
    private var pendingSessionTitle: String = ""
    private var pendingIsVoiceAnsweringSession: Boolean = false
    private var pendingSpeechRate: Float? = null
    private var pendingVoiceId: String? = null
    private var pendingQuestionOnlyMode: Boolean? = null
    private var pendingVoiceAnswering: Boolean? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (!isBound) {
                // Rapid toggle: stop() fired before connection landed — kill the orphaned service.
                (service as? StudySessionVoiceService.LocalBinder)?.stopPlayback()
                return
            }
            val binder = service as? StudySessionVoiceService.LocalBinder ?: return
            voiceBinder.value = binder
            // Subscribed before the replay below, so an event a replayed command causes (such as a
            // refused microphone type on startVoiceAnswering) is never lost.
            observe(binder)
            binder.loadSession(pendingCards, pendingStartIndex, pendingSessionTitle, pendingIsVoiceAnsweringSession)
            // Commands can land before the async bind completes (voiceBinder was still null), so
            // replay whatever was requested in the meantime.
            pendingSpeechRate?.let { binder.setSpeechRate(it) }
            pendingVoiceId?.let { binder.setVoice(it) }
            pendingQuestionOnlyMode?.let { binder.setQuestionOnlyMode(it) }
            pendingVoiceAnswering?.let { if (it) binder.startVoiceAnswering() }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            voiceBinder.value = null
        }
    }

    override fun start(
        cards: List<Flashcard>,
        startIndex: Int,
        sessionTitle: String,
        isVoiceAnsweringSession: Boolean,
    ) {
        pendingCards = cards.toVoiceFlashcards()
        pendingStartIndex = startIndex
        pendingSessionTitle = sessionTitle
        pendingIsVoiceAnsweringSession = isVoiceAnsweringSession
        // Bind only: MediaSessionService promotes itself to a foreground service when playback
        // starts, so an explicit startForegroundService here would risk a 5s FGS-timeout ANR.
        val intent = Intent(context, StudySessionVoiceService::class.java).apply {
            action = StudySessionVoiceService.ACTION_BIND_LOCAL
        }
        isBound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        connectMediaController()
    }

    override fun updateQueue(cards: List<Flashcard>) {
        val voiceCards = cards.toVoiceFlashcards()
        pendingCards = voiceCards
        pendingStartIndex = 0
        voiceBinder.value?.updateQueue(voiceCards)
    }

    override fun stop() {
        voiceBinder.value?.stopPlayback()
        unbind()
        _state.value = VoicePlaybackState()
        pendingQuestionOnlyMode = null
        pendingVoiceAnswering = null
    }

    override fun play() {
        voiceBinder.value?.play()
    }

    override fun pause() {
        voiceBinder.value?.pause()
    }

    override fun moveToNextCard() {
        voiceBinder.value?.moveToNextCard()
    }

    override fun moveToPreviousCard() {
        voiceBinder.value?.moveToPreviousCard()
    }

    override fun jumpTo(index: Int) {
        voiceBinder.value?.jumpTo(index)
    }

    override fun restartCurrentCard() {
        voiceBinder.value?.restartCurrentCard()
    }

    override fun showAnswer() {
        voiceBinder.value?.showAnswer()
    }

    override fun advanceAfterVoiceAnswer() {
        voiceBinder.value?.advanceAfterVoiceAnswer()
    }

    override fun setQuestionOnlyMode(enabled: Boolean) {
        pendingQuestionOnlyMode = enabled
        voiceBinder.value?.setQuestionOnlyMode(enabled)
    }

    override fun setSpeechRate(rate: Float) {
        pendingSpeechRate = rate
        voiceBinder.value?.setSpeechRate(rate)
    }

    override fun setVoice(voiceId: String?) {
        pendingVoiceId = voiceId
        voiceBinder.value?.setVoice(voiceId)
    }

    /** Before the bind completes, the notice finishes at once, as on an engine that is not ready. */
    override fun speakNotice(notice: SpokenNotice) {
        val binder = voiceBinder.value
        if (binder != null) {
            binder.speakNotice(notice)
        } else {
            playbackEventChannel.trySend(PlaybackEvent.NoticeFinished(notice))
        }
    }

    override fun startVoiceAnswering() {
        pendingVoiceAnswering = true
        voiceBinder.value?.startVoiceAnswering()
    }

    override fun stopVoiceAnswering() {
        pendingVoiceAnswering = false
        voiceBinder.value?.stopVoiceAnswering()
    }

    override suspend fun awaitRouteReady() {
        voiceBinder.filterNotNull().first().awaitRouteReady()
    }

    override fun startListening() {
        voiceBinder.value?.startListening()
    }

    override fun stopListening() {
        voiceBinder.value?.stopListening()
    }

    private fun observe(binder: StudySessionVoiceService.LocalBinder) {
        binderJobs.forEach { it.cancel() }
        binderJobs = listOf(
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                binder.state.collect { _state.value = it }
            },
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                binder.playbackEvents.collect { event ->
                    // Stops the service before unbinding, so a voice-answering service never
                    // stays in the foreground with the microphone type after an engine failure.
                    if (event == PlaybackEvent.EngineUnavailable) {
                        binder.stopPlayback()
                        unbind()
                        _state.value = VoicePlaybackState()
                    }
                    playbackEventChannel.trySend(event)
                }
            },
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                binder.captureEvents.collect { captureEventChannel.trySend(it) }
            },
        )
    }

    private fun connectMediaController() {
        if (controllerFuture != null) return
        val token =
            SessionToken(context, ComponentName(context, StudySessionVoiceService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync()
    }

    private fun releaseMediaController() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    private fun unbind() {
        binderJobs.forEach { it.cancel() }
        binderJobs = emptyList()
        releaseMediaController()
        if (isBound) {
            runCatching { context.unbindService(serviceConnection) }
            isBound = false
        }
        voiceBinder.value = null
    }

    private fun List<Flashcard>.toVoiceFlashcards(): List<VoiceFlashcard> = map { card ->
        VoiceFlashcard(
            spokenQuestion = (
                card.questionSpoken?.takeIf { it.isNotBlank() }
                    ?: card.question
                ).forSpeech(),
            spokenAnswer = (
                card.answerSpoken?.takeIf { it.isNotBlank() }
                    ?: card.answer
                ).forSpeech(),
            cardId = card.id,
            questionText = card.question,
            answerText = card.answer,
        )
    }
}

private fun String.forSpeech(): String {
    // extract code span content; wraps result in single quotes for verbal separation
    val codeTransformed = replace(Regex("`([^`]*)`")) { match ->
        val inner = match.groupValues[1]
            // generic types: List<String> → "List of String"; skips standalone tags like
            // <service> (no non-ws before <); skips closing tags
            .replace(Regex("(?<=\\S)<(?!/)([^>]+)>")) { " of ${it.groupValues[1]}" }
            .replace(Regex("(?<!\\.)\\.(?!\\.)"), " DOT ") // member access dots → " DOT "; lets through ellipsis (...)
            .replace("_", " ") // snake_case separators → spaces
            .replace(Regex(" {2,}"), " ") // collapse runs of spaces left by prior replacements
            .trim()
        // single quotes in order to verbally separate the inline code from surrounding text;
        // avoids reading it as a single word
        "'$inner'"
    }
    return codeTransformed
        // XML/HTML tags → inner content; handles <tag>, </tag>, <tag />; lets through < and >
        // not forming a full tag
        .replace(Regex("</?([^>]+?)\\s*/?>")) { it.groupValues[1].trim() }
        // SCREAMING_SNAKE_CASE → lowercase words; requires at least one underscore, lets through
        // bare acronyms like HTTP
        .replace(Regex("\\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+\\b")) { it.value.lowercase().replace('_', ' ') }
        // Unicode arrows → full stop; avoid reading them as "right pointing arrow" etc.; they are
        // used as visual separators and reading them is distracting
        .replace(Regex("[→←↑↓⇒⇐⇑⇓↔⇔]"), ".")
        .replace("`", "'") // remaining stray backticks → single quotes
}
