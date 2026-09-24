package com.rossomak.flashcards.core.ui.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.glyph.CachedGlyph.Ready
import com.rossomak.flashcards.core.ui.glyph.CachedGlyph.Unavailable
import com.rossomak.flashcards.core.ui.glyph.rememberCategoryGlyph
import com.rossomak.flashcards.core.ui.theme.AppSizes
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.cornerRadius
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.toCategoryColor

/**
 * Glyph shown when [iconSvg] is absent (not yet curated) or malformed (invalid SVG) — both cases
 * land on the same fallback, no distinction made, no crash either way. See
 * docs/design/category-icon-color.md.
 */
private val FallbackIcon = Icons.Default.Folder

private val ICON_CONTENT_SIZE = 24.dp

/**
 * Rounded, tinted square that hosts a category's monochrome SVG glyph, rendered through
 * [CategoryGlyphCache][com.rossomak.flashcards.core.ui.glyph.CategoryGlyphCache] (once per process,
 * off the main thread) from a plain SVG document that arrives embedded on the `Category` object
 * itself — sibling of [FlashcardsIconTile] for callers whose icon/color are curated, nullable
 * Firestore data rather than a fixed local [androidx.compose.ui.graphics.vector.ImageVector]. Named `Vector`, not
 * `Remote`: nothing is fetched separately, no network/image-cache story.
 *
 * [iconSvg] and [color] are both nullable and independently fault-tolerant — an absent field and
 * a malformed one land on the same themed default, no crash either way. While an uncached glyph
 * loads, the tile shows its tinted container alone. All of that handling
 * lives here, not at the call site: [FlashcardsListGroup][com.rossomak.flashcards.core.ui.composables.lists.FlashcardsListGroup]
 * callers pass `category.color`/`category.iconSvg` straight through. See
 * docs/design/category-icon-color.md's Rendering section.
 */
@Composable
fun FlashcardsVectorIconTile(
    iconSvg: String?,
    color: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val tintColor = color?.let { runCatching { it.toCategoryColor() }.getOrNull() }
        ?: MaterialTheme.colorScheme.onSecondaryContainer
    val containerColor = tintColor.copy(alpha = DEFAULT_CONTAINER_ALPHA)
    val glyphCache = rememberCategoryGlyph(iconSvg = iconSvg, size = ICON_CONTENT_SIZE)

    Box(
        modifier = modifier
            .size(MaterialTheme.sizes.iconTile)
            .background(
                color = containerColor,
                shape = RoundedCornerShape(MaterialTheme.cornerRadius.small),
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (glyphCache) {
            // Still loading: the tinted container alone, never the fallback glyph as a placeholder.
            null -> Unit
            Unavailable -> CompositionLocalProvider(LocalContentColor provides tintColor) {
                Icon(imageVector = FallbackIcon, contentDescription = contentDescription)
            }
            is Ready -> Image(
                bitmap = glyphCache.glyph,
                contentDescription = contentDescription,
                modifier = Modifier.size(ICON_CONTENT_SIZE),
                colorFilter = rememberTintFilter(tintColor),
            )
        }
    }
}

/** Diameter of [FlashcardsInlineCategoryGlyph] — reuses the compact metadata-badge icon token. */
private val INLINE_GLYPH_SIZE = AppSizes.metadataBadgeIconCompact

/**
 * Bare category glyph, no tile/background — a small inline icon meant to sit inside a text line
 * (e.g. a search result's secondary line naming its parent category), sibling of
 * [FlashcardsVectorIconTile] for callers that don't want its 40dp tinted-container tile. Always
 * rendered in [tint] rather than the category's own [color] — a caller that wants the curated
 * color reads [FlashcardsVectorIconTile] instead. [iconSvg] absent or malformed both land on the
 * same generic fallback glyph, no crash either way — same handling [FlashcardsVectorIconTile]
 * gives its tile, just without the container.
 */
@Composable
fun FlashcardsInlineCategoryGlyph(
    iconSvg: String?,
    tint: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    when (val glyphCache = rememberCategoryGlyph(iconSvg = iconSvg, size = INLINE_GLYPH_SIZE)) {
        // Still loading: reserve the glyph's space so the text line doesn't shift when it arrives.
        null -> Spacer(modifier = modifier.size(INLINE_GLYPH_SIZE))
        Unavailable -> Icon(
            imageVector = FallbackIcon,
            contentDescription = contentDescription,
            modifier = modifier.size(INLINE_GLYPH_SIZE),
            tint = tint,
        )
        is Ready -> Image(
            bitmap = glyphCache.glyph,
            contentDescription = contentDescription,
            modifier = modifier.size(INLINE_GLYPH_SIZE),
            colorFilter = rememberTintFilter(tint),
        )
    }
}

/**
 * `SrcIn` tint for a cached glyph mask — recolors every opaque pixel regardless of the source SVG's
 * own fill, the same mechanism `Icon(tint = ...)` uses. A paint-level filter, so no offscreen layer.
 */
@Composable
private fun rememberTintFilter(tint: Color): ColorFilter = remember(tint) { ColorFilter.tint(tint) }

@ShowkaseComposable(name = "Vector icon tile", group = "Lists")
@Composable
fun FlashcardsVectorIconTileShowcase() {
    FlashcardsTheme {
        Surface {
            // Null iconSvg/color never resolve, so the showcase renders the fallback glyph and
            // themed default tint — exercising the same fallback an uncurated category hits.
            FlashcardsVectorIconTile(iconSvg = null, color = null, contentDescription = null)
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsVectorIconTilePreview() {
    FlashcardsTheme {
        Surface {
            FlashcardsVectorIconTile(iconSvg = null, color = null, contentDescription = null)
        }
    }
}
