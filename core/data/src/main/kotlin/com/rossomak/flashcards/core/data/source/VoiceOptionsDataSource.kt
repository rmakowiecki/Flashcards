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
     * Starts a short-lived engine purely to enumerate its voices, then shuts it down. Only the
     * engine's init callback lands on the main thread; enumerating (binder IPC, often hundreds of
     * voices), curating and mapping run on [Dispatchers.Default]. Throws if the engine fails to
     * initialize, so a caller can tell a failed load from an engine with no curated voices.
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
     * Must run on the main thread: the init callback is posted there, so it can only run once the
     * constructor has returned and `engine` is assigned — off-main, a fast callback could see no
     * engine and report a working one as failed. A failure to bind the engine service is still
     * reported synchronously, from inside the constructor, so that engine is shut down once the
     * constructor returns instead of from the callback.
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
        // Grouped by ISO country code (VoiceCuration guarantees one of US/GB/AU, never blank)
        // purely to number each group's voices 1-based — Voice.name is Android's own unique id,
        // but its format is engine-opaque (not documented as delimited in any particular way), so
        // parsing it for a display label risks collisions between unrelated voices; a friendly,
        // collision-free label is built in core:ui instead, from countryCode + this index alone
        // (see VoiceOption's own doc).
        return VoiceCuration.curate(voices.orEmpty())
            .groupBy { it.locale.country }
            .flatMap { (countryCode, countryVoices) ->
                countryVoices.mapIndexed { index, voice ->
                    VoiceOption(id = voice.name, countryCode = countryCode, variantIndex = index + 1)
                }
            }
    }
}
