package com.rossomak.flashcards.feature.study.preview

/** Why the Preview Study Session Screen has nothing to show. Each reason has its own error state. */
sealed interface PreviewLoadFailureReason {

    /** A read failed, most often offline. Retry can recover. */
    data object ReadFailed : PreviewLoadFailureReason

    /** The server confirms none of the session's Subcategories exist any more. Retry cannot recover. */
    data object SubcategoriesUnavailable : PreviewLoadFailureReason

    /** The server confirms the Quick Session's Category no longer exists. Retry cannot recover. */
    data object CategoryUnavailable : PreviewLoadFailureReason
}
