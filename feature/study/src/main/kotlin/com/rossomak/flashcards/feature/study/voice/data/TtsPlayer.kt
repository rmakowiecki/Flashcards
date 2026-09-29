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
import com.rossomak.flashcards.core.domain.model.TransportCommandType
import com.rossomak.flashcards.core.domain.model.VoicePhase
import com.rossomak.flashcards.core.domain.model.VoicePlaybackState
import com.rossomak.flashcards.core.domain.model.type
import com.rossomak.flashcards.core.domain.repository.StudyVoicePlaybackGateway
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Media3 [SimpleBasePlayer] that reads flashcards aloud with the system [TextToSpeech] engine and
 * surfaces playback to a `MediaSession` (lock screen / Bluetooth / notification transport controls,
 * audio focus). It presents one part of one card when told to ([presentQuestion], [presentAnswer]),
 * reads it aloud while playing, and reports when it was read in full. It never starts another part
 * or card by itself: the pauses between parts and the card position belong to the study session
 * coordinator. After a part finishes it stays playing and idle, so the system controls keep showing
 * playing through the coordinator's pause.
 *
 * Only the study session coordinators drive it, through [StudySessionVoiceService.LocalBinder]. System
 * transport controls reach Media3's `handle*` overrides, which never act: they report a
 * [PlaybackEvent.ExternalCommand] through [onEvent], and the coordinator applies it exactly like the
 * matching in-app command. Every change calls [publishState] to refresh both the Media3 state and
 * the [voiceState] side-channel.
 *
 * [voiceState] carries the [VoicePhase], which the standard [Player] state cannot express.
 *
 * It makes no session decision. Audio-focus auto-pause and auto-resume are the one behavior it
 * still runs by itself. Each utterance starts with a [PlaybackPreroll], so headset buttons reach this
 * session and Bluetooth speech starts unclipped.
 */
@UnstableApi
class TtsPlayer(
    context: Context,
    private val onEvent: (PlaybackEvent) -> Unit,
) : SimpleBasePlayer(Looper.getMainLooper()) {

    private val _voiceState = MutableStateFlow(VoicePlaybackState())
    val voiceState: StateFlow<VoicePlaybackState> = _voiceState.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private val playbackPreroll = PlaybackPreroll()

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wasPlayingBeforeFocusLoss = false

    // Whether the transient focus loss came while playing between parts, with nothing being read.
    private var wasIdleBeforeFocusLoss = false

    private var ttsReady = false
    private var startWhenReady = false

    private var cards: List<VoiceFlashcard> = emptyList()
    private var index = 0
    private var phase = VoicePhase.Question
    private var isPlaying = false

    // An utterance is in flight; false while playing between parts.
    private var isSpeaking = false
    private var speechRate = VoicePlaybackState.DEFAULT_SPEECH_RATE
    private var pendingVoiceId: String? = null
    private var subcategoryName = ""

    // What the system controls offer, as the coordinator last set it. Every command until then.
    private var transportCommands: Set<TransportCommandType> = TransportCommandType.entries.toSet()

    // The session's own counter, shown instead of the position in the list once set.
    private var sessionProgress: Pair<Int, Int>? = null

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
     * auto-resumes. A gain after a loss between parts resumes without reading, so the coordinator
     * decides the next step; a loss mid-part reads that part again.
     */
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (wasPlayingBeforeFocusLoss) {
                    wasPlayingBeforeFocusLoss = false
                    if (wasIdleBeforeFocusLoss) resumeWithoutReading() else doPlay()
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
                    wasIdleBeforeFocusLoss = !isSpeaking
                    doPause() // stay registered — AUDIOFOCUS_GAIN fires when the other app releases
                }
            }
        }
    }

    override fun getState(): State {
        val progress = sessionProgress
        val items = cards.mapIndexed { cardIndex, _ ->
            MediaItemData.Builder("card-$cardIndex")
                .setMediaItem(MediaItem.Builder().setMediaId("card-$cardIndex").build())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(subcategoryName.ifBlank { DEFAULT_TITLE })
                        .setArtist(progress?.let { (completed, total) -> "$completed / $total" } ?: "${cardIndex + 1} / ${cards.size}")
                        .build()
                )
                .build()
        }
        return State.Builder()
            .setAvailableCommands(transportCommands.toPlayerCommands())
            .setPlaybackState(if (cards.isEmpty()) STATE_IDLE else STATE_READY)
            .setPlayWhenReady(isPlaying, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaylist(items)
            .setCurrentMediaItemIndex(index.coerceIn(0, maxOf(0, cards.lastIndex)))
            .build()
    }

    // Media3 checks only the commands it publishes; one play-pause command covers both, and a
    // controller can race an update, so every handler checks the exact command again.

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        reportExternal(if (playWhenReady) TransportCommand.Play else TransportCommand.Pause)
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleStop(): ListenableFuture<*> {
        reportExternal(TransportCommand.Stop)
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
        command?.let(::reportExternal)
        return Futures.immediateVoidFuture()
    }

    private fun reportExternal(command: TransportCommand) {
        if (command.type in transportCommands) onEvent(PlaybackEvent.ExternalCommand(command))
    }

    /** The commands the system controls offer from now on; see [StudyVoicePlaybackGateway.setAvailableCommands]. */
    fun setTransportCommands(commands: Set<TransportCommandType>) {
        if (commands == transportCommands) return
        transportCommands = commands
        invalidateState()
    }

    /** Shows [completedCount] of [totalCount] as the subtitle, instead of the position in the list. */
    fun setSessionProgress(completedCount: Int, totalCount: Int) {
        sessionProgress = completedCount to totalCount
        invalidateState()
    }

    /**
     * Reports playing again without reading anything, while the voice round goes on on the notice
     * voice. Nothing is in flight, so a later read starts cleanly.
     */
    fun resumeWithoutReading() {
        if (cards.isEmpty()) return
        wasPlayingBeforeFocusLoss = false
        isPlaying = true
        requestAudioFocus()
        publishState()
    }

    fun loadAndStartSession(cards: List<VoiceFlashcard>, startIndex: Int, subcategoryName: String) {
        this.cards = cards
        this.subcategoryName = subcategoryName
        this.index = if (cards.isEmpty()) 0 else startIndex.coerceIn(0, cards.lastIndex)
        this.phase = VoicePhase.Question
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

    /**
     * Starts or resumes reading the part being presented, from its start. Named apart from
     * [Player.play], which the Media3 session routes to [handleSetPlayWhenReady]. Clears an
     * auto-resume pending from a transient focus loss: the user decided.
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

    /**
     * Presents the question of card [targetIndex]: reads it while playing, only shows it while
     * paused. An index outside the list stops playing.
     */
    fun presentQuestion(targetIndex: Int) {
        if (targetIndex !in cards.indices) {
            isPlaying = false
            stopUtterance()
            publishState()
            return
        }
        index = targetIndex
        phase = VoicePhase.Question
        if (isPlaying) {
            speakQuestion()
        } else {
            stopUtterance()
            publishState()
        }
    }

    /** Presents the answer of card [targetIndex] and reports it revealed: reads it while playing, only shows it while paused. */
    fun presentAnswer(targetIndex: Int) {
        if (targetIndex !in cards.indices) return
        index = targetIndex
        phase = VoicePhase.Answer
        if (isPlaying) {
            speakAnswer()
        } else {
            stopUtterance()
            publishState()
            cards.getOrNull(index)?.let { card -> onEvent(PlaybackEvent.AnswerRevealed(card.cardId)) }
        }
    }

    /** Applies from the next utterance; nothing is read again. */
    fun setVoice(voiceId: String?) {
        pendingVoiceId = voiceId
        if (ttsReady) applyVoice(voiceId)
    }

    private fun applyVoice(voiceId: String?) = tts.applySessionVoice(voiceId)

    /** Applies from the next utterance; nothing is read again. */
    fun setPlaybackSpeechRate(rate: Float) {
        speechRate =
            rate.coerceIn(VoicePlaybackState.MIN_SPEECH_RATE, VoicePlaybackState.MAX_SPEECH_RATE)
        if (ttsReady) tts.setSpeechRate(speechRate)
        publishState()
    }

    fun stopPlayback() {
        isPlaying = false
        startWhenReady = false
        generation++
        stopUtterance()
        abandonAudioFocus()
        cards = emptyList()
        index = 0
        transportCommands = TransportCommandType.entries.toSet()
        sessionProgress = null
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

    private fun speakQuestion() {
        val card = cards.getOrNull(index) ?: return
        phase = VoicePhase.Question
        isPlaying = true
        isSpeaking = true
        val generationId = ++generation
        requestAudioFocus()
        publishState()
        playbackPreroll.play()
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
        isSpeaking = true
        val generationId = ++generation
        requestAudioFocus()
        publishState()
        onEvent(PlaybackEvent.AnswerRevealed(card.cardId))
        playbackPreroll.play()
        tts.speak(
            card.spokenAnswer.ifBlank { " " },
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId(TAG_ANSWER, generationId),
        )
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
        isSpeaking = false
        isPlaying = false
        publishState()
    }

    private fun onUtteranceDone(utteranceId: String) {
        val generationId = utteranceId.substringAfterLast(SEPARATOR).toIntOrNull() ?: return
        if (generationId != generation) return // superseded by a newer command/utterance
        isSpeaking = false
        val card = cards.getOrNull(index) ?: return
        when (utteranceId.substringBefore(SEPARATOR)) {
            TAG_QUESTION -> onEvent(PlaybackEvent.QuestionFinished(card.cardId))
            TAG_ANSWER -> onEvent(PlaybackEvent.AnswerFinished(card.cardId))
        }
    }

    private fun stopUtterance() {
        generation++ // invalidate the in-flight utterance callback
        isSpeaking = false
        if (ttsReady) tts.stop()
    }

    private fun releaseEngine() {
        generation++
        handler.removeCallbacksAndMessages(null)
        abandonAudioFocus()
        playbackPreroll.release()
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

        const val SEPARATOR = ":"
        const val TAG_QUESTION = "question"
        const val TAG_ANSWER = "answer"

        fun utteranceId(tag: String, generation: Int): String = "$tag$SEPARATOR$generation"
    }
}

/**
 * The Media3 commands for a set of transport commands. Play and pause share one command, offered
 * whenever either is; the player itself refuses the one not in the set.
 */
@UnstableApi
internal fun Set<TransportCommandType>.toPlayerCommands(): Player.Commands {
    val builder = Player.Commands.Builder().addAll(
        Player.COMMAND_PREPARE,
        Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
        Player.COMMAND_GET_METADATA,
        Player.COMMAND_GET_TIMELINE,
        Player.COMMAND_RELEASE,
    )
    forEach { type ->
        when (type) {
            TransportCommandType.Play, TransportCommandType.Pause -> builder.add(Player.COMMAND_PLAY_PAUSE)
            TransportCommandType.Stop -> builder.add(Player.COMMAND_STOP)
            TransportCommandType.Next -> builder.addAll(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            TransportCommandType.Previous -> builder.add(Player.COMMAND_SEEK_TO_PREVIOUS)
            TransportCommandType.PreviousCard -> builder.add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            TransportCommandType.JumpTo -> builder.add(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
        }
    }
    return builder.build()
}

/**
 * [voiceId] `null` means "no explicit choice yet" — resolves to a curated English voice, never the
 * device's system default (which may not even be English). Shared by the question and notice
 * engines, so both speak with the same voice.
 */
internal fun TextToSpeech.applySessionVoice(voiceId: String?) {
    val resolved = voiceId?.let { id -> voices?.firstOrNull { it.name == id } }
        ?: VoiceCuration.curate(voices.orEmpty()).firstOrNull()
    if (resolved != null) voice = resolved
}
