package com.rossomak.flashcards.core.ui.glyph

import android.graphics.Canvas
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.createBitmap
import com.caverock.androidsvg.SVG

/** Sized for a few dozen Categories, each seen at a couple of sizes. */
private const val CATEGORY_GLYPH_CACHE_CAPACITY = 128

/**
 * Process-wide cache of rasterized Category glyph masks, untinted — a plain singleton rather than a
 * Hilt binding because its readers are Composables. Survives the Main screen's tab host disposing a
 * tab, so returning to Home or Browse draws every already-seen glyph in its first frame.
 */
object CategoryGlyphCache {
    private val cache = GlyphCache(capacity = CATEGORY_GLYPH_CACHE_CAPACITY, render = ::renderCategoryGlyph)

    fun peek(key: GlyphKey): CachedGlyph<ImageBitmap>? = cache.peek(key)

    suspend fun load(key: GlyphKey): CachedGlyph<ImageBitmap> = cache.load(key)
}

/**
 * Parses [Category.iconSvg][com.rossomak.flashcards.core.domain.model.Category.iconSvg] — a plain
 * SVG document, not Android VectorDrawable XML — via `androidsvg` rather than a first-party
 * parser: real category icons carry `transform="translate(...) scale(...)"` attributes, a
 * compound mini-language a hand-rolled parser would have to tokenize itself, and `androidsvg`
 * implements full SVG semantics (transforms, nested groups, fill rules) for free.
 * `fill`/`fill-rule` in the source SVG are irrelevant and never read: tint always overrides the
 * rendered color when the glyph is drawn (see `FlashcardsVectorIconTile`), result flattens to one tint color.
 * Any malformed SVG throws — `GlyphCache` turns that into its cached "no glyph" outcome, so the tile shows its fallback icon.
 */
fun String.toCategorySvg(): SVG = SVG.getFromString(this)

/**
 * Renders the SVG stretched to fill a [GlyphKey.sizePx] square, in its source colors. Only the alpha
 * channel matters: callers draw it with a `SrcIn` tint color filter, which recolors every opaque pixel
 * — a paint-level filter, no offscreen layer. Keeping tint out of the bitmap means one entry serves
 * every tint, so a tint change (selection toggle, light/dark switch) never misses the cache. Throws on
 * a malformed source; [GlyphCache] turns that into [CachedGlyph.Unavailable].
 */
private fun renderCategoryGlyph(key: GlyphKey): ImageBitmap {
    // Rendered at the SVG's native size, then stretched via drawPicture(picture, dst) — the
    // no-bounds overload draws at native size with no scaling.
    val picture = key.svgSource.toCategorySvg().renderToPicture()
    val bitmap = createBitmap(key.sizePx, key.sizePx)
    val canvas = Canvas(bitmap)
    canvas.drawPicture(picture, RectF(0f, 0f, key.sizePx.toFloat(), key.sizePx.toFloat()))
    return bitmap.asImageBitmap()
}

/**
 * The untinted glyph mask for [iconSvg] at [size]: read synchronously from [CategoryGlyphCache] so a cached
 * glyph draws in the same frame, otherwise loaded off the main thread. Null while that load is in
 * flight — callers show nothing rather than their fallback, which would flash the wrong icon. A null
 * [iconSvg] is [CachedGlyph.Unavailable] straight away.
 */
@Composable
internal fun rememberCategoryGlyph(iconSvg: String?, size: Dp): CachedGlyph<ImageBitmap>? {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val key = iconSvg?.let { GlyphKey(svgSource = it, sizePx = sizePx) }
    val glyphState = remember(key) {
        mutableStateOf(if (key == null) CachedGlyph.Unavailable else CategoryGlyphCache.peek(key))
    }
    LaunchedEffect(key) {
        if (key != null && glyphState.value == null) glyphState.value = CategoryGlyphCache.load(key)
    }
    return glyphState.value
}
