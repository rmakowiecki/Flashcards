package com.rossomak.flashcards.feature.study.voice.data

import android.content.Context
import com.rossomak.flashcards.core.domain.model.GradingFailureReason
import com.rossomak.flashcards.core.domain.model.SpokenNotice
import com.rossomak.flashcards.core.ui.composables.rating.labelRes
import com.rossomak.flashcards.feature.study.R

/**
 * What [notice] says out loud. The feedback names the Rating and the rationale, never the
 * percentage; a grading failure names its cause, since the snackbar saying the same may never be
 * seen with the phone in a pocket.
 */
internal fun Context.spokenText(notice: SpokenNotice): String = when (notice) {
    is SpokenNotice.Feedback ->
        getString(R.string.study_session_voice_answer_grade_spoken_message, getString(notice.rating.labelRes), notice.rationale)
    SpokenNotice.SilenceSkip -> getString(R.string.study_session_voice_answer_skip_spoken_message)
    SpokenNotice.SilencePause -> getString(R.string.study_session_voice_answer_skip_pause_spoken_message)
    is SpokenNotice.GradingFailed -> when (notice.reason) {
        GradingFailureReason.NoConnection -> getString(R.string.study_session_voice_answer_offline_spoken_message)
        GradingFailureReason.ServiceError -> getString(R.string.study_session_voice_answer_service_error_spoken_message)
    }
    SpokenNotice.GradingPause -> getString(R.string.study_session_voice_answer_grading_pause_spoken_message)
    SpokenNotice.CaptureFailed -> getString(R.string.study_session_voice_answer_capture_unavailable_spoken_message)
}
