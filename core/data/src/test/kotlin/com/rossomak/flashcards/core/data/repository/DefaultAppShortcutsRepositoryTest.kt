package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.domain.model.ShortcutTarget
import io.kotest.matchers.shouldBe
import org.junit.Test

/**
 * Covers only [truncateToDynamicLimit] — the one piece of `DefaultAppShortcutsRepository` with no
 * Android framework dependency. `pinShortcut`/`syncDynamicShortcuts` themselves need a real
 * `Intent`/`ShortcutManagerCompat`/`android.graphics.Canvas` runtime this project's plain-JVM
 * unit tests don't provide (see TESTING.md) and are exercised only by compilation + manual
 * verification.
 */
class DefaultAppShortcutsRepositoryTest {

    @Test
    fun `truncateToDynamicLimit keeps input order, does not re-sort`() {
        val favorites = listOf(target("category:3"), target("category:1"), target("category:2"))

        val result = truncateToDynamicLimit(favorites, maxCount = 3)

        result.map { it.id } shouldBe listOf("category:3", "category:1", "category:2")
    }

    @Test
    fun `truncateToDynamicLimit drops entries past maxCount, keeping the earliest`() {
        val favorites = listOf(target("category:1"), target("category:2"), target("category:3"))

        val result = truncateToDynamicLimit(favorites, maxCount = 2)

        result.map { it.id } shouldBe listOf("category:1", "category:2")
    }

    @Test
    fun `truncateToDynamicLimit with maxCount 0 returns empty`() {
        val favorites = listOf(target("category:1"))

        val result = truncateToDynamicLimit(favorites, maxCount = 0)

        result shouldBe emptyList()
    }

    @Test
    fun `truncateToDynamicLimit with maxCount above input size returns everything unchanged`() {
        val favorites = listOf(target("category:1"), target("category:2"))

        val result = truncateToDynamicLimit(favorites, maxCount = 10)

        result shouldBe favorites
    }

    private fun target(id: String) = ShortcutTarget(
        id = id,
        name = "Name $id",
        route = "/study/category/$id",
        iconSvg = null,
        color = null,
    )
}
