package com.rossomak.flashcards.core.data.source

import android.content.Context
import android.speech.tts.TextToSpeech
import com.rossomak.flashcards.core.data.voice.VoiceCuration
import com.rossomak.flashcards.core.domain.model.VoiceOption
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class VoiceOptionsDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun getAvailableVoices(): List<VoiceOption> = suspendCancellableCoroutine { continuation ->
        var tts: TextToSpeech? = null
        continuation.invokeOnCancellation { tts?.shutdown() }
        tts = TextToSpeech(context) { status ->
            val voices = if (status == TextToSpeech.SUCCESS) {
                // Grouped by ISO country code (VoiceCuration guarantees one of US/GB/AU, never
                // blank) purely to number each group's voices 1-based — Voice.name is Android's
                // own unique id, but its format is engine-opaque (not documented as delimited in
                // any particular way), so parsing it for a display label risks collisions between
                // unrelated voices; a friendly, collision-free label is built in core:ui instead,
                // from countryCode + this index alone (see VoiceOption's own doc).
                VoiceCuration.curate(tts?.voices.orEmpty())
                    .groupBy { it.locale.country }
                    .flatMap { (countryCode, countryVoices) ->
                        countryVoices.mapIndexed { index, voice ->
                            VoiceOption(id = voice.name, countryCode = countryCode, variantIndex = index + 1)
                        }
                    }
            } else {
                emptyList()
            }
            tts?.shutdown()
            continuation.resume(voices)
        }
    }
}
