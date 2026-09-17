package com.rossomak.flashcards.core.ui.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.List as AutoMirroredList
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle.OnGradient
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle.OnSurface
import com.rossomak.flashcards.core.ui.theme.AppSizes
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.brandColors
import com.rossomak.flashcards.core.ui.theme.cornerRadius
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.spacing

/** Geometry for [FlashcardsMetadataBadge] at a given [FlashcardsComponentSize]. */
private data class FlashcardsMetadataBadgeMetrics(
    val height: Dp,
    val iconSize: Dp,
    val textStyle: TextStyle,
)

@Composable
@ReadOnlyComposable
private fun FlashcardsComponentSize.metadataBadgeMetrics(
    sizes: AppSizes = MaterialTheme.sizes,
    typography: Typography = MaterialTheme.typography,
): FlashcardsMetadataBadgeMetrics = when (this) {
    FlashcardsComponentSize.Normal -> FlashcardsMetadataBadgeMetrics(
        height = sizes.metadataBadgeHeight,
        iconSize = sizes.metadataBadgeIcon,
        textStyle = typography.labelMedium,
    )
    FlashcardsComponentSize.Small -> FlashcardsMetadataBadgeMetrics(
        height = sizes.metadataBadgeHeightCompact,
        iconSize = sizes.metadataBadgeIconCompact,
        textStyle = typography.labelSmall,
    )
}

/**
 * The metadata badge — the smaller, read-only sibling of [FlashcardsTagChip]: a 32dp pill with a
 * fully rounded corner, an optional leading icon, and a compact label. Used for at-a-glance stats
 * ("~ 12 min", "3 topics", "42 cards") and for the flat study tags shown on card rows.
 *
 * Pass [onClick] only when the badge doubles as an affordance (e.g. a sort badge that opens a
 * dialog); it then reports itself to accessibility as a button.
 *
 * Pass `size = FlashcardsComponentSize.Small` where badges sit inside another interactive row
 * (e.g. flat study tags on a flashcard row) and must recede behind the row's own content — same
 * `Normal`/`Small` axis every other `Flashcards*` component family uses (ADR-0034).
 */
@Composable
fun FlashcardsMetadataBadge(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: FlashcardsComponentStyle = OnSurface,
    size: FlashcardsComponentSize = FlashcardsComponentSize.Normal,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(MaterialTheme.cornerRadius.full)
    val metrics = size.metadataBadgeMetrics()
    val brandColors = MaterialTheme.brandColors
    val onGradient = brandColors.onGradientContent
    val containerColor = when (style) {
        OnSurface -> MaterialTheme.colorScheme.surfaceVariant
        OnGradient -> brandColors.onGradientContainer
    }
    val contentColor = when (style) {
        OnSurface -> MaterialTheme.colorScheme.onSurface
        OnGradient -> onGradient
    }
    val iconColor = when (style) {
        OnSurface -> MaterialTheme.colorScheme.onSurfaceVariant
        OnGradient -> onGradient
    }
    val border = when (style) {
        OnSurface -> null
        OnGradient -> BorderStroke(
            width = MaterialTheme.sizes.hairline,
            color = brandColors.onGradientBorder,
        )
    }

    Surface(
        modifier = modifier
            .height(metrics.height)
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = border,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxsmall),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(metrics.iconSize),
                )
            }
            Text(
                text = label,
                style = metrics.textStyle,
            )
        }
    }
}

@Composable
private fun MetadataBadgeSurfaceSampleRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(MaterialTheme.spacing.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
    ) {
        FlashcardsMetadataBadge(label = "~ 12 min", icon = Icons.Default.DateRange)
        FlashcardsMetadataBadge(label = "3 topics", icon = Icons.AutoMirrored.Filled.AutoMirroredList)
        FlashcardsMetadataBadge(label = "42 cards", icon = Icons.Default.Star)
    }
}

@ShowkaseComposable(name = "Metadata badge — on surface", group = "Chips & Badges")
@Composable
fun FlashcardsMetadataBadgeSurfaceShowcase() {
    FlashcardsTheme {
        Surface {
            MetadataBadgeSurfaceSampleRow()
        }
    }
}

@ShowkaseComposable(name = "Metadata badge — on gradient", group = "Chips & Badges")
@Composable
fun FlashcardsMetadataBadgeGradientShowcase() {
    FlashcardsTheme {
        Surface {
            MetadataBadgeGradientSampleRow()
        }
    }
}

@ShowkaseComposable(name = "Metadata badge — label only", group = "Chips & Badges")
@Composable
fun FlashcardsMetadataBadgeLabelOnlyShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsMetadataBadge(
                label = "State",
                modifier = Modifier.padding(MaterialTheme.spacing.small),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsMetadataBadgePreview() {
    FlashcardsTheme {
        Surface {
            MetadataBadgeSampleColumn()
        }
    }
}

@Composable
private fun MetadataBadgeSampleColumn(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
    ) {
        MetadataBadgeSurfaceSampleRow()
        Row(
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
        ) {
            FlashcardsMetadataBadge(label = "State")
            FlashcardsMetadataBadge(label = "Coroutines")
            FlashcardsMetadataBadge(label = "private")
        }
        MetadataBadgeGradientSampleRow()
    }
}

@Composable
private fun MetadataBadgeGradientSampleRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(MaterialTheme.brandColors.topBarGradient)
            .padding(MaterialTheme.spacing.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
    ) {
        FlashcardsMetadataBadge(
            label = "~ 12 min",
            icon = Icons.Default.DateRange,
            style = OnGradient,
        )
        FlashcardsMetadataBadge(
            label = "3 topics",
            icon = Icons.AutoMirrored.Filled.List,
            style = OnGradient,
        )
        FlashcardsMetadataBadge(
            label = "42 cards",
            icon = Icons.Default.Star,
            style = OnGradient,
        )
    }
}
