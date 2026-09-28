package com.rossomak.flashcards.feature.study.voice.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.rossomak.flashcards.core.data.voice.VoiceCuration
import com.rossomak.flashcards.core.domain.model.PlaybackEvent
import com.rossomak.flashcards.core.domain.model.TransportCommand
import com.rossomak.flashcards.core.domain.model.VoicePhase
import com.rossomak.flashcards.core.domain.model.VoicePlaybackState
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Media3 [SimpleBasePlayer] that reads flashcards aloud with the system [TextToSpeech] engine and
 * surfaces playback to a `MediaSession` (lock screen / Bluetooth / notification transport controls,
 * audio focus). It owns the TTS engine and the per-card sequence:
 * speak question -> pause -> speak answer -> pause -> next card.
 *
 * Only the study session coordinators drive it, through [StudySessionVoiceService.LocalBinder]. System
 * transport controls reach Media3's `handle*` overrides, which never act: they report a
 * [PlaybackEvent.ExternalCommand] through [onEvent], and the coordinator applies it exactly like the
 * matching in-app command. Every change calls [publishState] to refresh both the Media3 state and
 * the [voiceState] side-channel.
 *
 * [voiceState] carries TTS-specific fields ([VoicePhase], between-card pause) that the standard
 * [Player] state cannot express.
 *
 * It makes no session decision. Audio-focus auto-pause and auto-resume are the one behavior it
 * still runs by itself.
 */
@UnstableApi
class TtsPlayer(
    context: Context,
    private val onEvent: (PlaybackEvent) -> Unit,
) : SimpleBasePlayer(Looper.getMainLooper()) {

    private val _voiceState = MutableStateFlow(VoicePlaybackState())
    val voiceState: StateFlow<VoicePlaybackState> = _voiceState.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wasPlayingBeforeFocusLoss = false

    private var ttsReady = false
    private var startWhenReady = false

    private var cards: List<VoiceFlashcard> = emptyList()
    private var index = 0
    private var phase = VoicePhase.Question
    private var isPlaying = false
    private var isBetweenPause = false
    private var speechRate = VoicePlaybackState.DEFAULT_SPEECH_RATE
    private var pendingVoiceId: String? = null
    private var subcategoryName = ""

    // Rated voice answering (ADR-0025): stops after each question instead of going on to the
    // answer, and reports QuestionFinished so the coordinator can open the listening window.
    // Distinct from Fast mode's continuous question->pause->answer->next loop.
    private var isQuestionOnlyMode = false

    /**
     * Incremented on every new utterance and every interrupting command. An [onDone] callback whose
     * embedded generation no longer matches has been superseded (e.g. by a pause or skip) and is
     * ignored — this is how a naturally finished utterance is told apart from a stopped one.
     */
    private var generation = 0

    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            runCatching {
                tts.language = Locale.US // app supports English voice only
            }
            applyVoice(pendingVoiceId)
            tts.setSpeechRate(speechRate)
            tts.setOnUtteranceProgressListener(utteranceListener)
            if (startWhenReady) {
                startWhenReady = false
                speakQuestion()
            }
        } else {
            onEvent(PlaybackEvent.EngineUnavailable)
        }
    }

    /**
     * Media3 manages audio focus only for [androidx.media3.exoplayer.ExoPlayer]; a custom
     * [SimpleBasePlayer] must do it manually. Focus is requested once and held for the playback
     * lifetime (never abandoned on pause — abandoning lets a defensively-paused app such as Spotify
     * grab the slot), then auto-pauses on loss and auto-resumes on the following gain.
     *
     * A permanent [AudioManager.AUDIOFOCUS_LOSS] is the exception: the system drops our request from
     * the focus stack, so it is forgotten here and the next play requests focus again. It never
     * auto-resumes.
     */
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (wasPlayingBeforeFocusLoss) {
                    wasPlayingBeforeFocusLoss = false
                    doPlay()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                audioFocusRequest = null
                wasPlayingBeforeFocusLoss = false
                if (isPlaying) doPause()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (isPlaying) {
                    wasPlayingBeforeFocusLoss = true
                    doPause() // stay registered — AUDIOFOCUS_GAIN fires when the other app releases
                }
            }
        }
    }

    override fun getState(): State {
        val items = cards.mapIndexed { cardIndex, _ ->
            MediaItemData.Builder("card-$cardIndex")
                .setMediaItem(MediaItem.Builder().setMediaId("card-$cardIndex").build())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(subcategoryName.ifBlank { DEFAULT_TITLE })
                        .setArtist("${cardIndex + 1} / ${cards.size}")
                        .build()
                )
                .build()
        }
        return State.Builder()
            .setAvailableCommands(AVAILABLE_COMMANDS)
            .setPlaybackState(if (cards.isEmpty()) STATE_IDLE else STATE_READY)
            .setPlayWhenReady(isPlaying, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaylist(items)
            .setCurrentMediaItemIndex(index.coerceIn(0, maxOf(0, cards.lastIndex)))
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        onEvent(PlaybackEvent.ExternalCommand(if (playWhenReady) TransportCommand.Play else TransportCommand.Pause))
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleStop(): ListenableFuture<*> {
        onEvent(PlaybackEvent.ExternalCommand(TransportCommand.Stop))
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        releaseEngine()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int
    ): ListenableFuture<*> {
        val command = when (seekCommand) {
            COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> TransportCommand.Next
            COMMAND_SEEK_TO_PREVIOUS -> TransportCommand.Previous
            COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> TransportCommand.PreviousCard
            else -> if (mediaItemIndex != index) TransportCommand.JumpTo(mediaItemIndex) else null
        }
        command?.let { onEvent(PlaybackEvent.ExternalCommand(it)) }
        return Futures.immediateVoidFuture()
    }

    fun loadAndStartSession(cards: List<VoiceFlashcard>, startIndex: Int, subcategoryName: String) {
        this.cards = cards
        this.subcategoryName = subcategoryName
        this.index = if (cards.isEmpty()) 0 else startIndex.coerceIn(0, cards.lastIndex)
        this.phase = VoicePhase.Question
        this.isBetweenPause = false
        if (cards.isEmpty()) {
            publishState()
            return
        }
        if (ttsReady) speakQuestion() else startWhenReady = true
    }

    /**
     * Swaps in a fresh queue order without touching the in-flight utterance, TTS engine, or
     * `MediaSession` — only [cards]/[index] change. [cards]'s head is always whatever card is
     * currently speaking, so this always resets [index] to 0.
     */
    fun updateQueue(cards: List<VoiceFlashcard>) {
        this.cards = cards
        this.index = 0
        publishState()
    }

    /** Toggles between Fast's continuous auto-advance and Rated voice answering's stop-after-question shape. */
    fun setQuestionOnlyMode(enabled: Boolean) {
        isQuestionOnlyMode = enabled
        publishState()
    }

    /**
     * Reads the question of `cards[0]`, or stops when the queue is empty. The coordinator always
     * hands over the reordered queue first, so `cards[0]` is already the next card to ask; unlike
     * Fast's fixed-list advance, this never increments [index].
     */
    fun advanceAfterVoiceAnswer() {
        if (cards.isNotEmpty()) {
            index = 0
            speakQuestion()
        } else {
            isPlaying = false
            publishState()
        }
    }

    /**
     * Starts or resumes reading. Named apart from [Player.play], which the Media3 session routes to
     * [handleSetPlayWhenReady]. Clears an auto-resume pending from a transient focus loss: the user
     * decided.
     */
    fun startReading() {
        wasPlayingBeforeFocusLoss = false
        doPlay()
    }

    /** Pauses reading; the counterpart of [startReading]. */
    fun pauseReading() {
        wasPlayingBeforeFocusLoss = false
        doPause()
    }

    fun moveToNextCard() {
        if (index >= cards.lastIndex) return
        index++
        moveToQuestion()
    }

    fun moveToPreviousCard() {
        if (index <= 0) return
        index--
        moveToQuestion()
    }

    fun restartCurrentCardPlayback() = moveToQuestion()

    fun skipToCardAnswerPlayback() {
        if (cards.isEmpty()) return
        phase = VoicePhase.Answer
        if (isPlaying) {
            speakAnswer()
        } else {
            stopUtterance()
            publishState()
        }
    }

    fun setVoice(voiceId: String?) {
        pendingVoiceId = voiceId
        if (ttsReady) {
            applyVoice(voiceId)
            if (isPlaying) {
                when (phase) {
                    VoicePhase.Question -> speakQuestion()
                    VoicePhase.Answer -> speakAnswer()
                }
            }
        }
    }

    /**
     * [voiceId] `null` means "no explicit choice yet" — resolves to a curated English voice,
     * never the device's system default (which may not even be English).
     */
    private fun applyVoice(voiceId: String?) {
        val resolved = voiceId?.let { id -> tts.voices?.firstOrNull { it.name == id } }
            ?: VoiceCuration.curate(tts.voices.orEmpty()).firstOrNull()
        if (resolved != null) tts.voice = resolved
    }

    fun setPlaybackSpeechRate(rate: Float) {
        speechRate =
            rate.coerceIn(VoicePlaybackState.MIN_SPEECH_RATE, VoicePlaybackState.MAX_SPEECH_RATE)
        if (ttsReady) tts.setSpeechRate(speechRate)
        if (isPlaying) {
            when (phase) {
                VoicePhase.Question -> speakQuestion()
                VoicePhase.Answer -> speakAnswer()
            }
        } else {
            publishState()
        }
    }

    fun stopPlayback() {
        isPlaying = false
        startWhenReady = false
        generation++
        stopUtterance()
        abandonAudioFocus()
        cards = emptyList()
        index = 0
        isBetweenPause = false
        _voiceState.value = VoicePlaybackState(isActive = false)
        invalidateState()
    }

    private fun doPlay() {
        if (cards.isEmpty()) return
        when (phase) {
            VoicePhase.Question -> speakQuestion()
            VoicePhase.Answer -> speakAnswer()
        }
    }

    private fun doPause() {
        isPlaying = false
        stopUtterance()
        publishState()
    }

    fun jumpTo(targetIndex: Int) {
        if (cards.isEmpty()) return
        index = targetIndex.coerceIn(0, cards.lastIndex)
        moveToQuestion()
    }

    /** Move to the question of the current [index]; keep playing if we were, else just show it. */
    private fun moveToQuestion() {
        phase = VoicePhase.Question
        isBetweenPause = false
        if (isPlaying) {
            speakQuestion()
        } else {
            stopUtterance()
            publishState()
        }
    }

    private fun speakQuestion() {
        val card = cards.getOrNull(index) ?: return
        phase = VoicePhase.Question
        isPlaying = true
        val generationId = ++generation
        requestAudioFocus()
        publishState()
        tts.speak(
            card.spokenQuestion.ifBlank { " " },
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId(TAG_QUESTION, generationId),
        )
    }

    private fun speakAnswer() {
        val card = cards.getOrNull(index) ?: return
        phase = VoicePhase.Answer
        isPlaying = true
        val generationId = ++generation
        requestAudioFocus()
        publishState()
        tts.speak(
            card.spokenAnswer.ifBlank { " " },
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId(TAG_ANSWER, generationId),
        )
    }

    private fun silence(durationMs: Long, tag: String) {
        val generationId = ++generation
        tts.playSilentUtterance(
            durationMs,
            TextToSpeech.QUEUE_FLUSH,
            utteranceId(tag, generationId)
        )
    }

    private fun advanceAfterCard() {
        if (index < cards.lastIndex) {
            index++
            isBetweenPause = false
            speakQuestion()
        } else {
            phase =
                VoicePhase.Question // reset so tapping Play re-reads last card from the question
            isPlaying = false
            publishState()
            onEvent(PlaybackEvent.EndReached)
        }
    }

    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            utteranceId ?: return
            handler.post { onUtteranceDone(utteranceId) }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = Unit

        override fun onError(utteranceId: String?, errorCode: Int) {
            utteranceId ?: return
            handler.post { onUtteranceError(utteranceId) }
        }
    }

    private fun onUtteranceError(utteranceId: String) {
        val generationId = utteranceId.substringAfterLast(SEPARATOR).toIntOrNull() ?: return
        if (generationId != generation) return
        generation++
        isPlaying = false
        publishState()
    }

    private fun onUtteranceDone(utteranceId: String) {
        val generationId = utteranceId.substringAfterLast(SEPARATOR).toIntOrNull() ?: return
        if (generationId != generation) return // superseded by a newer command/utterance
        when (utteranceId.substringBefore(SEPARATOR)) {
            TAG_QUESTION ->
                if (isQuestionOnlyMode) {
                    cards.getOrNull(index)?.let { card -> onEvent(PlaybackEvent.QuestionFinished(card.cardId)) }
                } else {
                    silence(QUESTION_TO_ANSWER_PAUSE_MS, TAG_PAUSE)
                }
            TAG_PAUSE -> speakAnswer()
            TAG_ANSWER -> {
                isBetweenPause = true
                publishState()
                silence(ANSWER_TO_NEXT_PAUSE_MS, TAG_BETWEEN)
            }

            TAG_BETWEEN -> {
                isBetweenPause = false
                advanceAfterCard()
            }
        }
    }

    private fun stopUtterance() {
        generation++ // invalidate the in-flight utterance callback
        if (ttsReady) tts.stop()
    }

    private fun releaseEngine() {
        generation++
        handler.removeCallbacksAndMessages(null)
        abandonAudioFocus()
        runCatching {
            tts.stop()
            tts.shutdown()
        }
    }

    /** Push the current internal state to both the Media3 [getState] and the [voiceState] flow. */
    private fun publishState() {
        _voiceState.value = VoicePlaybackState(
            isActive = cards.isNotEmpty(),
            isPlaying = isPlaying,
            currentIndex = index,
            totalCards = cards.size,
            phase = phase,
            isInBetweenPause = isBetweenPause,
            speechRate = speechRate,
        )
        invalidateState()
    }

    /** Request focus once and keep it; a no-op if already held (guards against per-utterance churn). */
    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest != null) return
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setWillPauseWhenDucked(true)
                .setAcceptsDelayedFocusGain(true) // queue instead of fail when another app holds focus
                .setOnAudioFocusChangeListener(audioFocusListener, handler)
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
    }

    private companion object {
        const val DEFAULT_TITLE = "Study session"

        const val QUESTION_TO_ANSWER_PAUSE_MS = 1_500L
        const val ANSWER_TO_NEXT_PAUSE_MS = 2_500L

        const val SEPARATOR = ":"
        const val TAG_QUESTION = "question"
        const val TAG_PAUSE = "pause"
        const val TAG_ANSWER = "answer"
        const val TAG_BETWEEN = "between"

        val AVAILABLE_COMMANDS = Player.Commands.Builder()
            .addAll(
                COMMAND_PLAY_PAUSE,
                COMMAND_PREPARE,
                COMMAND_STOP,
                COMMAND_SEEK_TO_NEXT,
                COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                COMMAND_SEEK_TO_PREVIOUS,
                COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                COMMAND_SEEK_TO_MEDIA_ITEM,
                COMMAND_GET_CURRENT_MEDIA_ITEM,
                COMMAND_GET_METADATA,
                COMMAND_GET_TIMELINE,
                COMMAND_RELEASE,
            )
            .build()

        fun utteranceId(tag: String, generation: Int): String = "$tag$SEPARATOR$generation"
    }
}
