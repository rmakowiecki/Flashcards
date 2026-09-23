package com.rossomak.flashcards.core.data.permission

import android.Manifest
import com.rossomak.flashcards.core.domain.model.AppPermission
import com.rossomak.flashcards.core.domain.model.AppPermission.RecordAudio

/** The manifest permission string the system prompt and permission checks need for an [AppPermission]. */
fun AppPermission.toManifestPermission(): String = when (this) {
    RecordAudio -> Manifest.permission.RECORD_AUDIO
}
