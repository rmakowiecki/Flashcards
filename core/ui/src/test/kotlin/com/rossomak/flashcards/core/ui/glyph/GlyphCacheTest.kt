package com.rossomak.flashcards.core.ui.glyph

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GlyphCacheTest {

    private val renderedKeys = mutableListOf<GlyphKey>()

    private fun createCache(dispatcher: CoroutineDispatcher, capacity: Int = DEFAULT_CAPACITY): GlyphCache<String> =
        GlyphCache(capacity = capacity, loadDispatcher = dispatcher) { key ->
            renderedKeys += key
            require(key.svgSource != MALFORMED_SVG) { "malformed" }
            if (key.svgSource == OUT_OF_MEMORY_SVG && renderedKeys.size == 1) throw OutOfMemoryError()
            "glyph:${key.svgSource}"
        }

    private fun key(svgSource: String = SVG_A, sizePx: Int = SIZE_SMALL): GlyphKey = GlyphKey(svgSource = svgSource, sizePx = sizePx)

    @Test
    fun `peek before any load returns null`() = runTest {
        val cache = createCache(StandardTestDispatcher(testScheduler))

        cache.peek(key()) shouldBe null
    }

    @Test
    fun `peek after load returns the rendered glyph synchronously`() = runTest {
        val cache = createCache(StandardTestDispatcher(testScheduler))

        cache.load(key())

        cache.peek(key()) shouldBe CachedGlyph.Ready("glyph:$SVG_A")
    }

    @Test
    fun `concurrent loads of the same key render once and share the result`() = runTest {
        val cache = createCache(StandardTestDispatcher(testScheduler))

        val results = List(CONCURRENT_REQUESTS) { async { cache.load(key()) } }.awaitAll()

        renderedKeys.size shouldBe 1
        results.toSet() shouldBe setOf(CachedGlyph.Ready("glyph:$SVG_A"))
    }

    @Test
    fun `load of a cached key does not render again`() = runTest {
        val cache = createCache(StandardTestDispatcher(testScheduler))

        cache.load(key())
        cache.load(key())

        renderedKeys.size shouldBe 1
    }

    @Test
    fun `malformed source resolves to Unavailable and is not re-rendered`() = runTest {
        val cache = createCache(StandardTestDispatcher(testScheduler))
        val malformedKey = key(svgSource = MALFORMED_SVG)

        val firstResult = cache.load(malformedKey)
        val secondResult = cache.load(malformedKey)

        firstResult.shouldBeInstanceOf<CachedGlyph.Unavailable>()
        secondResult.shouldBeInstanceOf<CachedGlyph.Unavailable>()
        cache.peek(malformedKey).shouldBeInstanceOf<CachedGlyph.Unavailable>()
        renderedKeys.size shouldBe 1
    }

    @Test
    fun `least recently used entry is evicted at capacity`() = runTest {
        val cache = createCache(StandardTestDispatcher(testScheduler), capacity = 2)
        cache.load(key(svgSource = SVG_A))
        cache.load(key(svgSource = SVG_B))
        cache.peek(key(svgSource = SVG_A))

        cache.load(key(svgSource = SVG_C))

        cache.peek(key(svgSource = SVG_B)) shouldBe null
        cache.peek(key(svgSource = SVG_A)) shouldBe CachedGlyph.Ready("glyph:$SVG_A")
        cache.peek(key(svgSource = SVG_C)) shouldBe CachedGlyph.Ready("glyph:$SVG_C")
    }

    @Test
    fun `different sizes of the same source are distinct entries`() = runTest {
        val cache = createCache(StandardTestDispatcher(testScheduler))

        cache.load(key(sizePx = SIZE_SMALL))

        cache.peek(key(sizePx = SIZE_LARGE)) shouldBe null
        cache.load(key(sizePx = SIZE_LARGE))
        renderedKeys shouldBe listOf(key(sizePx = SIZE_SMALL), key(sizePx = SIZE_LARGE))
    }

    @Test
    fun `render throwing an Error is not cached and a later load retries`() = runTest {
        val cache = createCache(StandardTestDispatcher(testScheduler))
        val flakyKey = key(svgSource = OUT_OF_MEMORY_SVG)

        shouldThrow<OutOfMemoryError> { cache.load(flakyKey) }

        cache.peek(flakyKey) shouldBe null
        cache.load(flakyKey) shouldBe CachedGlyph.Ready("glyph:$OUT_OF_MEMORY_SVG")
        renderedKeys.size shouldBe 2
    }

    @Test
    fun `non-positive capacity is rejected`() = runTest {
        shouldThrow<IllegalArgumentException> { createCache(StandardTestDispatcher(testScheduler), capacity = 0) }
    }

    private companion object {
        const val DEFAULT_CAPACITY = 8
        const val CONCURRENT_REQUESTS = 3
        const val SVG_A = "<svg id=\"a\"/>"
        const val SVG_B = "<svg id=\"b\"/>"
        const val SVG_C = "<svg id=\"c\"/>"
        const val MALFORMED_SVG = "not an svg"
        const val OUT_OF_MEMORY_SVG = "<svg id=\"oom\"/>"
        const val SIZE_SMALL = 24
        const val SIZE_LARGE = 48
    }
}
