package com.rossomak.flashcards.core.common.voice

import android.speech.tts.Voice

/**
 * Shared curation rule for system TTS voices, used by voice enumeration, preview, and playback.
 * Restricted to US/GB/AU English — other English locales (e.g. India, Nigeria) produced
 * intonation users found jarring, and a full unfiltered voice list was too long to be usable.
 */
object VoiceCuration {

    private val PREFERRED_COUNTRIES = listOf("US", "GB", "AU")
    private const val MAX_VOICES_PER_COUNTRY = 3

    fun curate(voices: Collection<Voice>): List<Voice> =
        voices.filter { it.locale.language == "en" && it.locale.country in PREFERRED_COUNTRIES }
            .groupBy { it.locale.country }
            .entries
            .sortedBy { PREFERRED_COUNTRIES.indexOf(it.key) }
            .flatMap { (_, group) ->
                group.sortedWith(
                    compareByDescending<Voice> { it.quality }.thenBy { it.isNetworkConnectionRequired }
                ).take(MAX_VOICES_PER_COUNTRY)
            }
}
