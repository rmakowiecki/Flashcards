package com.rossomak.flashcards.core.ui.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.composables.FlashcardsEmptyStateTone.Error
import com.rossomak.flashcards.core.ui.composables.FlashcardsEmptyStateTone.Info
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledButton
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle.OnGradient
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle.OnSurface
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.brandColors
import com.rossomak.flashcards.core.ui.theme.semanticColors
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.spacing

/** Container fill alpha for [FlashcardsEmptyStateTone.Error]'s [OnGradient] circle. */
private const val ON_GRADIENT_ERROR_CONTAINER_ALPHA = 0.33f

/** Border alpha for [FlashcardsEmptyStateTone.Error]'s [OnGradient] circle. */
private const val ON_GRADIENT_ERROR_BORDER_ALPHA = 0.5f

/**
 * The shared layout for "nothing to show" and "something went wrong" screens: a tinted, color-coded
 * icon circle, a title, up to two lines of supporting copy, and one CTA slot. Used for empty
 * search results, empty lists, cleared filters, and load errors alike — [tone] is the only thing
 * that shifts between "empty" and "error".
 *
 * The title only picks up [tone]'s color for [FlashcardsEmptyStateTone.Error] (red, matching the
 * icon); for [FlashcardsEmptyStateTone.Info] it stays the plain on-surface text color rather than
 * the icon circle's own tint, which reads as an unrelated accent color on body-sized title text.
 *
 * Does not size or center itself — pass a `modifier` that does (`Modifier.fillMaxSize()` plus
 * whatever alignment the call site needs), same as every other `core:ui` composable that doesn't
 * own its own layout slot.
 *
 * [button] is a slot, not label/icon/click params: this component only positions a CTA, it does not
 * construct one. Pass a lambda that calls a button composable directly (typically
 * [FlashcardsFilledButton], with `style = style` so it matches the rest of the empty state), or
 * `null` to omit it.
 *
 * [style] follows the shared on-surface/on-gradient axis every `Flashcards*` component family uses
 * (ADR-0034). [OnGradient]'s [FlashcardsEmptyStateTone.Error] circle mirrors
 * [FlashcardsXpBreakdownRow][com.rossomak.flashcards.core.ui.composables.banners.FlashcardsXpBreakdownRow]'s
 * own `Loss` tone: `semanticColors.onGradientLossContainer` at a low alpha for the fill and a higher
 * one for the border, so the gradient shows through rather than sitting under a flat, opaque circle.
 * The icon and title use `brandColors.onGradientLoss` rather than [OnSurface]'s own
 * `onNegativeContainer` red: that token is tuned for a themed surface and falls short of the
 * body-text contrast floor against the gradient, whereas `onGradientLoss` is pinned for exactly this
 * background (see `BrandColors.kt`). The supporting text stays the same white [OnGradient] always
 * uses regardless of [tone]: a muted, low-contrast tone reads as illegible on the dark brand
 * gradient.
 */
private data class EmptyStateColors(
    val container: Color,
    val icon: Color,
    val title: Color,
    val supportingText: Color,
    val border: BorderStroke?,
)

/**
 * [EmptyStateColors.icon] tints both the icon glyph and its circle's own container — that pairing
 * is what a tinted icon circle *is*. [EmptyStateColors.title] is deliberately its own field rather
 * than reusing [EmptyStateColors.icon]: on [FlashcardsEmptyStateTone.Info] the icon tint reads as a
 * stray accent color on body-sized title text, so the title falls back to the plain on-surface (or,
 * [OnGradient], on-gradient-content) color instead; only [FlashcardsEmptyStateTone.Error] needs
 * (and gets) the same color both places. [EmptyStateColors.supportingText] is its own field too,
 * rather than inheriting whatever [EmptyStateColors.title] resolves to: it never carries [tone]'s
 * color, on either [style] — muted on [OnSurface], plain white on [OnGradient] — same as
 * [EmptyStateColors.title] would if [tone] were always [FlashcardsEmptyStateTone.Info].
 */
@Composable
private fun emptyStateColors(
    tone: FlashcardsEmptyStateTone,
    style: FlashcardsComponentStyle,
): EmptyStateColors {
    val semanticColors = MaterialTheme.semanticColors
    val brandColors = MaterialTheme.brandColors
    return when (style) {
        OnSurface -> when (tone) {
            Info -> EmptyStateColors(
                container = MaterialTheme.colorScheme.secondaryContainer,
                icon = MaterialTheme.colorScheme.onSecondaryContainer,
                title = MaterialTheme.colorScheme.onSurface,
                supportingText = MaterialTheme.colorScheme.onSurfaceVariant,
                border = null,
            )
            Error -> EmptyStateColors(
                container = semanticColors.negativeContainer,
                icon = semanticColors.onNegativeContainer,
                title = semanticColors.onNegativeContainer,
                supportingText = MaterialTheme.colorScheme.onSurfaceVariant,
                border = null,
            )
        }
        OnGradient -> when (tone) {
            Info -> EmptyStateColors(
                container = brandColors.onGradientContainer,
                icon = brandColors.onGradientContent,
                title = brandColors.onGradientContent,
                supportingText = brandColors.onGradientContent,
                border = BorderStroke(width = MaterialTheme.sizes.hairline, color = brandColors.onGradientBorder),
            )
            Error -> EmptyStateColors(
                container = semanticColors.onGradientLossContainer.copy(alpha = ON_GRADIENT_ERROR_CONTAINER_ALPHA),
                icon = brandColors.onGradientLoss,
                title = brandColors.onGradientLoss,
                supportingText = brandColors.onGradientContent,
                border = BorderStroke(
                    width = MaterialTheme.sizes.hairline,
                    color = semanticColors.onGradientLossContainer.copy(alpha = ON_GRADIENT_ERROR_BORDER_ALPHA),
                ),
            )
        }
    }
}

@Composable
fun FlashcardsEmptyState(
    icon: ImageVector,
    title: String,
    supportingText: String,
    modifier: Modifier = Modifier,
    tone: FlashcardsEmptyStateTone = Info,
    style: FlashcardsComponentStyle = OnSurface,
    button: (@Composable () -> Unit)? = null,
) {
    val colors = emptyStateColors(tone, style)

    Column(
        modifier = modifier.padding(horizontal = MaterialTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(MaterialTheme.sizes.emptyStateIconCircle),
            shape = CircleShape,
            color = colors.container,
            contentColor = colors.icon,
            border = colors.border,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    // Decorative: the title below already names the state for a screen reader.
                    contentDescription = null,
                    modifier = Modifier.size(MaterialTheme.sizes.emptyStateIcon),
                )
            }
        }

        Surface(color = Color.Transparent, contentColor = colors.title) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = MaterialTheme.spacing.normal),
                )

                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.supportingText,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = MaterialTheme.spacing.xsmall),
                )

                if (button != null) {
                    Box(modifier = Modifier.padding(top = MaterialTheme.spacing.normal)) {
                        button()
                    }
                }
            }
        }
    }
}

@ShowkaseComposable(name = "Empty state — Info", group = "Empty states")
@PreviewLightDark
@Composable
private fun FlashcardsEmptyStateInfoShowcase() {
    FlashcardsTheme {
        Surface {
            Box(modifier = Modifier.padding(MaterialTheme.spacing.normal)) {
                FlashcardsEmptyState(
                    icon = Icons.Filled.SearchOff,
                    title = "No matches",
                    supportingText = "Nothing matches \"xyz123\". Try a different search.",
                )
            }
        }
    }
}

@ShowkaseComposable(name = "Empty state — with CTA", group = "Empty states")
@PreviewLightDark
@Composable
private fun FlashcardsEmptyStateWithCtaShowcase() {
    FlashcardsTheme {
        Surface {
            Box(modifier = Modifier.padding(MaterialTheme.spacing.normal)) {
                FlashcardsEmptyState(
                    icon = Icons.Filled.School,
                    title = "Ready to start?",
                    supportingText = "Run your first session — recents and favorites will appear here.",
                    button = {
                        FlashcardsFilledButton(text = "Start", onClick = {}, icon = Icons.Filled.School)
                    },
                )
            }
        }
    }
}

@ShowkaseComposable(name = "Empty state — Error", group = "Empty states")
@PreviewLightDark
@Composable
private fun FlashcardsEmptyStateErrorShowcase() {
    FlashcardsTheme {
        Surface {
            Box(modifier = Modifier.padding(MaterialTheme.spacing.normal)) {
                FlashcardsEmptyState(
                    icon = Icons.Filled.CloudOff,
                    title = "Something went wrong",
                    supportingText = "Couldn't load your categories. Check your connection and try again.",
                    tone = Error,
                    button = {
                        FlashcardsFilledButton(text = "Retry", onClick = {})
                    },
                )
            }
        }
    }
}

@ShowkaseComposable(name = "Empty state — on gradient, Info", group = "Empty states")
@Preview
@Composable
private fun FlashcardsEmptyStateOnGradientInfoShowcase() {
    FlashcardsTheme {
        Surface(color = MaterialTheme.brandColors.screenGradientBase) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.brandColors.screenGradient)
                    .padding(MaterialTheme.spacing.normal),
            ) {
                FlashcardsEmptyState(
                    icon = Icons.Filled.SearchOff,
                    title = "No matches",
                    supportingText = "Nothing matches the current filters.",
                    style = OnGradient,
                    button = {
                        FlashcardsFilledButton(text = "Reset filters", onClick = {}, style = OnGradient)
                    },
                )
            }
        }
    }
}

@ShowkaseComposable(name = "Empty state — on gradient, Error", group = "Empty states")
@Preview
@Composable
private fun FlashcardsEmptyStateOnGradientErrorShowcase() {
    FlashcardsTheme {
        Surface(color = MaterialTheme.brandColors.screenGradientBase) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.brandColors.screenGradient)
                    .padding(MaterialTheme.spacing.normal),
            ) {
                FlashcardsEmptyState(
                    icon = Icons.Filled.CloudOff,
                    title = "Something went wrong",
                    supportingText = "Couldn't load your categories. Check your connection and try again.",
                    tone = Error,
                    style = OnGradient,
                    button = {
                        FlashcardsFilledButton(text = "Retry", onClick = {}, style = OnGradient)
                    },
                )
            }
        }
    }
}
