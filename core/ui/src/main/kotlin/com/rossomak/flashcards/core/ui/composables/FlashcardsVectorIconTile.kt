package com.rossomak.flashcards.core.ui.composables

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.caverock.androidsvg.SVG
import com.rossomak.flashcards.core.ui.theme.AppSizes
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.cornerRadius
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.toCategoryColor
import com.rossomak.flashcards.core.ui.theme.toCategorySvg

/**
 * Glyph shown when [iconSvg] is absent (not yet curated) or malformed (invalid SVG) — both cases
 * land on the same fallback, no distinction made, no crash either way. See
 * docs/design/category-icon-color.md.
 */
private val FallbackIcon = Icons.Default.Folder

private val ICON_CONTENT_SIZE = 24.dp

/**
 * Rounded, tinted square that hosts a category's monochrome SVG glyph, rasterized at render time
 * from a plain SVG document that arrives embedded on the `Category` object itself — sibling of
 * [FlashcardsIconTile] for callers whose icon/color are curated, nullable Firestore data rather
 * than a fixed local [androidx.compose.ui.graphics.vector.ImageVector]. Named `Vector`, not
 * `Remote`: nothing is fetched separately, no network/image-cache story.
 *
 * [iconSvg] and [color] are both nullable and independently fault-tolerant — an absent field and
 * a malformed one land on the same themed default, no crash either way. All of that handling
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
    val svg = remember(iconSvg) {
        iconSvg?.let { runCatching { it.toCategorySvg() }.getOrNull() }
    }

    Box(
        modifier = modifier
            .size(MaterialTheme.sizes.iconTile)
            .background(
                color = containerColor,
                shape = RoundedCornerShape(MaterialTheme.cornerRadius.small),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (svg == null) {
            CompositionLocalProvider(LocalContentColor provides tintColor) {
                Icon(imageVector = FallbackIcon, contentDescription = contentDescription)
            }
        } else {
            SvgGlyph(
                svg = svg,
                tint = tintColor,
                contentDescription = contentDescription,
                modifier = Modifier.size(ICON_CONTENT_SIZE),
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
    val svg = remember(iconSvg) {
        iconSvg?.let { runCatching { it.toCategorySvg() }.getOrNull() }
    }
    if (svg == null) {
        Icon(
            imageVector = FallbackIcon,
            contentDescription = contentDescription,
            modifier = modifier.size(INLINE_GLYPH_SIZE),
            tint = tint,
        )
    } else {
        SvgGlyph(
            svg = svg,
            tint = tint,
            contentDescription = contentDescription,
            modifier = modifier.size(INLINE_GLYPH_SIZE),
        )
    }
}

/**
 * Rasterizes [svg] into a `Picture` sized to fill this composable, then recolors it with [tint]
 * via the same `BlendMode.SrcIn` mechanism `Icon(tint = ...)` uses internally — a manual
 * `saveLayer`/[Paint] is needed here (instead of just calling `Icon`) because a `Picture` isn't an
 * [androidx.compose.ui.graphics.vector.ImageVector]. This recolors every opaque pixel regardless
 * of the SVG's own internal fill color, so the source document's actual `fill` value never
 * matters.
 */
@Composable
private fun SvgGlyph(
    svg: SVG,
    tint: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val tintPaint = remember(tint) { Paint().apply { colorFilter = ColorFilter.tint(tint, BlendMode.SrcIn) } }
    // Rendered once at the SVG's own native size (its width/height or viewBox), then stretched to
    // fit via drawPicture(picture, dst) below — Canvas.drawPicture(Picture)'s no-bounds overload
    // draws at native size with no scaling, so a bare renderToPicture(widthPx, heightPx) call
    // alone isn't a reliable substitute for an explicit stretch-to-fit draw.
    val picture = remember(svg) { svg.renderToPicture() }
    Canvas(
        modifier = modifier.let {
            if (contentDescription != null) it.semantics { this.contentDescription = contentDescription } else it
        },
    ) {
        val dst = android.graphics.RectF(0f, 0f, size.width, size.height)
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), tintPaint)
            canvas.nativeCanvas.drawPicture(picture, dst)
            canvas.restore()
        }
    }
}

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
