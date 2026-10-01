package com.rossomak.flashcards.core.ui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.cornerRadius
import com.rossomak.flashcards.core.ui.theme.sizes

/**
 * Opacity of [MaterialTheme.colorScheme.secondaryContainer] used as the tile's default fill.
 * Public (not private) so [FlashcardsVectorIconTile] and feature-owned tiles can derive their
 * container tint from the same constant rather than redefining it.
 */
const val DEFAULT_CONTAINER_ALPHA = 0.12f

/**
 * Rounded, tinted square that hosts a leading icon in settings and category rows. Callers
 * supply the glyph (typically a feature-owned [ImageVector]) so `:core:ui` stays free of the
 * heavy icon set; the tile owns only the container shape, size, and tint.
 *
 * Defaults to a translucent accent tint (settings rows); category rows pass their own
 * category-coded [containerColor]/[contentColor].
 */
@Composable
fun FlashcardsIconTile(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    containerColor: Color = contentColor.copy(alpha = DEFAULT_CONTAINER_ALPHA),
) {
    Box(
        modifier = modifier
            .size(MaterialTheme.sizes.iconTile)
            .background(
                color = containerColor,
                shape = RoundedCornerShape(MaterialTheme.cornerRadius.small),
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
    }
}

@ShowkaseComposable(name = "Icon tile", group = "Lists")
@Composable
fun FlashcardsIconTileShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsIconTile(icon = Icons.Default.Menu, contentDescription = null)
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsIconTilePreview() {
    FlashcardsTheme {
        Surface {
            FlashcardsIconTile(icon = Icons.Default.Menu, contentDescription = null)
        }
    }
}
