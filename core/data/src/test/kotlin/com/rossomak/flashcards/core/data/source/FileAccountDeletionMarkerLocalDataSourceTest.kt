package com.rossomak.flashcards.core.data.source

import android.content.Context
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileAccountDeletionMarkerLocalDataSourceTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = mockk<Context>().also { every { it.filesDir } answers { temporaryFolder.root } }

    private fun createDataSource() = FileAccountDeletionMarkerLocalDataSource(context)

    @Test
    fun `nothing is marked on a fresh install`() {
        createDataSource().read() shouldBe null
    }

    @Test
    fun `a written uid survives a new instance and is gone once cleared`() {
        createDataSource().write(UID)

        createDataSource().read() shouldBe UID

        createDataSource().clear()
        createDataSource().read() shouldBe null
    }

    @Test
    fun `an unreadable marker reads as nothing marked instead of throwing`() {
        File(temporaryFolder.root, FILE_NAME).mkdir()

        createDataSource().read() shouldBe null
    }

    private companion object {
        const val UID = "uid-deleted"
        const val FILE_NAME = "account_deletion_marker"
    }
}
