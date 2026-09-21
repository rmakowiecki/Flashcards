package com.rossomak.flashcards.feature.study.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.rossomak.flashcards.core.domain.model.Flashcard
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@UnstableApi
class StudySessionVoiceGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : VoiceGateway {

    private val _state = MutableStateFlow(VoicePlaybackState())
    override val state: StateFlow<VoicePlaybackState> = _state.asStateFlow()

    private val _voiceAnswerState = MutableStateFlow(VoiceAnswerState())
    override val voiceAnswerState: StateFlow<VoiceAnswerState> = _voiceAnswerState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var voiceBinder: StudySessionVoiceService.LocalBinder? = null
    private var voiceStateJob: Job? = null
    private var voiceAnswerStateJob: Job? = null
    private var isBound = false

    // The LocalBinder carries playback state and commands, but MediaSessionService only registers
    // its session (and thus shows the notification / lock-screen controls / goes foreground) once a
    // MediaController connects via onGetSession. This controller exists purely to activate that
    // system transport surface; commands and state still flow through the binder.
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private var pendingCards: List<VoiceFlashcard> = emptyList()
    private var pendingStartIndex: Int = 0
    private var pendingSubcategoryName: String = ""
    private var pendingSpeechRate: Float? = null
    private var pendingVoiceId: String? = null
    private var pendingVoiceAnswering: Boolean? = null
    private var pendingNextSilenceWillPauseSession: Boolean? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (!isBound) {
                // Rapid toggle: stop() fired before connection landed — kill the orphaned service.
                (service as? StudySessionVoiceService.LocalBinder)?.stopPlayback()
                return
            }
            val binder = service as? StudySessionVoiceService.LocalBinder ?: return
            voiceBinder = binder
            binder.loadSession(pendingCards, pendingStartIndex, pendingSubcategoryName)
            // setSpeechRate/setVoice can land before the async bind completes (voiceBinder was
            // still null), so replay whatever was requested in the meantime.
            pendingSpeechRate?.let { binder.setPlaybackSpeechRate(it) }
            pendingVoiceId?.let { binder.setVoice(it) }
            pendingVoiceAnswering?.let { binder.setVoiceAnswering(it) }
            pendingNextSilenceWillPauseSession?.let { binder.setNextSilenceWillPauseSession(it) }
            collectVoiceState(binder)
            collectVoiceAnswerState(binder)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            voiceBinder = null
        }
    }

    override fun start(
        cards: List<Flashcard>,
        startIndex: Int,
        subcategoryName: String,
    ) {
        pendingCards = cards.toVoiceFlashcards()
        pendingStartIndex = startIndex
        pendingSubcategoryName = subcategoryName
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
        voiceBinder?.updateQueue(voiceCards)
    }

    override fun stop() {
        voiceBinder?.stopPlayback()
        unbind()
        _state.value = VoicePlaybackState()
        _voiceAnswerState.value = VoiceAnswerState()
        pendingVoiceAnswering = null
        pendingNextSilenceWillPauseSession = null
    }

    override fun togglePlayPause() {
        voiceBinder?.togglePlayPause()
    }

    override fun rewindToNext() {
        voiceBinder?.moveToNextCard()
    }

    override fun rewindToPrevious() {
        voiceBinder?.moveToPreviousCard()
    }

    override fun restartCurrentCard() {
        voiceBinder?.restartCurrentCardPlayback()
    }

    override fun showAnswer() {
        voiceBinder?.skipToCardAnswerPlayback()
    }

    override fun setSpeechRate(rate: Float) {
        pendingSpeechRate = rate
        voiceBinder?.setPlaybackSpeechRate(rate)
    }

    override fun setVoice(voiceId: String?) {
        pendingVoiceId = voiceId
        voiceBinder?.setVoice(voiceId)
    }

    override fun setVoiceAnswering(enabled: Boolean) {
        pendingVoiceAnswering = enabled
        voiceBinder?.setVoiceAnswering(enabled)
    }

    override fun setNextSilenceWillPauseSession(willPause: Boolean) {
        pendingNextSilenceWillPauseSession = willPause
        voiceBinder?.setNextSilenceWillPauseSession(willPause)
    }

    private fun collectVoiceState(binder: StudySessionVoiceService.LocalBinder) {
        voiceStateJob?.cancel()
        voiceStateJob = scope.launch {
            binder.state.collect { voice ->
                if (voice.error != null) {
                    unbind()
                    _state.value = VoicePlaybackState(error = voice.error)
                    return@collect
                }
                _state.value = voice
            }
        }
    }

    private fun collectVoiceAnswerState(binder: StudySessionVoiceService.LocalBinder) {
        voiceAnswerStateJob?.cancel()
        voiceAnswerStateJob = scope.launch {
            binder.voiceAnswerState.collect { _voiceAnswerState.value = it }
        }
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
        voiceStateJob?.cancel()
        voiceStateJob = null
        voiceAnswerStateJob?.cancel()
        voiceAnswerStateJob = null
        releaseMediaController()
        if (isBound) {
            runCatching { context.unbindService(serviceConnection) }
            isBound = false
        }
        voiceBinder = null
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
}
