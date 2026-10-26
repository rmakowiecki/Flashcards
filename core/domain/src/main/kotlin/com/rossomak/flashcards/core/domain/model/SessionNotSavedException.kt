package com.rossomak.flashcards.core.domain.model

/**
 * The failure [com.rossomak.flashcards.core.domain.usecase.SubmitStudySessionUseCase] returns when a
 * finished session could not be queued for delivery: nothing on the device holds the session, so it is
 * lost, unlike a failed XP preview of a session that is queued. A distinct type so the Session Summary
 * can tell the User the session was not saved.
 */
class SessionNotSavedException(sessionId: String, cause: Throwable? = null) : Exception("Session $sessionId could not be queued for delivery", cause)
