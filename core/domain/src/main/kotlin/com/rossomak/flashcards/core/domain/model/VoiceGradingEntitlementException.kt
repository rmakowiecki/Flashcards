package com.rossomak.flashcards.core.domain.model

/**
 * Thrown when the backend (real or simulated) rejects a voice grading request because the
 * caller has no active premium entitlement — the client-side mirror of the Cloud Function's
 * HTTP 403. Deliberately a distinct type so callers can voice a specific message instead of a
 * generic network error. Domain-owned (not `core.data`) since it is part of
 * [com.rossomak.flashcards.core.domain.repository.VoiceAnswerGradingRepository]'s documented
 * failure contract, and feature layers must be able to catch it without depending on `core:data`.
 */
class VoiceGradingEntitlementException :
    Exception("Voice grading rejected: no active premium entitlement")
