package com.rossomak.flashcards.core.ui.composables.buttons

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize.Normal
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize.Small
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle.OnGradient
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle.OnSurface
import com.rossomak.flashcards.core.ui.theme.AppSizes
import com.rossomak.flashcards.core.ui.theme.AppSpacing
import com.rossomak.flashcards.core.ui.theme.brandColors
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.spacing

/**
 * The geometry that varies by [FlashcardsComponentSize], resolved once per composition. Icon size
 * isn't here — both tiers share the same M3 [ButtonDefaults.IconSize] (18dp), so it's referenced
 * directly at the call site in [FlashcardsButtonIcon] instead of being duplicated per tier.
 */
internal data class FlashcardsButtonMetrics(
    val height: Dp,
    val horizontalPadding: Dp,
    val textStyle: TextStyle,
)

@Composable
@ReadOnlyComposable
internal fun FlashcardsComponentSize.metrics(
    sizes: AppSizes = MaterialTheme.sizes,
    spacing: AppSpacing = MaterialTheme.spacing,
    typography: Typography = MaterialTheme.typography,
): FlashcardsButtonMetrics = when (this) {
    Normal -> FlashcardsButtonMetrics(
        height = sizes.buttonHeightNormal,
        horizontalPadding = spacing.medium,
        textStyle = typography.labelLarge,
    )
    Small -> FlashcardsButtonMetrics(
        height = sizes.buttonHeightSmall,
        horizontalPadding = spacing.normal,
        textStyle = typography.labelMedium,
    )
}

/** Container fill alpha for the disabled state on [FlashcardsComponentStyle.OnSurface] (M3 convention). */
private const val DISABLED_CONTAINER_ALPHA = 0.12f

/** Content (text/icon) alpha for the disabled state on [FlashcardsComponentStyle.OnSurface] (M3 convention). */
private const val DISABLED_CONTENT_ALPHA = 0.38f

/**
 * Container fill alpha for the disabled state on [FlashcardsComponentStyle.OnGradient]. Same
 * intent as [DISABLED_CONTAINER_ALPHA], based on white instead of `onSurface` since that style
 * sits on the theme-fixed [com.rossomak.flashcards.core.ui.theme.BrandColors.topBarGradient].
 */
private const val ON_GRADIENT_DISABLED_CONTAINER_ALPHA = 0.20f

/** Content alpha for the disabled state on [FlashcardsComponentStyle.OnGradient]. */
private const val ON_GRADIENT_DISABLED_CONTENT_ALPHA = 0.50f

/** Disabled container color for the given [style], shared by every `Flashcards*Button`. */
@Composable
@ReadOnlyComposable
internal fun disabledButtonContainerColorFor(style: FlashcardsComponentStyle): Color = when (style) {
    OnSurface -> MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_CONTAINER_ALPHA)
    OnGradient -> Color.White.copy(alpha = ON_GRADIENT_DISABLED_CONTAINER_ALPHA)
}

/** Disabled content color for the given [style], shared by every `Flashcards*Button`. */
@Composable
@ReadOnlyComposable
internal fun disabledButtonContentColorFor(style: FlashcardsComponentStyle): Color = when (style) {
    OnSurface -> MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_CONTENT_ALPHA)
    OnGradient -> Color.White.copy(alpha = ON_GRADIENT_DISABLED_CONTENT_ALPHA)
}

/**
 * Container fill alpha for [com.rossomak.flashcards.core.ui.theme.BrandColors.ctaButtonGradient]
 * when disabled, shared by every gradient-filled `Flashcards*Button`
 * ([FlashcardsFilledButton], [FlashcardsFilledIconButton]). M3's `disabledContainerColor` only
 * takes a flat [Color], so the gradient itself keeps painting (via a background modifier behind a
 * transparent container) and is dimmed with this alpha instead of being swapped out for a solid fill.
 */
internal const val DISABLED_GRADIENT_ALPHA = 0.38f

/**
 * Resolved colors for a gradient-filled `Flashcards*Button` ([FlashcardsFilledButton],
 * [FlashcardsFilledIconButton]), shared so both stay in sync. Fixed white content on [OnSurface]
 * rather than `colorScheme.onPrimary` — that style's container is the `ctaButtonGradient` brush,
 * itself fixed across themes, so its content color must be fixed too.
 */
internal data class FilledButtonColors(
    val containerColor: Color,
    val contentColor: Color,
    val disabledContainerColor: Color,
    val disabledContentColor: Color,
)

/** Resolves [FilledButtonColors] for the given [style], shared by every gradient-filled `Flashcards*Button`. */
@Composable
@ReadOnlyComposable
internal fun filledButtonColorsFor(style: FlashcardsComponentStyle): FilledButtonColors {
    val onGradient = style == OnGradient
    return FilledButtonColors(
        containerColor = if (onGradient) Color.White else Color.Transparent,
        contentColor = if (onGradient) MaterialTheme.brandColors.onGradientFilled else Color.White,
        disabledContainerColor = if (onGradient) disabledButtonContainerColorFor(style) else Color.Transparent,
        disabledContentColor = disabledButtonContentColorFor(style),
    )
}

/**
 * Enabled container color for the given [style], shared by every tonal-treatment
 * `Flashcards*Button` ([FlashcardsTonalButton], [FlashcardsTonalIconButton]). [onSurfaceContainer]
 * is the one part that varies per type — each picks its own `OnSurface` token — while the
 * `OnGradient` branch is always [com.rossomak.flashcards.core.ui.theme.BrandColors.onGradientContainer].
 */
@Composable
@ReadOnlyComposable
internal fun enabledButtonContainerColorFor(style: FlashcardsComponentStyle, onSurfaceContainer: Color): Color = when (style) {
    OnSurface -> onSurfaceContainer
    OnGradient -> MaterialTheme.brandColors.onGradientContainer
}

/**
 * Enabled content color for the given [style], the content-color counterpart to
 * [enabledButtonContainerColorFor].
 */
@Composable
@ReadOnlyComposable
internal fun enabledButtonContentColorFor(style: FlashcardsComponentStyle, onSurfaceContent: Color): Color = when (style) {
    OnSurface -> onSurfaceContent
    OnGradient -> MaterialTheme.brandColors.onGradientContent
}

/**
 * Shared label/icon content for every `Flashcards*Button`, rendered as the `content` lambda of
 * the underlying M3 [androidx.compose.material3.Button]/[androidx.compose.material3.FilledTonalButton]/
 * [androidx.compose.material3.OutlinedButton]/[androidx.compose.material3.TextButton]. Their own
 * internal `Row` already provides icon/label spacing and vertical centering, so this only emits
 * children — it doesn't wrap another `Row`. The icon carries extra padding on its label-facing
 * side, on top of that built-in spacing, because [ButtonDefaults.IconSpacing] alone reads too
 * dense against the icon.
 */
@Composable
internal fun RowScope.FlashcardsButtonContent(
    text: String,
    icon: ImageVector?,
    iconPosition: FlashcardsButtonIconPosition,
    metrics: FlashcardsButtonMetrics,
) {
    val extraIconGap = MaterialTheme.spacing.xxsmall
    if (icon != null && iconPosition == FlashcardsButtonIconPosition.Leading) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.padding(end = extraIconGap).size(ButtonDefaults.IconSize),
        )
    }
    Text(text = text, style = metrics.textStyle)
    if (icon != null && iconPosition == FlashcardsButtonIconPosition.Trailing) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.padding(start = extraIconGap).size(ButtonDefaults.IconSize),
        )
    }
}
