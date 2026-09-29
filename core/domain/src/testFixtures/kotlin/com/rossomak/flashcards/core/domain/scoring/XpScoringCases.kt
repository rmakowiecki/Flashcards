package com.rossomak.flashcards.core.domain.scoring

import com.rossomak.flashcards.core.domain.model.ScoringState
import com.rossomak.flashcards.core.domain.model.XpBreakdown
import com.rossomak.flashcards.core.domain.model.XpConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Loads the XP scoring cases shared with the Cloud Functions test suite (`testdata/xp-scoring/` at
 * the repo root, carried as this module's test-fixtures resources). The file's schema is documented in
 * the README next to it. The classes here are test-only: they exist to read that file, not to model
 * the domain.
 */
object XpScoringCases {

    /** The key a case's `skip` map uses for this runner. */
    const val RUNNER = "kotlin"

    private const val DIRECTORY = "xp-scoring"
    private const val CASES_FILE = "$DIRECTORY/scoring-cases.json"
    private const val DEFAULT_CONFIG_FILE = "$DIRECTORY/default-xp-config.json"
    private const val DEFAULT_CONFIG_NAME = "default"

    private val json = Json

    val defaultConfig: XpConfig by lazy { decodeConfig(readJsonObject(DEFAULT_CONFIG_FILE)) }

    val file: XpScoringCaseFile by lazy { json.decodeFromString(readResource(CASES_FILE)) }

    /** The case's named configuration with its `configOverrides` applied on top. */
    fun resolveConfig(input: XpScoringCaseInput): XpConfig {
        val base = when (input.config) {
            DEFAULT_CONFIG_NAME -> readJsonObject(DEFAULT_CONFIG_FILE)
            else -> requireNotNull(file.configs[input.config]) { "unknown config \"${input.config}\"" }
        }
        return decodeConfig(JsonObject(base + input.configOverrides.orEmpty()))
    }

    private fun decodeConfig(configJson: JsonObject): XpConfig = json.decodeFromJsonElement<XpScoringCaseConfig>(configJson).toDomain()

    private fun readJsonObject(path: String): JsonObject = json.decodeFromString(readResource(path))

    private fun readResource(path: String): String =
        requireNotNull(javaClass.classLoader.getResource(path)) { "test resource $path not found" }.readText()
}

@Serializable
data class XpScoringCaseFile(
    val description: String,
    val configs: Map<String, JsonObject>,
    val cases: List<XpScoringCase>,
)

@Serializable
data class XpScoringCase(
    val name: String,
    val kind: String,
    val skip: Map<String, String> = emptyMap(),
    val input: XpScoringCaseInput,
    val expected: JsonObject,
)

@Serializable
data class XpScoringCaseInput(
    val config: String,
    val configOverrides: JsonObject? = null,
    val priorState: XpScoringCasePriorState? = null,
    val session: XpScoringCaseSession? = null,
    val newCardsStudied: Int = 0,
    val level: Int? = null,
    val streakAndGoal: XpScoringCaseStreakAndGoal? = null,
)

@Serializable
data class XpScoringCaseStreakAndGoal(
    val studyDate: String,
    val dailyGoalMinutes: Int,
    val todayTotalSeconds: Long,
) {
    fun toDomain(): StreakAndGoalInput = StreakAndGoalInput(studyDate = studyDate, dailyGoalMinutes = dailyGoalMinutes, todayTotalSeconds = todayTotalSeconds)
}

/** Every field required, so the default configuration file cannot silently drop one. */
@Serializable
data class XpScoringCaseConfig(
    val newCardStudied: Int,
    val cardMastered: Int,
    val cardPartial: Int,
    val masteryDefended: Int,
    val cardDemastered: Int,
    val sessionCompleted: Int,
    val dailyGoalMet: Int,
    val streakPerDay: Int,
    val streakMaxPerDay: Int,
    val minuteStudied: Int,
    val levelCurveBase: Double,
    val levelCurveExponent: Double,
) {
    fun toDomain(): XpConfig = XpConfig(
        newCardStudied = newCardStudied,
        cardMastered = cardMastered,
        cardPartial = cardPartial,
        masteryDefended = masteryDefended,
        cardDemastered = cardDemastered,
        sessionCompleted = sessionCompleted,
        dailyGoalMet = dailyGoalMet,
        streakPerDay = streakPerDay,
        streakMaxPerDay = streakMaxPerDay,
        minuteStudied = minuteStudied,
        levelCurveBase = levelCurveBase,
        levelCurveExponent = levelCurveExponent,
    )
}

/** A partial scoring state: missing fields take the starting state's value. */
@Serializable
data class XpScoringCasePriorState(
    val xp: Long? = null,
    val level: Int? = null,
    val xpIntoCurrentLevel: Long? = null,
    val currentStreak: Int? = null,
    val bestStreak: Int? = null,
    val lastStudyDate: String? = null,
    val goalMetDate: String? = null,
    val studiedSecondsOnLastStudyDate: Long? = null,
) {
    fun toDomain(): ScoringState {
        val startingState = ScoringState()
        return ScoringState(
            xp = xp ?: startingState.xp,
            level = level ?: startingState.level,
            xpIntoCurrentLevel = xpIntoCurrentLevel ?: startingState.xpIntoCurrentLevel,
            currentStreak = currentStreak ?: startingState.currentStreak,
            bestStreak = bestStreak ?: startingState.bestStreak,
            lastStudyDate = lastStudyDate ?: startingState.lastStudyDate,
            goalMetDate = goalMetDate ?: startingState.goalMetDate,
            studiedSecondsOnLastStudyDate = studiedSecondsOnLastStudyDate ?: startingState.studiedSecondsOnLastStudyDate,
        )
    }
}

@Serializable
data class XpScoringCaseSession(
    val studyMode: String,
    val durationSeconds: Int,
    val abandoned: Boolean,
    val cardResults: List<XpScoringCaseCardResult>,
)

@Serializable
data class XpScoringCaseCardResult(
    val state: String,
    val wasPreviouslyMastered: Boolean = false,
)

@Serializable
data class ExpectedSessionXp(
    val breakdown: ExpectedBreakdown,
    val newScoringState: ExpectedScoringState,
    val levelsCrossed: List<Int>,
)

@Serializable
data class ExpectedBreakdown(
    val newCards: Int,
    val mastered: Int,
    val partial: Int,
    val masteryDefenseBonus: Int,
    val demastered: Int,
    val timeStudied: Int,
    val sessionCompletionBonus: Int,
    val dailyGoalBonus: Int,
    val streakBonus: Int,
    val xpTotal: Int,
) {
    fun toDomain(): XpBreakdown = XpBreakdown(
        newCards = newCards,
        mastered = mastered,
        partial = partial,
        masteryDefenseBonus = masteryDefenseBonus,
        demastered = demastered,
        timeStudied = timeStudied,
        sessionCompletionBonus = sessionCompletionBonus,
        dailyGoalBonus = dailyGoalBonus,
        streakBonus = streakBonus,
    )
}

@Serializable
data class ExpectedScoringState(
    val xp: Long,
    val level: Int,
    val xpIntoCurrentLevel: Long,
    val currentStreak: Int,
    val bestStreak: Int,
    val lastStudyDate: String,
    val goalMetDate: String,
    val studiedSecondsOnLastStudyDate: Long,
) {
    fun toDomain(): ScoringState = ScoringState(
        xp = xp,
        level = level,
        xpIntoCurrentLevel = xpIntoCurrentLevel,
        currentStreak = currentStreak,
        bestStreak = bestStreak,
        lastStudyDate = lastStudyDate,
        goalMetDate = goalMetDate,
        studiedSecondsOnLastStudyDate = studiedSecondsOnLastStudyDate,
    )
}

@Serializable
data class ExpectedStreakAndGoal(
    val streakBonus: Int,
    val dailyGoalBonus: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val lastStudyDate: String,
    val goalMetDate: String,
    val studiedSecondsOnLastStudyDate: Long,
) {
    fun toDomain(): StreakAndGoalAwards = StreakAndGoalAwards(
        streakBonus = streakBonus,
        dailyGoalBonus = dailyGoalBonus,
        currentStreak = currentStreak,
        bestStreak = bestStreak,
        lastStudyDate = lastStudyDate,
        goalMetDate = goalMetDate,
        studiedSecondsOnLastStudyDate = studiedSecondsOnLastStudyDate,
    )
}

@Serializable
data class ExpectedLevelThreshold(val threshold: Long)
