package com.rossomak.flashcards.feature.study.voice.data

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.rossomak.flashcards.core.domain.model.SpokenNotice
import com.rossomak.flashcards.core.domain.model.VoiceSettings
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The text-to-speech engine [NoticeSpeaker] speaks on, reduced to what it uses. */
internal interface NoticeEngine {

    /** Queues [text] behind anything already speaking. */
    fun speak(text: String, utteranceId: String)

    /** Cuts what is speaking and drops everything queued; each one is reported ended. */
    fun stop()
    fun shutdown()

    /** Applies from the next utterance; one already speaking keeps its voice. */
    fun setVoice(voiceId: String?)

    /** Applies from the next utterance; one already speaking keeps its rate. */
    fun setSpeechRate(rate: Float)
}

/** What a [NoticeEngine] reports, on any thread. */
internal interface NoticeEngineListener {
    fun onInitialized(isReady: Boolean)
    fun onUtteranceStarted(utteranceId: String)

    /** The utterance finished, or failed. Either way it is over. */
    fun onUtteranceEnded(utteranceId: String)
}

/**
 * Speaks [SpokenNotice]s on a text-to-speech engine of their own, separate from [TtsPlayer]'s, so a
 * notice callback never touches the player's utterance state machine (ADR-0031). It makes no
 * session decision; it only guarantees [onNoticeFinished]:
 * - exactly once per [speak] call, in call order, even when a later notice is given up on first,
 *   except for a feedback cut by [stopFeedback], which never reports finished;
 * - a short notice is given up on [WATCHDOG_TIMEOUT] after [speak] at the latest;
 * - [SpokenNotice.Feedback] is given up on if the engine has not started it within
 *   [WATCHDOG_TIMEOUT]; once it speaks, it is cut off [STARTED_FEEDBACK_TIMEOUT] later, a bound
 *   real feedback never reaches that only guards against an engine that stops reporting;
 * - a notice spoken while the engine is not ready, or after it failed, finishes at once.
 *
 * A late engine callback for a notice already given up on is dropped. Everything runs on [scope].
 *
 * Relies on the session speaking one notice at a time, each only after the previous one finished:
 * [stopFeedback] and the started-feedback cutoff stop the whole engine, which would also drop a
 * notice queued behind the feedback and report it finished unspoken.
 */
internal class NoticeSpeaker(
    private val scope: CoroutineScope,
    private val resolveText: (SpokenNotice) -> String,
    private val onNoticeFinished: (SpokenNotice) -> Unit,
    private val onEngineUnavailable: () -> Unit,
    engineFactory: (NoticeEngineListener) -> NoticeEngine,
) {
    private enum class EngineState { Initializing, Ready, Failed }

    private class PendingNotice(val utteranceId: String, val notice: SpokenNotice) {
        var isFinished = false
        var watchdog: Job? = null
    }

    private var engineState = EngineState.Initializing
    private val pendingNotices = ArrayDeque<PendingNotice>()
    private var utteranceCount = 0

    private val engine: NoticeEngine = engineFactory(
        object : NoticeEngineListener {
            override fun onInitialized(isReady: Boolean) {
                scope.launch { onEngineInitialized(isReady) }
            }

            override fun onUtteranceStarted(utteranceId: String) {
                scope.launch { onStarted(utteranceId) }
            }

            override fun onUtteranceEnded(utteranceId: String) {
                scope.launch { finish(utteranceId) }
            }
        },
    )

    fun speak(notice: SpokenNotice) {
        val pending = PendingNotice(utteranceId = "$UTTERANCE_PREFIX${utteranceCount++}", notice = notice)
        pendingNotices.addLast(pending)
        if (engineState != EngineState.Ready) {
            pending.isFinished = true
            deliverFinished()
            return
        }
        engine.speak(resolveText(notice), pending.utteranceId)
        pending.watchdog = scope.launch {
            delay(WATCHDOG_TIMEOUT)
            finish(pending.utteranceId)
        }
    }

    /**
     * Cuts the [SpokenNotice.Feedback] being spoken and forgets it: no [onNoticeFinished] for it,
     * and the engine's late callback for it is dropped. The one exception to the guarantee above.
     * Text-to-speech cannot pause mid-utterance and a finish carries no id, so a stopped feedback
     * that still reported finished could be taken for the end of a replay started right after it.
     * Short notices are never cut.
     */
    fun stopFeedback() {
        val feedback = pendingNotices.filter { it.notice is SpokenNotice.Feedback && !it.isFinished }
        if (feedback.isEmpty()) return
        feedback.forEach { it.watchdog?.cancel() }
        // Forgotten before the stop, so the stop's own callback finds nothing to finish.
        pendingNotices.removeAll(feedback)
        engine.stop()
        deliverFinished()
    }

    fun setVoice(voiceId: String?) = engine.setVoice(voiceId)

    fun setSpeechRate(rate: Float) = engine.setSpeechRate(rate)

    fun release() {
        pendingNotices.forEach { it.watchdog?.cancel() }
        pendingNotices.clear()
        engine.shutdown()
    }

    private fun onEngineInitialized(isReady: Boolean) {
        engineState = if (isReady) EngineState.Ready else EngineState.Failed
        if (!isReady) onEngineUnavailable()
    }

    private fun onStarted(utteranceId: String) {
        val pending = pendingNotices.firstOrNull { it.utteranceId == utteranceId && !it.isFinished } ?: return
        if (pending.notice !is SpokenNotice.Feedback) return
        pending.watchdog?.cancel()
        pending.watchdog = scope.launch {
            delay(STARTED_FEEDBACK_TIMEOUT)
            // Stopped first, so nothing spoken in reaction to the finish is flushed with it.
            engine.stop()
            finish(pending.utteranceId)
        }
    }

    private fun finish(utteranceId: String) {
        val pending = pendingNotices.firstOrNull { it.utteranceId == utteranceId && !it.isFinished } ?: return
        pending.isFinished = true
        pending.watchdog?.cancel()
        deliverFinished()
    }

    /** Delivers finished notices from the oldest, stopping at the first one still speaking. */
    private fun deliverFinished() {
        while (pendingNotices.firstOrNull()?.isFinished == true) {
            onNoticeFinished(pendingNotices.removeFirst().notice)
        }
    }

    companion object {
        val WATCHDOG_TIMEOUT: Duration = 5.seconds
        val STARTED_FEEDBACK_TIMEOUT: Duration = 20.seconds
        private const val UTTERANCE_PREFIX = "notice-"
    }
}

/**
 * A [NoticeEngine] on the system [TextToSpeech], always in English: the app's content is English
 * only. Speaks with the session voice and speech rate, like the questions; a voice or rate set
 * before the engine is ready applies once it is.
 */
internal class TextToSpeechNoticeEngine(context: Context, private val listener: NoticeEngineListener) : NoticeEngine {

    private var isReady = false
    private var voiceId: String? = null
    private var speechRate = VoiceSettings.DEFAULT_SPEECH_RATE

    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        isReady = status == TextToSpeech.SUCCESS
        if (isReady) {
            // Never the device's system locale (e.g. Polish), which garbles English notice text.
            tts.language = Locale.US
            tts.applySessionVoice(voiceId)
            tts.setSpeechRate(speechRate)
            tts.setOnUtteranceProgressListener(progressListener)
        }
        listener.onInitialized(isReady)
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            utteranceId?.let(listener::onUtteranceStarted)
        }

        override fun onDone(utteranceId: String?) {
            utteranceId?.let(listener::onUtteranceEnded)
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            utteranceId?.let(listener::onUtteranceEnded)
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            utteranceId?.let(listener::onUtteranceEnded)
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            utteranceId?.let(listener::onUtteranceEnded)
        }
    }

    override fun speak(text: String, utteranceId: String) {
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    override fun stop() {
        runCatching { tts.stop() }
    }

    override fun shutdown() {
        runCatching { tts.shutdown() }
    }

    override fun setVoice(voiceId: String?) {
        this.voiceId = voiceId
        if (isReady) tts.applySessionVoice(voiceId)
    }

    override fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(VoiceSettings.MIN_SPEECH_RATE, VoiceSettings.MAX_SPEECH_RATE)
        if (isReady) tts.setSpeechRate(speechRate)
    }
}
