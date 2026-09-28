package com.rossomak.flashcards.core.domain.scoring

import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Loads the Card Progress merge cases shared with the Cloud Functions test suite
 * (`testdata/card-progress-merge/` at the repo root, carried as this module's test-fixtures
 * resources). The file's schema is documented in the README next to it. The classes here are
 * test-only: they exist to read that file, not to model the domain.
 */
object CardProgressMergeCases {

    private const val CASES_FILE = "card-progress-merge/merge-cases.json"

    val file: CardProgressMergeCaseFile by lazy { Json.decodeFromString(readResource(CASES_FILE)) }

    private fun readResource(path: String): String =
        requireNotNull(javaClass.classLoader.getResource(path)) { "test resource $path not found" }.readText()
}

@Serializable
data class CardProgressMergeCaseFile(
    val description: String,
    val cases: List<CardProgressMergeCase>,
)

@Serializable
data class CardProgressMergeCase(
    val name: String,
    val input: CardProgressMergeCaseInput,
    val expected: CardProgressMergeCaseExpected,
)

@Serializable
data class CardProgressMergeCaseInput(
    val priorCards: Map<String, Map<String, FlashcardStudyProgressState>>,
    val session: CardProgressMergeCaseSession,
)

@Serializable
data class CardProgressMergeCaseSession(
    val studyMode: String,
    val cardResults: List<CardProgressMergeCaseCardResult>,
)

/** [wasPreviouslyMastered] is `null` on a Fast entry. */
@Serializable
data class CardProgressMergeCaseCardResult(
    val cardId: String,
    val subcategoryId: String,
    val state: FlashcardStudyProgressState,
    val wasPreviouslyMastered: Boolean? = null,
)

@Serializable
data class CardProgressMergeCaseExpected(
    val cardUpdates: Map<String, Map<String, CardProgressMergeCaseUpdate>>,
    val summaryDeltas: Map<String, CardProgressMergeCaseDelta>,
    val newCardsStudied: Int,
    val previouslyMastered: Map<String, Boolean>,
)

@Serializable
data class CardProgressMergeCaseUpdate(
    val state: FlashcardStudyProgressState,
    val stampFirstStudied: Boolean,
    val stampMastered: Boolean,
) {
    fun toDomain(): CardProgressUpdate = CardProgressUpdate(state = state, stampFirstStudied = stampFirstStudied, stampMastered = stampMastered)
}

@Serializable
data class CardProgressMergeCaseDelta(
    val masteredDelta: Int,
    val studiedDelta: Int,
) {
    fun toDomain(): SubcategoryProgressDelta = SubcategoryProgressDelta(masteredDelta = masteredDelta, studiedDelta = studiedDelta)
}
