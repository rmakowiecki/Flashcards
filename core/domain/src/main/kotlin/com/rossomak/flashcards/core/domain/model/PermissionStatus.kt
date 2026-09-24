package com.rossomak.flashcards.core.domain.model

/**
 * What the app knows about one [AppPermission].
 *
 * [Denied] means not granted, but the system will still show its prompt on the next request —
 * either it was never asked, or the user refused without choosing "Don't ask again".
 * [PermanentlyDenied] means the last request came back refused with no prompt left to show, so only
 * the app's system Settings page can grant it. Android 11+ reports a first prompt dismissed without
 * an answer the same way, so such a dismissal also reads as [PermanentlyDenied] until the next
 * request shows the real prompt again.
 */
sealed interface PermissionStatus {

    data object Denied : PermissionStatus

    data object PermanentlyDenied : PermissionStatus

    data object Granted : PermissionStatus
}
