package com.rossomak.flashcards.core.data.source

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.rossomak.flashcards.core.data.model.XpConfigDto
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

/**
 * Reads the server-owned XP configuration document, `config/xp`: every
 * [com.rossomak.flashcards.core.domain.model.XpConfig] field, admin-edited, and the values the
 * `submitStudySession` Cloud Function scores every session with. Readable by any signed-in user, a
 * Guest included (`firestore.rules`).
 */
class XpConfigRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    /** `null` when the document does not exist. */
    suspend fun getXpConfig(): XpConfigDto? {
        val document = firestore.collection(COLLECTION_CONFIG).document(DOCUMENT_XP).get().await()
        if (!document.exists()) return null

        return XpConfigDto(
            newCardStudied = document.number(FIELD_NEW_CARD_STUDIED),
            cardMastered = document.number(FIELD_CARD_MASTERED),
            cardPartial = document.number(FIELD_CARD_PARTIAL),
            masteryDefended = document.number(FIELD_MASTERY_DEFENDED),
            cardDemastered = document.number(FIELD_CARD_DEMASTERED),
            sessionCompleted = document.number(FIELD_SESSION_COMPLETED),
            dailyGoalMet = document.number(FIELD_DAILY_GOAL_MET),
            streakPerDay = document.number(FIELD_STREAK_PER_DAY),
            streakMaxPerDay = document.number(FIELD_STREAK_MAX_PER_DAY),
            minuteStudied = document.number(FIELD_MINUTE_STUDIED),
            levelCurveBase = document.number(FIELD_LEVEL_CURVE_BASE),
            levelCurveExponent = document.number(FIELD_LEVEL_CURVE_EXPONENT),
        )
    }

    private fun DocumentSnapshot.number(field: String): Number? = get(field) as? Number

    private companion object {
        const val COLLECTION_CONFIG = "config"
        const val DOCUMENT_XP = "xp"
        const val FIELD_NEW_CARD_STUDIED = "newCardStudied"
        const val FIELD_CARD_MASTERED = "cardMastered"
        const val FIELD_CARD_PARTIAL = "cardPartial"
        const val FIELD_MASTERY_DEFENDED = "masteryDefended"
        const val FIELD_CARD_DEMASTERED = "cardDemastered"
        const val FIELD_SESSION_COMPLETED = "sessionCompleted"
        const val FIELD_DAILY_GOAL_MET = "dailyGoalMet"
        const val FIELD_STREAK_PER_DAY = "streakPerDay"
        const val FIELD_STREAK_MAX_PER_DAY = "streakMaxPerDay"
        const val FIELD_MINUTE_STUDIED = "minuteStudied"
        const val FIELD_LEVEL_CURVE_BASE = "levelCurveBase"
        const val FIELD_LEVEL_CURVE_EXPONENT = "levelCurveExponent"
    }
}
