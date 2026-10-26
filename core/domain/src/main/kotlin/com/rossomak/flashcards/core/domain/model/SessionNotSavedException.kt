package com.rossomak.flashcards.core.domain.model

/** [com.rossomak.flashcards.core.domain.usecase.SubmitStudySessionUseCase]'s failure when a finished session could not be queued, so it is lost. */
class SessionNotSavedException(sessionId: String, cause: Throwable? = null) : Exception("Session $sessionId could not be queued for delivery", cause)
