package com.rossomak.flashcards.core.data.source

import android.content.Context
import com.rossomak.flashcards.core.common.loge
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * File-backed [AccountDeletionMarkerLocalDataSource]: [FILE_NAME] under [Context.filesDir], holding only
 * the uid. A half-written uid never matches a signed-in User, so the marker needs no atomic write.
 */
class FileAccountDeletionMarkerLocalDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AccountDeletionMarkerLocalDataSource {

    private val file: File get() = File(context.filesDir, FILE_NAME)

    override fun read(): String? = try {
        if (file.exists()) file.readText().ifBlank { null } else null
    } catch (exception: IOException) {
        loge(exception) { "Could not read the account deletion marker" }
        null
    }

    override fun write(uid: String): Boolean = try {
        file.writeText(uid)
        true
    } catch (exception: IOException) {
        loge(exception) { "Could not write the account deletion marker" }
        false
    }

    override fun clear() {
        if (file.exists() && !file.delete()) {
            loge { "Could not delete the account deletion marker" }
        }
    }

    private companion object {
        const val FILE_NAME = "account_deletion_marker"
    }
}
