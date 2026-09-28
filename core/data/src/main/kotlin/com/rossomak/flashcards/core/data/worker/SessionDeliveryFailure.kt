package com.rossomak.flashcards.core.data.worker

import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.FirebaseFunctionsException.Code.FAILED_PRECONDITION
import com.google.firebase.functions.FirebaseFunctionsException.Code.INVALID_ARGUMENT
import com.google.firebase.functions.FirebaseFunctionsException.Code.PERMISSION_DENIED

/** How [SessionSubmissionDeliveryWorker] treats one failed delivery of a pending session. */
enum class SessionDeliveryFailure {

    /** Might succeed later (network, outage, expired token): keep the entry queued and retry. */
    Transient,

    /** The server rejected this session and always will: dead-letter it. */
    Permanent,
}

/**
 * Only a `submitStudySession` rejection of the session itself is permanent: `INVALID_ARGUMENT`,
 * `FAILED_PRECONDITION` or `PERMISSION_DENIED`. Every other Functions code and every other exception
 * (I/O and so on) is transient, so a bad network can never make the queue give up on a session.
 */
fun classifySessionDeliveryFailure(exception: Throwable): SessionDeliveryFailure = when {
    exception is FirebaseFunctionsException && exception.code in PERMANENT_CODES -> SessionDeliveryFailure.Permanent
    else -> SessionDeliveryFailure.Transient
}

private val PERMANENT_CODES = setOf(INVALID_ARGUMENT, FAILED_PRECONDITION, PERMISSION_DENIED)
