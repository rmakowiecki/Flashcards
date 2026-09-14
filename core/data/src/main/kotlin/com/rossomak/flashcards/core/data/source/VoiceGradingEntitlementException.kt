package com.rossomak.flashcards.core.data.source

/**
 * Thrown when the backend (real or simulated) rejects a voice grading request because the
 * caller has no active premium entitlement — the client-side mirror of the Cloud Function's
 * HTTP 403. Deliberately a distinct type so callers can voice a specific message instead of a
 * generic network error.
 */
class VoiceGradingEntitlementException :
    Exception("Voice grading rejected: no active premium entitlement")
