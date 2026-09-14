package com.rossomak.flashcards.core.data.source

import com.rossomak.flashcards.core.data.model.EntitlementDto
import com.rossomak.flashcards.core.data.model.VoiceGradingStreamEventDto
import com.rossomak.flashcards.core.domain.model.VoiceGradingEntitlementException
import java.io.IOException
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Test-only [VoiceGradingRemoteDataSource] double (ADR-0029 §7): the runtime fake/real router is gone, so this
 * survives purely as a DI stand-in in unit tests. Offline / no-cost manual iteration is now the
 * Firebase emulator's job (deferred), not an in-app fake.
 *
 * Mimics realistic behavior rather than a canned success: transcripts contain disfluencies and
 * occasional blurted PII (which the sanitize step then strips), grades vary across the full range,
 * latency is simulated, and a small share of calls fail with an [IOException] so retry/error paths
 * get exercised. Entitlement rejection mirrors the server-side PERMISSION_DENIED via
 * [VoiceGradingEntitlementException] — the client never gates on a local premium flag.
 */
class FakeVoiceGradingRemoteDataSource : VoiceGradingRemoteDataSource {

    // Unit tests disable failure/latency injection so assertions on response shape stay
    // deterministic.
    var isTransientFailureInjectionEnabled = true
    var isLatencySimulationEnabled = true

    /** What the *simulated* server-side entitlement record says for this user. */
    var simulatePremiumEntitlement = true

    /**
     * Mimics the streamed callable (ADR-0028): an upload+STT+sanitize delay before the transcript
     * chunk, then an independent grading-LLM delay before the final grade — two separate
     * [simulateNetworkCall] windows so a transient failure can land either before any chunk is sent
     * or after the transcript already arrived.
     */
    override fun transcribeAndGradeSpokenAnswer(
        cardId: String,
        question: String,
        expectedAnswer: String,
        wavBytes: ByteArray,
    ): Flow<VoiceGradingStreamEventDto> = flow {
        simulateNetworkCall()
        enforceSimulatedEntitlement()
        val spokenTranscript = plausibleSpokenAnswer(expectedAnswer, wavBytes.size)
        val sanitizedTranscript = sanitize(spokenTranscript)
        emit(VoiceGradingStreamEventDto.TranscriptChunk(sanitizedTranscript))

        simulateNetworkCall()
        val gradePercent = grade(expectedAnswer, sanitizedTranscript)
        emit(VoiceGradingStreamEventDto.Graded(gradePercent, feedbackFor(gradePercent)))
    }

    override suspend fun transcribeAndSanitize(wavBytes: ByteArray): Result<String> = try {
        simulateNetworkCall()
        enforceSimulatedEntitlement()
        val sampleAnswer = SAMPLE_ANSWERS.random()
        Result.success(sanitize(plausibleSpokenAnswer(sampleAnswer, wavBytes.size)))
    } catch (exception: VoiceGradingEntitlementException) {
        Result.failure(exception)
    } catch (exception: IOException) {
        Result.failure(exception)
    }

    override suspend fun checkEntitlement(): EntitlementDto {
        simulateNetworkCall()
        return EntitlementDto(isPremium = simulatePremiumEntitlement)
    }

    private suspend fun simulateNetworkCall() {
        if (isLatencySimulationEnabled) delay(Random.nextLong(MIN_LATENCY_MS, MAX_LATENCY_MS))
        if (isTransientFailureInjectionEnabled && Random.nextInt(100) < TRANSIENT_FAILURE_PERCENT) {
            throw IOException("Simulated transient network failure")
        }
    }

    private fun enforceSimulatedEntitlement() {
        if (!simulatePremiumEntitlement) {
            throw VoiceGradingEntitlementException()
        }
    }

    /**
     * Builds what a real STT transcript of a spoken attempt at [expectedAnswer] would look like: a
     * subset of the expected answer's words (recall varies per call), sprinkled with disfluencies
     * and — occasionally — blurted PII. Longer recordings keep more words.
     */
    private fun plausibleSpokenAnswer(expectedAnswer: String, wavSizeBytes: Int): String {
        val words = expectedAnswer.split(WHITESPACE_REGEX).filter { it.isNotBlank() }
        if (words.isEmpty()) return DISFLUENCIES.random()
        val durationSeconds = wavSizeBytes / (WAV_BYTES_PER_SECOND.toDouble())
        val lengthBias = (durationSeconds / 10.0).coerceIn(0.0, 0.3)
        val recallRatio = (Random.nextDouble(MIN_RECALL_RATIO, MAX_RECALL_RATIO) + lengthBias).coerceAtMost(1.0)
        val spoken = mutableListOf<String>()
        words.forEach { word ->
            if (Random.nextDouble() < DISFLUENCY_PROBABILITY) spoken += DISFLUENCIES.random()
            if (Random.nextDouble() < recallRatio) {
                spoken += word.lowercase().trim('.', ',', ';', ':')
                if (Random.nextDouble() < WORD_REPEAT_PROBABILITY) spoken += spoken.last()
            }
        }
        if (Random.nextDouble() < PII_BLURT_PROBABILITY) spoken += PII_BLURTS.random()
        return spoken.joinToString(" ").ifBlank { DISFLUENCIES.random() }
    }

    /** PII stripped, disfluencies and immediate word repeats normalized. */
    private fun sanitize(transcript: String): String {
        var sanitized = transcript
            .replace(EMAIL_REGEX, PII_PLACEHOLDER)
            .replace(PHONE_REGEX, PII_PLACEHOLDER)
            .replace(NAME_INTRO_REGEX, PII_PLACEHOLDER)
        val withoutDisfluencies = sanitized.split(WHITESPACE_REGEX)
            .filter { it.isNotBlank() && it.lowercase().trim(',', '.') !in DISFLUENCIES }
        val deduplicated = withoutDisfluencies.filterIndexed { index, word ->
            index == 0 || word != withoutDisfluencies[index - 1]
        }
        sanitized = deduplicated.joinToString(" ")
        return sanitized.replaceFirstChar { it.uppercase() }
    }

    /** Token-overlap completeness heuristic with jitter, standing in for LLM judgment. */
    private fun grade(expectedAnswer: String, sanitizedTranscript: String): Int {
        val expectedTokens = expectedAnswer.tokens()
        if (expectedTokens.isEmpty()) return 0
        val transcriptTokens = sanitizedTranscript.tokens().toSet()
        val coveredCount = expectedTokens.count { it in transcriptTokens }
        val coverage = coveredCount * 100 / expectedTokens.size
        val jitter = Random.nextInt(-GRADE_JITTER, GRADE_JITTER + 1)
        return (coverage + jitter).coerceIn(0, 100)
    }

    private fun feedbackFor(gradePercent: Int): String = when {
        gradePercent >= 85 -> HIGH_GRADE_FEEDBACK.random()
        gradePercent >= 55 -> MID_GRADE_FEEDBACK.random()
        gradePercent >= 25 -> LOW_GRADE_FEEDBACK.random()
        else -> MISS_FEEDBACK.random()
    }

    private fun String.tokens(): List<String> =
        lowercase().split(NON_WORD_REGEX).filter { it.length > 2 }

    private companion object {
        const val MIN_LATENCY_MS = 400L
        const val MAX_LATENCY_MS = 1_500L
        const val TRANSIENT_FAILURE_PERCENT = 5
        const val WAV_BYTES_PER_SECOND = 32_044 // 16kHz mono 16-bit + header amortized
        const val MIN_RECALL_RATIO = 0.45
        const val MAX_RECALL_RATIO = 0.95
        const val DISFLUENCY_PROBABILITY = 0.12
        const val WORD_REPEAT_PROBABILITY = 0.05
        const val PII_BLURT_PROBABILITY = 0.15
        const val GRADE_JITTER = 8
        const val PII_PLACEHOLDER = "[redacted]"

        val WHITESPACE_REGEX = Regex("\\s+")
        val NON_WORD_REGEX = Regex("[^a-z0-9]+")
        val EMAIL_REGEX = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
        val PHONE_REGEX = Regex("\\+?\\d[\\d -]{7,}\\d")
        val NAME_INTRO_REGEX = Regex("(?i)my name is \\w+( \\w+)?")

        val DISFLUENCIES = listOf("um", "uh", "erm", "like", "you know", "so")
        val PII_BLURTS = listOf(
            "my name is Jane Doe by the way",
            "call me at +1 555 123 4567",
            "send it to jane.doe@example.com",
        )
        val SAMPLE_ANSWERS = listOf(
            "A foreground service keeps running with a persistent notification even when the app is backgrounded",
            "StateFlow holds a single value and replays it to new collectors while SharedFlow does not retain state by default",
            "Bluetooth SCO routes the microphone through the hands-free profile at narrowband sample rates",
        )
        val HIGH_GRADE_FEEDBACK = listOf(
            "Excellent — you covered all the key points of the expected answer.",
            "Complete answer; terminology and reasoning both accurate.",
            "Spot on. Minor phrasing differences only.",
        )
        val MID_GRADE_FEEDBACK = listOf(
            "Good core understanding, but you missed one or two supporting details.",
            "Mostly right — the main concept landed, though the answer lacked precision.",
            "Partial credit: correct direction, but an important qualifier was missing.",
        )
        val LOW_GRADE_FEEDBACK = listOf(
            "You touched the topic but missed the main point of the expected answer.",
            "Only fragments matched — revisit the core definition.",
            "The answer drifted; key terms from the expected answer never came up.",
        )
        val MISS_FEEDBACK = listOf(
            "That did not match the expected answer — worth re-reading this card.",
            "No substantive overlap with the expected answer detected.",
        )
    }
}
