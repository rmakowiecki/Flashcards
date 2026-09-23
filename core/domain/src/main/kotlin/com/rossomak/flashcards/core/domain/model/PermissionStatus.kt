package com.rossomak.flashcards.core.domain.model

/**
 * What the app knows about one [AppPermission].
 *
 * [Denied] is a soft denial: the system will still show its prompt on the next request.
 * [PermanentlyDenied] means the system no longer prompts, so only the app's system Settings page
 * can grant it. A prompt dismissed without an answer is neither — it stays [NotRequested].
 */
sealed interface PermissionStatus {

    data object NotRequested : PermissionStatus

    data object Denied : PermissionStatus

    data object PermanentlyDenied : PermissionStatus

    data object Granted : PermissionStatus
}
