package com.rossomak.flashcards.core.ui.composables.level

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.R
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle.OnGradient
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle.OnSurface
import com.rossomak.flashcards.core.ui.composables.progress.FlashcardsLinearProgressBar
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.brandColors
import com.rossomak.flashcards.core.ui.theme.cornerRadius
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.spacing

/** Diagonal stripe thickness on [StripedAvatarPlaceholder], and the gap between stripes (same value). */
private val AVATAR_STRIPE_WIDTH = 6.dp

/** [StripedAvatarPlaceholder]'s base fill, under the diagonal stripes. */
private const val AVATAR_BASE_ALPHA = 0.18f

/** [StripedAvatarPlaceholder]'s stripe color alpha, over the base fill. */
private const val AVATAR_STRIPE_ALPHA = 0.4f

/**
 * Alpha applied to the "LEVEL" label and the xp-count text — the two pieces the design mock
 * renders at reduced emphasis next to the full-brightness level number and "XP" suffix. One
 * shared constant for both [FlashcardsComponentStyle] values, so the two styles stay visually
 * consistent with each other rather than each being tuned separately to its own mock.
 */
private const val LEVEL_CARD_MUTED_TEXT_ALPHA = 0.7f

/**
 * The account-wide "current level" card — a placeholder for a future real identity card (real
 * avatar, rank/tier pill), built to unblock the Session Summary screen's payoff moment without
 * designing that real card twice.
 *
 * Supports both [FlashcardsComponentStyle] values, following the same container/border shape as
 * [com.rossomak.flashcards.core.ui.composables.banners.FlashcardsInfoBanner]: [OnSurface] is a flat
 * tonal card ([com.rossomak.flashcards.core.ui.theme.BrandColors.tonalButtonContainer], no border),
 * [OnGradient] is the translucent-white-on-brand-gradient treatment
 * ([com.rossomak.flashcards.core.ui.theme.BrandColors.onGradientContainer] plus a hairline border)
 * matching [FlashcardsLinearProgressBar]'s own `OnGradient` style.
 *
 * No real avatar/photo loading (see [StripedAvatarPlaceholder]) and no rank/tier pill
 * (BRONZE/SILVER/GOLD) at all, not even a stub — both are explicitly out of scope for this
 * placeholder.
 *
 * The mock's frosted-glass `OnGradient` surface (`rgba(255,255,255,0.11)` + backdrop blur) is
 * approximated with a flat translucent container color only — `Modifier.blur` needs API 31+ and
 * silently no-ops below it (this project's `minSdk = 24`). Real blur-as-a-design-token is tracked
 * separately.
 *
 * [progress] is not computed here — [xpIntoCurrentLevel]/[xpForNextLevel] are passed through only
 * for the readout text; the caller drives the fill via [progress] so it can animate the bar
 * (0 → final) independently of when the readout text itself should update.
 */
@Composable
fun FlashcardsLevelCard(
    level: Int,
    xpIntoCurrentLevel: Long,
    xpForNextLevel: Long,
    progress: Float,
    modifier: Modifier = Modifier,
    style: FlashcardsComponentStyle = OnSurface,
) {
    val brandColors = MaterialTheme.brandColors
    val onGradient = style == OnGradient
    val containerColor = if (onGradient) brandColors.onGradientContainer else brandColors.tonalButtonContainer
    val contentColor = if (onGradient) brandColors.onGradientContent else brandColors.onTonalButtonContainer
    val mutedContentColor = contentColor.copy(alpha = LEVEL_CARD_MUTED_TEXT_ALPHA)
    val border = if (onGradient) {
        BorderStroke(width = MaterialTheme.sizes.onGradientBorder, color = brandColors.onGradientBorder)
    } else {
        null
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MaterialTheme.cornerRadius.large),
        color = containerColor,
        contentColor = contentColor,
        border = border,
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.normal),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.normal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StripedAvatarPlaceholder(modifier = Modifier.size(MaterialTheme.sizes.levelCardAvatar))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall, Alignment.End),
                ) {
                    // alignByBaseline, not the Row's own verticalAlignment: labelLarge and
                    // headlineLarge have different descent, so aligning by box-bottom (Alignment.Bottom)
                    // leaves their glyph baselines visibly offset from each other.
                    Text(
                        // Uppercased here, not in the string resource — an all-caps resource makes
                        // TalkBack spell it letter-by-letter instead of reading the word.
                        text = stringResource(R.string.level_card_level_label).uppercase(),
                        modifier = Modifier.alignByBaseline(),
                        color = mutedContentColor,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = level.toString(),
                        modifier = Modifier.alignByBaseline(),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                FlashcardsLinearProgressBar(progress = progress, style = style)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = stringResource(R.string.level_card_xp_count_label, xpIntoCurrentLevel, xpForNextLevel),
                        color = mutedContentColor,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = stringResource(R.string.level_card_xp_unit_label),
                        modifier = Modifier.padding(start = MaterialTheme.spacing.xxsmall),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * A plain circular placeholder with a diagonal-stripe fill, matching the design mock's "photo"
 * dev-placeholder look exactly — an honest stand-in for a real avatar/photo, not a throwaway visual
 * style invented for this ticket. Real avatar/photo loading is a separate, future ticket.
 *
 * Derives its fill from [LocalContentColor] rather than a hardcoded white so it adapts to either
 * [FlashcardsComponentStyle] the enclosing [FlashcardsLevelCard] renders with, without needing its
 * own style parameter or dedicated color tokens.
 */
@Composable
private fun StripedAvatarPlaceholder(modifier: Modifier = Modifier) {
    val baseColor = LocalContentColor.current
    val stripeColor = baseColor.copy(alpha = AVATAR_STRIPE_ALPHA)

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(baseColor.copy(alpha = AVATAR_BASE_ALPHA)),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stripeWidthPx = AVATAR_STRIPE_WIDTH.toPx()
            // Wide enough to cover the circle at any rotation: the longest chord through a square
            // canvas is its diagonal, doubled again so the rotated sweep below still overshoots
            // both ends once translated back to center.
            val sweep = (size.width.coerceAtLeast(size.height)) * 2f
            rotate(degrees = 45f) {
                var x = -sweep
                while (x < sweep) {
                    drawRect(
                        color = stripeColor,
                        topLeft = Offset(x, -sweep / 2f),
                        size = Size(stripeWidthPx, sweep),
                    )
                    x += stripeWidthPx * 2
                }
            }
        }
    }
}

@ShowkaseComposable(name = "Level card", group = "Level")
@Composable
fun FlashcardsLevelCardShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsLevelCard(
                level = 7,
                xpIntoCurrentLevel = 2_300L,
                xpForNextLevel = 6_000L,
                progress = 2_300f / 6_000f,
                modifier = Modifier.padding(MaterialTheme.spacing.normal),
            )
        }
    }
}

@ShowkaseComposable(name = "Level card — on gradient", group = "Level")
@Preview
@Composable
fun FlashcardsLevelCardOnGradientShowcase() {
    FlashcardsTheme {
        Box(
            modifier = Modifier
                .background(MaterialTheme.brandColors.screenGradient)
                .padding(MaterialTheme.spacing.normal),
        ) {
            FlashcardsLevelCard(
                level = 12,
                xpIntoCurrentLevel = 2_840L,
                xpForNextLevel = 5_000L,
                progress = 2_840f / 5_000f,
                style = OnGradient,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsLevelCardPreview() {
    FlashcardsTheme {
        Surface {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.normal),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                FlashcardsLevelCard(
                    level = 7,
                    xpIntoCurrentLevel = 2_300L,
                    xpForNextLevel = 6_000L,
                    progress = 2_300f / 6_000f,
                )
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.brandColors.screenGradient)
                        .padding(MaterialTheme.spacing.normal),
                ) {
                    FlashcardsLevelCard(
                        level = 12,
                        xpIntoCurrentLevel = 2_840L,
                        xpForNextLevel = 5_000L,
                        progress = 2_840f / 5_000f,
                        style = OnGradient,
                    )
                }
            }
        }
    }
}
