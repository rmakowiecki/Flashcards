package com.rossomak.flashcards.core.ui.composables.level

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
import com.rossomak.flashcards.core.ui.composables.level.FlashcardsLevelCardState.Content
import com.rossomak.flashcards.core.ui.composables.level.FlashcardsLevelCardState.Loading
import com.rossomak.flashcards.core.ui.composables.level.FlashcardsLevelCardState.Unavailable
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
 * line, ellipsized, while the "LVL N" pair keeps its size. The name is the display name trimmed
 * ([levelCardName]); a null or blank name leaves its slot empty, with no fallback label — the
 * avatar's person icon already signals a nameless User. The avatar receives the raw display name
 * (initials, or the person icon for a null or blank one) and is decorative.
 *
 * The figures are one accessibility node described as "name, level, xp of xp" (without the name
 * when there is none), built from the final values in [Content], so a progress animation never
 * changes the announcement.
 *
 * The mock's frosted-glass `OnGradient` surface (`rgba(255,255,255,0.11)` + backdrop blur) is
 * approximated with a flat translucent container color only — `Modifier.blur` needs API 31+ and
 * silently no-ops below it (this project's `minSdk = 26`). Real blur-as-a-design-token is tracked
 * separately.
 *
 * What the card draws follows [state]:
 * - [Loading]: a same-height skeleton whose blocks shimmer, standing where each piece of text sits.
 * - [Content]: the figures. The bar's fill is derived from the two XP values ([levelCardProgress]). The bar
 *   animates changes to its fill but not its first composition, so a screen does not replay a sweep from 0
 *   on every launch. The figures are keyed on the Level, so a Level change starts the bar at its new fill
 *   instead of sweeping backwards; the same snap softens the short false level-up a double-counted delivery
 *   can cause.
 * - [Unavailable]: the same skeleton blocks without the shimmer. It has no UI of its own yet.
 *
 * The skeleton and the figures have the same height, so the fade between them moves nothing. A change
 * inside [Content] (a progress or name update) is not a transition: it recomposes in place, as does a
 * change between [Loading] and [Unavailable].
 *
 * The card owns its animation: a caller supplies states and never directs frames. A screen that wants a
 * richer entrance, such as a pour from a before position to an after position, shows the before [Content]
 * and then the after one, and the card decides how that change plays. The card exposes no animation
 * parameter and has no state variant carrying one.
 */
@Composable
fun FlashcardsLevelCard(
    state: FlashcardsLevelCardState,
    modifier: Modifier = Modifier,
    style: FlashcardsComponentStyle = OnSurface,
) {
    AnimatedContent(
        targetState = state,
        modifier = modifier,
        contentKey = { it is Content },
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "FlashcardsLevelCard",
    ) { shownState ->
        when (shownState) {
            Loading -> LevelCardSkeleton(isShimmering = true, style = style)

            Unavailable -> LevelCardSkeleton(isShimmering = false, style = style)

            is Content -> key(shownState.level) { LevelCardFigures(content = shownState, style = style) }
        }
    }
}

@Composable
private fun LevelCardFigures(content: Content, style: FlashcardsComponentStyle) {
    val level = content.level
    val xpIntoCurrentLevel = content.xpIntoCurrentLevel
    val xpForNextLevel = content.xpForNextLevel
    val displayName = content.displayName
    val mutedContentColor = MaterialTheme.brandColors.onGradientContent.copy(alpha = LEVEL_CARD_MUTED_TEXT_ALPHA)
    val name = remember(displayName) { levelCardName(displayName) }
    val description = when (name) {
        null -> stringResource(R.string.level_card_without_name_cd, level, xpIntoCurrentLevel, xpForNextLevel)
        else -> stringResource(R.string.level_card_cd, name, level, xpIntoCurrentLevel, xpForNextLevel)
    }

    LevelCardFrame(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        style = style,
        avatar = {
            FlashcardsAvatar(
                photoUrl = content.photoUrl,
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
                    style = MaterialTheme.typography.titleMediumEmphasized,
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
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
            }
        }
        FlashcardsLinearProgressBar(progress = levelCardProgress(xpIntoCurrentLevel, xpForNextLevel), style = OnGradient)
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
                style = MaterialTheme.typography.labelMediumEmphasized,
            )
        }
    }
}

/**
 * Stand-in for the figures while they are loading or unavailable: the same container, shape,
 * paddings and avatar size, with a block where each piece of text sits (name, "LVL N", bar track,
 * XP readout) at that text's line height, so both have the same height. Both are built on
 * [LevelCardFrame], so the two cannot drift apart when the card changes.
 *
 * The blocks shimmer through the `compose-shimmer` library while [isShimmering], with its theme
 * provided only here. The skeleton never shows data, so it has no accessibility semantics.
 */
@Composable
private fun LevelCardSkeleton(isShimmering: Boolean, style: FlashcardsComponentStyle) {
    val blockColor = MaterialTheme.brandColors.onGradientContent.copy(alpha = LEVEL_CARD_SKELETON_BLOCK_ALPHA)
    val blockShape = RoundedCornerShape(MaterialTheme.cornerRadius.small)
    val density = LocalDensity.current
    val typography = MaterialTheme.typography
    val topRowHeight = with(density) { typography.titleLarge.lineHeight.toDp() }
    val xpReadoutHeight = with(density) { typography.labelMedium.lineHeight.toDp() }
    val shimmerTheme = remember { defaultShimmerTheme.copy(shimmerWidth = LEVEL_CARD_SKELETON_SHIMMER_WIDTH) }

    CompositionLocalProvider(LocalShimmerTheme provides shimmerTheme) {
        LevelCardFrame(
            modifier = Modifier.clearAndSetSemantics {},
            style = style,
            contentModifier = if (isShimmering) Modifier.shimmer() else Modifier,
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
 * The container shared by [LevelCardFigures] and [LevelCardSkeleton]: the style's
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

/** The bar's fill, from 0 to 1: 0 when [xpForNextLevel] is not positive, and never above 1. */
internal fun levelCardProgress(xpIntoCurrentLevel: Long, xpForNextLevel: Long): Float =
    if (xpForNextLevel <= 0L) 0f else (xpIntoCurrentLevel.toFloat() / xpForNextLevel.toFloat()).coerceIn(0f, 1f)

@ShowkaseComposable(name = "Level card", group = "Level")
@Preview
@Composable
fun FlashcardsLevelCardShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsLevelCard(
                state = Content(
                    level = 7,
                    xpIntoCurrentLevel = 2_300L,
                    xpForNextLevel = 6_000L,
                    photoUrl = null,
                    displayName = "Jane Doe",
                ),
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
                state = Content(
                    level = 12,
                    xpIntoCurrentLevel = 2_840L,
                    xpForNextLevel = 5_000L,
                    photoUrl = null,
                    displayName = "Jane Doe",
                ),
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
                FlashcardsLevelCard(state = Loading)
                FlashcardsLevelCard(state = Unavailable)
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.brandColors.screenGradient)
                        .padding(MaterialTheme.spacing.normal),
                ) {
                    FlashcardsLevelCard(state = Loading, style = OnGradient)
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
        FlashcardsLevelCard(state = Loading, style = style)
        FlashcardsLevelCard(state = Unavailable, style = style)
    }
}

@Composable
private fun PreviewCard(level: Int, xpIntoCurrentLevel: Long, xpForNextLevel: Long, displayName: String?, style: FlashcardsComponentStyle) {
    FlashcardsLevelCard(
        state = Content(
            level = level,
            xpIntoCurrentLevel = xpIntoCurrentLevel,
            xpForNextLevel = xpForNextLevel,
            photoUrl = null,
            displayName = displayName,
        ),
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
