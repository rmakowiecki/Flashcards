package com.rossomak.flashcards.core.ui.composables.level

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
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
import com.valentinilk.shimmer.LocalShimmerTheme
import com.valentinilk.shimmer.defaultShimmerTheme
import com.valentinilk.shimmer.shimmer

/**
 * Alpha applied to the "LVL" label and the xp-count text — the two pieces the design mock renders
 * at reduced emphasis next to the full-brightness level number and "XP" suffix. One shared
 * constant for both [FlashcardsComponentStyle] values, so the two styles stay visually consistent
 * with each other rather than each being tuned separately to its own mock.
 */
private const val LEVEL_CARD_MUTED_TEXT_ALPHA = 0.7f

/** Alpha of the skeleton's placeholder blocks; the shimmer band modulates it on top. */
private const val LEVEL_CARD_SKELETON_BLOCK_ALPHA = 0.4f

/** Width of the shimmer band; narrower than the library default so it reads on a card-sized surface. */
private val LEVEL_CARD_SKELETON_SHIMMER_WIDTH = 240.dp

/** Widths of the placeholder blocks standing in for the name, the "LVL N" pair and the XP readout. */
private val LEVEL_CARD_SKELETON_NAME_WIDTH = 120.dp
private val LEVEL_CARD_SKELETON_LEVEL_WIDTH = 56.dp
private val LEVEL_CARD_SKELETON_XP_WIDTH = 80.dp

/**
 * The account-wide "current level" card: the signed-in User's [FlashcardsAvatar] beside their name,
 * a compact "LVL N" pair and XP progress. There is no rank/tier pill (BRONZE/SILVER/GOLD) at all,
 * not even a stub — it is explicitly out of scope for now.
 *
 * Only the container differs between the two [FlashcardsComponentStyle] values; the content is always
 * white and drawn in the `OnGradient` style, because both containers are brand gradient:
 * - [OnSurface]: the card is on a plain surface, so it paints its own
 *   [com.rossomak.flashcards.core.ui.theme.BrandColors.screenGradient], with no border.
 * - [OnGradient]: the card is on that same gradient already, so it is the translucent white
 *   [com.rossomak.flashcards.core.ui.theme.BrandColors.onGradientContainer] plus a hairline border
 *   that gives it an edge.
 *
 * The top row is `[name ……… LVL N]` on one shared baseline. Only the name gives way: it is one
 * line, ellipsized, while the "LVL N" pair keeps its size. The name is [displayName] trimmed
 * ([levelCardName]); a null or blank name leaves its slot empty, with no fallback label — the
 * avatar's person icon already signals a nameless User. The avatar receives the raw [displayName]
 * (initials, or the person icon for a null or blank one) and is decorative.
 *
 * The whole card is one accessibility node described as "name, level, xp of xp" (without the name
 * when there is none), built from the final values passed in, so a progress animation never
 * changes the announcement.
 *
 * The mock's frosted-glass `OnGradient` surface (`rgba(255,255,255,0.11)` + backdrop blur) is
 * approximated with a flat translucent container color only — `Modifier.blur` needs API 31+ and
 * silently no-ops below it (this project's `minSdk = 26`). Real blur-as-a-design-token is tracked
 * separately.
 *
 * [progress] is not computed here — [xpIntoCurrentLevel]/[xpForNextLevel] are passed through only
 * for the readout text; the caller drives the fill via [progress] so it can animate the bar
 * (0 → final) independently of when the readout text itself should update. The parameters are
 * primitives on purpose: the card does not know the domain level model.
 *
 * Use [FlashcardsLevelCardSkeleton] while the figures are still loading.
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
    val mutedContentColor = MaterialTheme.brandColors.onGradientContent.copy(alpha = LEVEL_CARD_MUTED_TEXT_ALPHA)
    val name = remember(displayName) { levelCardName(displayName) }
    val description = when (name) {
        null -> stringResource(R.string.level_card_without_name_cd, level, xpIntoCurrentLevel, xpForNextLevel)
        else -> stringResource(R.string.level_card_cd, name, level, xpIntoCurrentLevel, xpForNextLevel)
    }

    LevelCardFrame(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        style = style,
        avatar = {
            FlashcardsAvatar(
                photoUrl = photoUrl,
                displayName = displayName,
                size = FlashcardsAvatarSize.Medium,
                contentDescription = null,
                style = OnGradient,
            )
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            // alignByBaseline, not the Row's own verticalAlignment: the name, the label and the
            // number have different descent, so aligning by box-bottom (Alignment.Bottom) leaves
            // their glyph baselines visibly offset from each other.
            if (name != null) {
                Text(
                    text = name,
                    modifier = Modifier.weight(1f).alignByBaseline(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.alignByBaseline(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
            ) {
                Text(
                    text = stringResource(R.string.level_card_level_label).uppercase(),
                    modifier = Modifier.alignByBaseline(),
                    color = mutedContentColor,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = level.toString(),
                    modifier = Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
        FlashcardsLinearProgressBar(progress = progress, style = OnGradient)
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

/**
 * Stand-in for [FlashcardsLevelCard] while the figures are still loading: the same container,
 * shape, paddings and avatar size, with a block where each piece of text sits (name, "LVL N", bar
 * track, XP readout) at that text's line height, so both have the same height. Both are built on
 * [LevelCardFrame], so the two cannot drift apart when the card changes.
 *
 * The blocks shimmer through the `compose-shimmer` library, with its theme provided only here.
 * The skeleton never shows data, so it has no accessibility semantics.
 */
@Composable
fun FlashcardsLevelCardSkeleton(
    modifier: Modifier = Modifier,
    style: FlashcardsComponentStyle = OnSurface,
) {
    val blockColor = MaterialTheme.brandColors.onGradientContent.copy(alpha = LEVEL_CARD_SKELETON_BLOCK_ALPHA)
    val blockShape = RoundedCornerShape(MaterialTheme.cornerRadius.small)
    val density = LocalDensity.current
    val typography = MaterialTheme.typography
    val topRowHeight = with(density) { typography.titleLarge.lineHeight.toDp() }
    val xpReadoutHeight = with(density) { typography.labelMedium.lineHeight.toDp() }
    val shimmerTheme = remember { defaultShimmerTheme.copy(shimmerWidth = LEVEL_CARD_SKELETON_SHIMMER_WIDTH) }

    CompositionLocalProvider(LocalShimmerTheme provides shimmerTheme) {
        LevelCardFrame(
            modifier = modifier.clearAndSetSemantics {},
            style = style,
            contentModifier = Modifier.shimmer(),
            avatar = {
                Box(
                    modifier = Modifier
                        .size(FlashcardsAvatarSize.Medium.diameter)
                        .background(blockColor, CircleShape),
                )
            },
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SkeletonBlock(Modifier.size(LEVEL_CARD_SKELETON_NAME_WIDTH, topRowHeight), blockColor, blockShape)
                SkeletonBlock(Modifier.size(LEVEL_CARD_SKELETON_LEVEL_WIDTH, topRowHeight), blockColor, blockShape)
            }
            SkeletonBlock(Modifier.fillMaxWidth().height(MaterialTheme.sizes.progressBarThicknessNormal), blockColor, CircleShape)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SkeletonBlock(Modifier.size(LEVEL_CARD_SKELETON_XP_WIDTH, xpReadoutHeight), blockColor, blockShape)
            }
        }
    }
}

@Composable
private fun SkeletonBlock(sizeModifier: Modifier, color: Color, shape: Shape) {
    Box(modifier = sizeModifier.background(color, shape))
}

/**
 * The container shared by [FlashcardsLevelCard] and [FlashcardsLevelCardSkeleton]: the style's
 * surface, the padded row with the [avatar] slot, and the weighted [content] column.
 * [contentModifier] goes on the padded row, before its paddings.
 */
@Composable
private fun LevelCardFrame(
    modifier: Modifier,
    style: FlashcardsComponentStyle,
    avatar: @Composable () -> Unit,
    contentModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val brandColors = MaterialTheme.brandColors
    val shape = RoundedCornerShape(MaterialTheme.cornerRadius.large)
    val containerModifier = when (style) {
        OnSurface -> Modifier.background(brandColors.screenGradient, shape)
        OnGradient -> Modifier
    }
    val containerColor = when (style) {
        OnSurface -> Color.Transparent
        OnGradient -> brandColors.onGradientContainer
    }
    val border = when (style) {
        OnSurface -> null
        OnGradient -> BorderStroke(width = MaterialTheme.sizes.onGradientBorder, color = brandColors.onGradientBorder)
    }

    Surface(
        modifier = modifier.fillMaxWidth().then(containerModifier),
        shape = shape,
        color = containerColor,
        contentColor = brandColors.onGradientContent,
        border = border,
    ) {
        Row(
            modifier = contentModifier.padding(MaterialTheme.spacing.normal),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.normal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            avatar()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
                content = content,
            )
        }
    }
}

/** The name the card shows: [displayName] trimmed, or null for a null or blank one. */
internal fun levelCardName(displayName: String?): String? = displayName?.trim()?.takeIf { it.isNotEmpty() }

@ShowkaseComposable(name = "Level card", group = "Level")
@Preview
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

@ShowkaseComposable(name = "Level card skeleton", group = "Level")
@Preview
@Composable
fun FlashcardsLevelCardSkeletonShowcase() {
    FlashcardsTheme {
        Surface {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.normal),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                FlashcardsLevelCardSkeleton()
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.brandColors.screenGradient)
                        .padding(MaterialTheme.spacing.normal),
                ) {
                    FlashcardsLevelCardSkeleton(style = OnGradient)
                }
            }
        }
    }
}

@Composable
private fun LevelCardPreviewMatrix(style: FlashcardsComponentStyle) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        PreviewCard(level = 1, xpIntoCurrentLevel = 0L, xpForNextLevel = 1_000L, displayName = "Jane Doe", style = style)
        PreviewCard(level = 7, xpIntoCurrentLevel = 2_300L, xpForNextLevel = 6_000L, displayName = "Jane Doe", style = style)
        PreviewCard(
            level = 7,
            xpIntoCurrentLevel = 2_300L,
            xpForNextLevel = 6_000L,
            displayName = "Bartholomew Maximilian Featherstonehaugh-Worthington III",
            style = style,
        )
        PreviewCard(level = 7, xpIntoCurrentLevel = 2_300L, xpForNextLevel = 6_000L, displayName = null, style = style)
        PreviewCard(level = 7, xpIntoCurrentLevel = 2_300L, xpForNextLevel = 6_000L, displayName = "   ", style = style)
        PreviewCard(level = 128, xpIntoCurrentLevel = 91_300L, xpForNextLevel = 96_000L, displayName = "Jane Doe", style = style)
        FlashcardsLevelCardSkeleton(style = style)
    }
}

@Composable
private fun PreviewCard(level: Int, xpIntoCurrentLevel: Long, xpForNextLevel: Long, displayName: String?, style: FlashcardsComponentStyle) {
    FlashcardsLevelCard(
        level = level,
        xpIntoCurrentLevel = xpIntoCurrentLevel,
        xpForNextLevel = xpForNextLevel,
        progress = xpIntoCurrentLevel.toFloat() / xpForNextLevel,
        photoUrl = null,
        displayName = displayName,
        style = style,
    )
}

@Composable
private fun LevelCardPreviewBothStyles() {
    FlashcardsTheme {
        Surface {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.normal),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                LevelCardPreviewMatrix(style = OnSurface)
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.brandColors.screenGradient)
                        .padding(MaterialTheme.spacing.normal),
                ) {
                    LevelCardPreviewMatrix(style = OnGradient)
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsLevelCardPreview() = LevelCardPreviewBothStyles()

@Preview(fontScale = 2f)
@Composable
private fun FlashcardsLevelCardLargeFontPreview() = LevelCardPreviewBothStyles()

@Preview(locale = "ar")
@Composable
private fun FlashcardsLevelCardRtlPreview() = LevelCardPreviewBothStyles()
