package com.rossomak.flashcards.core.ui.glyph

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * Identity of one rendered glyph: the same SVG source at a different pixel size is a distinct entry.
 * Tint is deliberately absent — glyphs are cached as untinted masks and tinted at draw time.
 */
data class GlyphKey(val svgSource: String, val sizePx: Int)

/** Result of rendering a [GlyphKey], cached either way so a malformed source is never re-parsed. */
sealed interface CachedGlyph<out T> {
    data class Ready<T>(val glyph: T) : CachedGlyph<T>

    /** The source was malformed or rendering failed — callers show their fallback icon. */
    data object Unavailable : CachedGlyph<Nothing>
}

/**
 * Bounded, least-recently-used cache of rendered glyphs, shared across compositions so a glyph is
 * rendered once per process rather than once per appearance. [peek] is a synchronous, non-blocking
 * read for the composition's first frame; [load] renders a miss on [loadDispatcher], and concurrent
 * [load]s of the same key share that one render. A render that throws an [Exception] or returns null is
 * cached as [CachedGlyph.Unavailable]; an [Error] (e.g. `OutOfMemoryError` allocating the bitmap) is
 * transient, so it propagates to the waiting callers uncached and a later [load] retries.
 *
 * Generic over the glyph type and handed its [render] step so the caching rules are testable on the
 * plain JVM — the production instance is [CategoryGlyphCache].
 */
class GlyphCache<T : Any>(
    private val capacity: Int,
    loadDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val render: (GlyphKey) -> T?,
) {
    init {
        require(capacity > 0) { "capacity must be positive, was $capacity" }
    }

    private val lock = Any()
    private val loadScope = CoroutineScope(SupervisorJob() + loadDispatcher)
    private val entries = object : LinkedHashMap<GlyphKey, CachedGlyph<T>>(capacity, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<GlyphKey, CachedGlyph<T>>): Boolean = size > capacity
    }
    private val inFlightLoads = HashMap<GlyphKey, Deferred<CachedGlyph<T>>>()

    /** Returns the cached result for [key], or null if it hasn't been rendered yet. */
    fun peek(key: GlyphKey): CachedGlyph<T>? = synchronized(lock) { entries[key] }

    /** Returns the cached result for [key], rendering it off the caller's thread on a miss. */
    suspend fun load(key: GlyphKey): CachedGlyph<T> {
        val pendingLoad = synchronized(lock) {
            entries[key]?.let { return it }
            inFlightLoads.getOrPut(key) { loadScope.async { renderAndCache(key) } }
        }
        return pendingLoad.await()
    }

    // Any render Exception is an expected outcome (malformed curated SVG) meaning "no glyph"; Errors propagate.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun renderAndCache(key: GlyphKey): CachedGlyph<T> {
        try {
            val rendered = try {
                render(key)
            } catch (exception: Exception) {
                null
            }
            val result = rendered?.let { CachedGlyph.Ready(it) } ?: CachedGlyph.Unavailable
            synchronized(lock) { entries[key] = result }
            return result
        } finally {
            // Always cleared, even when an Error escapes, so a failed load never sticks for later callers.
            synchronized(lock) { inFlightLoads.remove(key) }
        }
    }

    private companion object {
        const val LOAD_FACTOR = 0.75f
    }
}
