package com.rossomak.flashcards.feature.study.rated

import androidx.annotation.StringRes
import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating
import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating.Correct
import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating.Failed
import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating.PartiallyCorrect
import com.rossomak.flashcards.feature.study.R

/**
 * The Rating's name on the voice answer sheet. A grader's verdict, so it is shorter than the
 * manual self-rating labels, which answer "How well did you answer it?".
 */
@get:StringRes
internal val FlashcardAttemptRating.voiceLabelRes: Int
    get() = when (this) {
        Correct -> R.string.study_session_voice_answer_correct_label
        PartiallyCorrect -> R.string.study_session_voice_answer_partial_label
        Failed -> R.string.study_session_voice_answer_failed_label
    }
