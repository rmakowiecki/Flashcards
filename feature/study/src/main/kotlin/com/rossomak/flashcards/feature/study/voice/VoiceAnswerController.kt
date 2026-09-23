package com.rossomak.flashcards.feature.study.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.content.ContextCompat
import com.rossomak.flashcards.core.common.loge
import com.rossomak.flashcards.core.domain.model.VoiceAnswerGrade
import com.rossomak.flashcards.core.domain.model.VoiceAnswerGradingEvent
import com.rossomak.flashcards.core.domain.model.VoiceCaptureFailureReason
import com.rossomak.flashcards.core.domain.usecase.TranscribeAndGradeSpokenAnswerUseCase
import com.rossomak.flashcards.core.voice.AudioRouteManager
import com.rossomak.flashcards.core.voice.CaptureRouteType
import com.rossomak.flashcards.core.voice.VoiceCaptureEngine
import com.rossomak.flashcards.core.voice.VoiceCaptureEvent
import com.rossomak.flashcards.feature.study.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class VoiceAnswerPhase { Idle, WaitingForQuestion, Listening, SpeechDetected, Grading, SpeakingNotice }

data class VoiceAnswerState(
    val isEnabled: Boolean = false,
    val phase: VoiceAnswerPhase = VoiceAnswerPhase.Idle,
    /** Sanitized transcript, set as soon as it streams in — ahead of [lastGrade] (ADR-0028). */
    val sanitizedTranscript: String? = null,
    val lastGrade: VoiceAnswerGrade? = null,
    val lastGradedCardId: String? = null,
    val captureRoute: CaptureRouteType = CaptureRouteType.None,
    // Compile-safe migration only: still just a null-check trigger for one fixed snackbar
    // message, not resolved per-reason yet.
    val error: VoiceAnswerFailureReason? = null,
)

/**
 * Session-scoped orchestration of the voice answering pipeline (Rated mode only, ADR-0025): waits
 * for the shared [VoiceGateway]/[TtsPlayer] engine to finish reading the question, then runs the
 * [VoiceCaptureEngine] (mic + VAD + on-device obfuscation) for a bounded listening window, grades
 * the captured utterance, and reports outcomes *audibly* — this feature's UX is phone-in-pocket/
 * screen-off, so both the grade feedback and the no-answer skip notice are spoken through a
 * dedicated notice TTS channel, never a toast.
 *
 * Listening only ever runs between [onQuestionFinishedSpeaking] and either a captured utterance or
 * the silence timeout — never while the question or a notice is being spoken, which is what closes
 * the phone-speaker/VAD overlap this controller used to have.
 *
 * Lives inside [StudySessionVoiceService] so listening shares the study session's
 * foreground-service lifecycle, and holds a PARTIAL_WAKE_LOCK across the listening window so
 * OEM battery managers can't starve the 20ms frame loop (design doc §Wake lock).
 */
class VoiceAnswerController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val transcribeAndGradeSpokenAnswer: TranscribeAndGradeSpokenAnswerUseCase,
    private val voiceCaptureEngine: VoiceCaptureEngine,
    private val audioRouteManager: AudioRouteManager,
) {

    private val _state = MutableStateFlow(VoiceAnswerState())
    val state: StateFlow<VoiceAnswerState> = _state.asStateFlow()

    /**
     * Emitted once the grade/skip notice has finished speaking (plus [ADVANCE_DELAY_MS]) — tells
     * the service to move the shared TTS engine to the next card.
     */
    private val _advanceRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val advanceRequests: SharedFlow<Unit> = _advanceRequests.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var captureEventsJob: Job? = null
    private var routeObserverJob: Job? = null
    private var sessionRouteJob: Job? = null
    private var listenStartJob: Job? = null
    private var listenTimeoutJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var activeCard: VoiceFlashcard? = null
    private var noticeTts: TextToSpeech? = null

    // Whether *this* listening window's silence timeout would be the one that pauses the
    // session — pushed by the ViewModel (the sole owner of the consecutive-silence count)
    // ahead of each listen cycle, so onSilenceTimeout() can pick its spoken message without
    // needing to know the count itself.
    private var nextSilenceWillPauseSession = false

    fun setActiveCard(card: VoiceFlashcard?) {
        activeCard = card
    }

    fun setNextSilenceWillPauseSession(willPause: Boolean) {
        nextSilenceWillPauseSession = willPause
    }

    fun start() {
        if (_state.value.isEnabled) return
        if (!hasRecordAudioPermission()) {
            _state.value = VoiceAnswerState(error = VoiceAnswerFailureReason.PermissionMissing)
            return
        }
        acquireWakeLock()
        ensureNoticeTts()
        _state.value = VoiceAnswerState(isEnabled = true, phase = VoiceAnswerPhase.WaitingForQuestion)
        captureEventsJob = scope.launch {
            voiceCaptureEngine.events.collect { event -> handleCaptureEvent(event) }
        }
        // Establish the session mic route once (BLE-first / SCO / phone), then keep the surfaced
        // CaptureRoute in state for the debug screen. v1 logs route changes only (ADR-0027 Q11).
        routeObserverJob = scope.launch {
            audioRouteManager.route.collect { route ->
                Log.i(TAG, "capture route -> ${route.type}")
                _state.update { it.copy(captureRoute = route.type) }
            }
        }
        sessionRouteJob = scope.launch { audioRouteManager.acquireSessionRoute() }
    }

    fun stop() {
        listenStartJob?.cancel()
        listenStartJob = null
        listenTimeoutJob?.cancel()
        listenTimeoutJob = null
        voiceCaptureEngine.stopListening()
        // Cancel before releasing: acquireSessionRoute() can still be mid-handshake here, and a
        // stale resume after releaseSessionRoute() would re-apply BT routing on a dead session.
        sessionRouteJob?.cancel()
        sessionRouteJob = null
        audioRouteManager.releaseSessionRoute()
        routeObserverJob?.cancel()
        routeObserverJob = null
        captureEventsJob?.cancel()
        captureEventsJob = null
        releaseWakeLock()
        activeCard = null
        nextSilenceWillPauseSession = false
        _state.value = VoiceAnswerState()
    }

    /** Full teardown when the owning service dies. */
    fun release() {
        stop()
        noticeTts?.shutdown()
        noticeTts = null
    }

    /** Called once the shared TTS engine finishes reading the current card's question — opens the listening window. */
    // startListening() below requires RECORD_AUDIO, but lint can't see that isEnabled only
    // flips true after hasRecordAudioPermission() passes in start() — this path is unreachable
    // without the permission granted.
    @SuppressLint("MissingPermission")
    fun onQuestionFinishedSpeaking() {
        if (!_state.value.isEnabled) return
        acquireWakeLock()
        // Clear the previous card's grade/error before opening this round's listening window —
        // otherwise it rides along on this phase-only update, gets picked up as "new" by the
        // ViewModel, and re-shows a stale snackbar (e.g. over this round's own no-answer notice),
        // or suppresses re-showing an identical error next round (LaunchedEffect keys on value).
        _state.update {
            it.copy(
                phase = VoiceAnswerPhase.Listening,
                sanitizedTranscript = null,
                lastGrade = null,
                lastGradedCardId = null,
                error = null,
            )
        }
        // Bluetooth-strict (ADR-0027): open the listening window only once the mic route has settled
        // to a capturable one. If a mic-capable BT device dropped, this suspends until it reconnects
        // (auto-reacquire) rather than capturing on the pocketed phone mic. Phone-only sessions
        // settle immediately, so this adds no latency when no BT is involved.
        listenStartJob?.cancel()
        listenStartJob = scope.launch {
            audioRouteManager.awaitRouteReady()
            if (!_state.value.isEnabled) return@launch
            voiceCaptureEngine.startListening()
            listenTimeoutJob?.cancel()
            listenTimeoutJob = scope.launch {
                delay(SILENCE_TIMEOUT_MS.milliseconds)
                onSilenceTimeout()
            }
        }
    }

    private suspend fun onSilenceTimeout() {
        voiceCaptureEngine.stopListening()
        _state.update { it.copy(phase = VoiceAnswerPhase.SpeakingNotice) }
        val messageRes = if (nextSilenceWillPauseSession) {
            R.string.study_session_voice_answer_skip_pause_spoken_message
        } else {
            R.string.study_session_voice_answer_skip_spoken_message
        }
        speakNotice(context.getString(messageRes))
    }

    private suspend fun handleCaptureEvent(event: VoiceCaptureEvent) {
        when (event) {
            is VoiceCaptureEvent.SpeechStarted -> {
                listenTimeoutJob?.cancel()
                _state.update { it.copy(phase = VoiceAnswerPhase.SpeechDetected) }
            }
            is VoiceCaptureEvent.SpeechEnded ->
                _state.update { it.copy(phase = VoiceAnswerPhase.Grading) }
            is VoiceCaptureEvent.UtteranceCaptured -> {
                listenTimeoutJob?.cancel()
                voiceCaptureEngine.stopListening()
                gradeUtterance(event.utterance.wavBytes)
            }
            is VoiceCaptureEvent.CaptureFailed -> {
                listenTimeoutJob?.cancel()
                voiceCaptureEngine.stopListening()
                _state.update {
                    it.copy(
                        phase = VoiceAnswerPhase.WaitingForQuestion,
                        error = VoiceAnswerFailureReason.CaptureFailed(event.reason),
                    )
                }
                if (event.reason !is VoiceCaptureFailureReason.PermissionMissing) {
                    // Own utterance id, not NOTICE_UTTERANCE_ID: this failure pauses the session rather than advancing to the next card,
                    // so it must not trigger onNoticeFinishedSpeaking()'s advance-request callback the way the grade/skip notices do.
                    speakStandaloneNotice(context.getString(R.string.study_session_voice_answer_capture_unavailable_spoken_message))
                }
            }
        }
    }

    /**
     * Collects the streamed grading call (ADR-0028): [VoiceAnswerGradingEvent.TranscriptReady]
     * updates [VoiceAnswerState.sanitizedTranscript] as soon as it arrives — ahead of, and
     * independent from, the grade/feedback that follows on [VoiceAnswerGradingEvent.Graded].
     */
    private suspend fun gradeUtterance(obfuscatedWav: ByteArray) {
        val card = activeCard ?: run {
            _state.update { it.copy(phase = VoiceAnswerPhase.WaitingForQuestion) }
            return
        }
        _state.update { it.copy(phase = VoiceAnswerPhase.Grading) }
        transcribeAndGradeSpokenAnswer(
            TranscribeAndGradeSpokenAnswerUseCase.Params(
                cardId = card.cardId,
                question = card.questionText,
                expectedAnswer = card.answerText,
                obfuscatedAnswerWav = obfuscatedWav,
            )
        )
            .onEach { event ->
                when (event) {
                    is VoiceAnswerGradingEvent.TranscriptReady ->
                        _state.update { it.copy(sanitizedTranscript = event.sanitizedTranscript) }
                    is VoiceAnswerGradingEvent.Graded -> {
                        _state.update {
                            it.copy(
                                phase = VoiceAnswerPhase.SpeakingNotice,
                                lastGrade = event.grade,
                                lastGradedCardId = card.cardId,
                                error = null,
                            )
                        }
                        speakNotice(
                            context.getString(
                                R.string.study_session_voice_answer_grade_spoken_message,
                                event.grade.gradePercent,
                                event.grade.feedback,
                            )
                        )
                    }
                }
            }
            .catch { error ->
                loge(error) { "voice answer grading/upload failed" }
                _state.update {
                    it.copy(
                        phase = VoiceAnswerPhase.SpeakingNotice,
                        error = VoiceAnswerFailureReason.GradingFailed(error.message),
                    )
                }
                // No screen to look at in this UX — failure must be audible (design doc §Upload
                // failure handling; silent-drop was explicitly rejected).
                speakNotice(context.getString(R.string.study_session_voice_answer_failure_spoken_message))
            }
            .collect()
    }

    /**
     * Dedicated TTS channel for grade/skip/failure notices, separate from [TtsPlayer]'s card
     * playback so notices can't corrupt the Media3 player's utterance state machine.
     */
    private fun ensureNoticeTts() {
        if (noticeTts != null) return
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) {
                tts?.shutdown()
                noticeTts = null
            } else {
                // App supports English content only — never fall back to the device's system locale (e.g. Polish), which garbles English notice text.
                tts?.language = Locale.US
                tts?.setOnUtteranceProgressListener(noticeUtteranceListener)
            }
        }
        noticeTts = tts
    }

    private val noticeUtteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            if (utteranceId != NOTICE_UTTERANCE_ID) return
            scope.launch { onNoticeFinishedSpeaking() }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = Unit

        override fun onError(utteranceId: String?, errorCode: Int) {
            if (utteranceId != NOTICE_UTTERANCE_ID) return
            scope.launch { onNoticeFinishedSpeaking() }
        }
    }

    /** 1000ms after the grade/skip notice finishes speaking (ADR-0025), tell the service to advance to the next card. */
    private suspend fun onNoticeFinishedSpeaking() {
        delay(ADVANCE_DELAY_MS.milliseconds)
        if (!_state.value.isEnabled) return
        _state.update { it.copy(phase = VoiceAnswerPhase.WaitingForQuestion) }
        _advanceRequests.emit(Unit)
    }

    private fun speakNotice(text: String) {
        noticeTts?.speak(text, TextToSpeech.QUEUE_ADD, null, NOTICE_UTTERANCE_ID)
    }

    /**
     * Same TTS channel as [speakNotice], different utterance id: [noticeUtteranceListener] only
     * chains into [onNoticeFinishedSpeaking]'s advance-next-card callback for [NOTICE_UTTERANCE_ID],
     * so a notice spoken here finishes as a no-op rather than advancing the session.
     */
    private fun speakStandaloneNotice(text: String) {
        noticeTts?.speak(text, TextToSpeech.QUEUE_ADD, null, CAPTURE_FAILURE_NOTICE_UTTERANCE_ID)
    }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    // Renewed every question cycle (see onQuestionFinishedSpeaking) rather than acquired once for
    // the whole session — acquire(timeout) on a non-reference-counted lock just resets its
    // auto-release deadline, so calling this repeatedly keeps the lock alive indefinitely across a
    // long session while WAKE_LOCK_TIMEOUT_MS still caps the damage if renewal itself ever stalls.
    private fun acquireWakeLock() {
        val lock = wakeLock ?: run {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
            }.also { wakeLock = it }
        }
        lock.acquire(WAKE_LOCK_TIMEOUT_MS)
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private companion object {
        const val TAG = "VoiceAnswerController"
        const val WAKE_LOCK_TAG = "flashcards:voiceAnswerCapture"
        const val WAKE_LOCK_TIMEOUT_MS = 60L * 60L * 1000L // 1h safety cap per session
        const val NOTICE_UTTERANCE_ID = "voice_answer_notice"
        const val CAPTURE_FAILURE_NOTICE_UTTERANCE_ID = "voice_answer_capture_failure_notice"
        const val SILENCE_TIMEOUT_MS = 8_000L
        const val ADVANCE_DELAY_MS = 1_000L
    }
}
