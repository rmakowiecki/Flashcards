package com.rossomak.flashcards.feature.study.voice.data

import android.content.Context
import androidx.annotation.StringRes
import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating
import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating.Correct
import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating.Failed
import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating.PartiallyCorrect
import com.rossomak.flashcards.core.domain.model.GradingFailureReason
import com.rossomak.flashcards.core.domain.model.SpokenNotice
import com.rossomak.flashcards.feature.study.R

/**
 * What [notice] says aloud. A grading failure names its cause, since its snackbar may go unseen
 * with the phone in a pocket.
 */
internal fun Context.spokenText(notice: SpokenNotice): String = when (notice) {
    is SpokenNotice.Feedback -> {
        val ratingName = getString(notice.rating.voiceSpokenLabelRes)
        if (notice.rationale.isBlank()) ratingName else getString(R.string.study_session_voice_answer_grade_spoken_message, ratingName, notice.rationale)
    }
    SpokenNotice.SilenceSkip -> getString(R.string.study_session_voice_answer_skip_spoken_message)
    SpokenNotice.SilencePause -> getString(R.string.study_session_voice_answer_skip_pause_spoken_message)
    is SpokenNotice.GradingFailed -> when (notice.reason) {
        GradingFailureReason.NoConnection -> getString(R.string.study_session_voice_answer_offline_spoken_message)
        GradingFailureReason.ServiceError -> getString(R.string.study_session_voice_answer_service_error_spoken_message)
    }
    SpokenNotice.GradingPause -> getString(R.string.study_session_voice_answer_grading_pause_spoken_message)
    SpokenNotice.CaptureFailed -> getString(R.string.study_session_voice_answer_capture_unavailable_spoken_message)
}

/** The Rating's name as the feedback speaks it, where "Partial" alone would sound clipped. */
@get:StringRes
private val FlashcardAttemptRating.voiceSpokenLabelRes: Int
    get() = when (this) {
        Correct -> R.string.study_session_voice_answer_correct_spoken_message
        PartiallyCorrect -> R.string.study_session_voice_answer_partial_spoken_message
        Failed -> R.string.study_session_voice_answer_failed_spoken_message
    }
