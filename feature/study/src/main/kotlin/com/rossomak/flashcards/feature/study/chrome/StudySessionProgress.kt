package com.rossomak.flashcards.feature.study.chrome

/**
 * The progress row [StudySessionHeader] shows: an already-formatted [label] plus the
 * [completedCount] out of [totalCount] it reads, with the bar's [fraction] derived from those
 * counts so the two can never disagree.
 */
data class StudySessionProgress(val label: String, val completedCount: Int, val totalCount: Int) {
    val fraction: Float get() = if (totalCount > 0) completedCount / totalCount.toFloat() else 0f
}
