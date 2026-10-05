package com.rossomak.flashcards.core.ui.composables.level

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.R
import com.rossomak.flashcards.core.ui.composables.FlashcardsAvatar
import com.rossomak.flashcards.core.ui.composables.FlashcardsAvatarSize
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle.OnGradient
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle.OnSurface
import com.rossomak.flashcards.core.ui.composables.progress.FlashcardsLinearProgressBar
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.brandColors
import com.rossomak.flashcards.core.ui.theme.cornerRadius
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.spacing

/**
 * Alpha applied to the "LEVEL" label and the xp-count text — the two pieces the design mock
 * renders at reduced emphasis next to the full-brightness level number and "XP" suffix. One
 * shared constant for both [FlashcardsComponentStyle] values, so the two styles stay visually
 * consistent with each other rather than each being tuned separately to its own mock.
 */
private const val LEVEL_CARD_MUTED_TEXT_ALPHA = 0.7f

/**
 * The account-wide "current level" card: the signed-in User's [FlashcardsAvatar] beside their level
 * number and XP progress. There is no rank/tier pill (BRONZE/SILVER/GOLD) at all, not even a stub —
 * it is explicitly out of scope for now.
 *
 * Supports both [FlashcardsComponentStyle] values, following the same container/border shape as
 * [com.rossomak.flashcards.core.ui.composables.banners.FlashcardsInfoBanner]: [OnSurface] is a flat
 * tonal card ([com.rossomak.flashcards.core.ui.theme.BrandColors.tonalButtonContainer], no border),
 * [OnGradient] is the translucent-white-on-brand-gradient treatment
 * ([com.rossomak.flashcards.core.ui.theme.BrandColors.onGradientContainer] plus a hairline border)
 * matching [FlashcardsLinearProgressBar]'s own `OnGradient` style.
 *
 * The avatar follows the card's [style] and is decorative next to the level readout. In [OnSurface]
 * its tonal container is the card's own, so the circle blends into the card and only the initials
 * or icon show.
 *
 * The mock's frosted-glass `OnGradient` surface (`rgba(255,255,255,0.11)` + backdrop blur) is
 * approximated with a flat translucent container color only — `Modifier.blur` needs API 31+ and
 * silently no-ops below it (this project's `minSdk = 24`). Real blur-as-a-design-token is tracked
 * separately.
 *
 * [progress] is not computed here — [xpIntoCurrentLevel]/[xpForNextLevel] are passed through only
 * for the readout text; the caller drives the fill via [progress] so it can animate the bar
 * (0 → final) independently of when the readout text itself should update. [photoUrl] and
 * [displayName] are handed to [FlashcardsAvatar] as is.
 */
@Composable
fun FlashcardsLevelCard(
    level: Int,
    xpIntoCurrentLevel: Long,
    xpForNextLevel: Long,
    progress: Float,
    photoUrl: String?,
    displayName: String?,
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
            FlashcardsAvatar(
                photoUrl = photoUrl,
                displayName = displayName,
                size = FlashcardsAvatarSize.Medium,
                contentDescription = null,
                style = style,
            )
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
                photoUrl = null,
                displayName = "Jane Doe",
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
                photoUrl = null,
                displayName = "Jane Doe",
                style = OnGradient,
            )
        }
    }
}

@ShowkaseComposable(name = "Level card — on gradient, no name", group = "Level")
@Preview
@Composable
fun FlashcardsLevelCardOnGradientIconShowcase() {
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
                photoUrl = null,
                displayName = null,
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
                    photoUrl = null,
                    displayName = "Jane Doe",
                )
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.brandColors.screenGradient)
                        .padding(MaterialTheme.spacing.normal),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                        FlashcardsLevelCard(
                            level = 12,
                            xpIntoCurrentLevel = 2_840L,
                            xpForNextLevel = 5_000L,
                            progress = 2_840f / 5_000f,
                            photoUrl = null,
                            displayName = "Jane Doe",
                            style = OnGradient,
                        )
                        FlashcardsLevelCard(
                            level = 12,
                            xpIntoCurrentLevel = 2_840L,
                            xpForNextLevel = 5_000L,
                            progress = 2_840f / 5_000f,
                            photoUrl = null,
                            displayName = null,
                            style = OnGradient,
                        )
                    }
                }
            }
        }
    }
}
