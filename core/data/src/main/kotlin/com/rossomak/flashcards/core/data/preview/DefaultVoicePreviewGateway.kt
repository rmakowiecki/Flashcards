package com.rossomak.flashcards.core.data.preview

import android.content.Context
import android.speech.tts.TextToSpeech
import com.rossomak.flashcards.core.common.loge
import com.rossomak.flashcards.core.data.voice.VoiceCuration
import com.rossomak.flashcards.core.domain.repository.VoicePreviewGateway
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultVoicePreviewGateway @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : VoicePreviewGateway {

    @Volatile private var tts: TextToSpeech? = null

    @Volatile private var ttsReady = false

    @Volatile private var ttsInitFailed = false

    @Volatile private var pendingPreviewVoiceId: String? = null

    @Volatile private var pendingPreviewRate: Float = 1f

    @Synchronized
    private fun ensureTts() {
        if (tts != null && !ttsInitFailed) return
        tts?.shutdown()
        ttsInitFailed = false
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                runCatching { tts?.language = Locale.US }
                ttsReady = true
                applyPendingPreview()
            } else {
                loge { "TextToSpeech init failed with status $status" }
                ttsInitFailed = true
            }
        }
    }

    override fun preview(voiceId: String?, speechRate: Float) {
        pendingPreviewVoiceId = voiceId
        pendingPreviewRate = speechRate
        ensureTts()
        if (ttsReady) applyPendingPreview()
    }

    private fun applyPendingPreview() {
        val engine = tts ?: return
        engine.stop()
        val targetVoiceId = pendingPreviewVoiceId
        val resolvedVoice = if (targetVoiceId != null) {
            engine.voices?.firstOrNull { it.name == targetVoiceId }
        } else {
            VoiceCuration.curate(engine.voices.orEmpty()).firstOrNull()
        }
        resolvedVoice?.let { engine.voice = it }
        engine.setSpeechRate(pendingPreviewRate)
        engine.speak(SAMPLE_TEXT, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_PREVIEW)
    }

    override fun stop() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    companion object {
        private const val UTTERANCE_PREVIEW = "preview"
        private const val SAMPLE_TEXT =
            "Here is an example of how this voice sounds at the selected speed."
    }
}
