package com.rossomak.flashcards.core.data.source

import android.content.Context
import android.speech.tts.TextToSpeech
import com.rossomak.flashcards.core.data.voice.VoiceCuration
import com.rossomak.flashcards.core.domain.model.VoiceOption
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class VoiceOptionsDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /**
     * Android lists voices only through an initialized engine, so this starts a short-lived one.
     * Throws if it fails to initialize.
     */
    suspend fun getAvailableVoices(): List<VoiceOption> {
        val engine = withContext(Dispatchers.Main.immediate) { initializedEngine() }
        return try {
            withContext(Dispatchers.Default) { engine.curatedVoiceOptions() }
        } finally {
            engine.shutdown()
        }
    }

    /**
     * Main thread only: the init callback is posted there, so it runs after `engine` is assigned.
     * A bind failure is reported synchronously from the constructor, hence the shutdown after it.
     */
    private suspend fun initializedEngine(): TextToSpeech = suspendCancellableCoroutine { continuation ->
        var engine: TextToSpeech? = null
        continuation.invokeOnCancellation { engine?.shutdown() }
        val createdEngine = TextToSpeech(context) { status ->
            val initialized = engine
            if (status == TextToSpeech.SUCCESS && initialized != null) {
                continuation.resume(initialized) { _, unused, _ -> unused.shutdown() }
            } else {
                initialized?.shutdown()
                continuation.resumeWithException(IllegalStateException("TextToSpeech failed to initialize, status $status"))
            }
        }
        engine = createdEngine
        if (!continuation.isActive) createdEngine.shutdown()
    }

    private fun TextToSpeech.curatedVoiceOptions(): List<VoiceOption> {
        // App is English-only content — never leave this on the device's system locale.
        language = Locale.US
        // Numbered 1-based per country for the display label; Voice.name is engine-opaque, never parsed.
        return VoiceCuration.curate(voices.orEmpty())
            .groupBy { it.locale.country }
            .flatMap { (countryCode, countryVoices) ->
                countryVoices.mapIndexed { index, voice ->
                    VoiceOption(id = voice.name, countryCode = countryCode, variantIndex = index + 1)
                }
            }
    }
}
