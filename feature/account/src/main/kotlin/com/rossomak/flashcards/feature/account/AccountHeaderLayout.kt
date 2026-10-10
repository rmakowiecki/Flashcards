package com.rossomak.flashcards.feature.account

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.util.lerp
import com.rossomak.flashcards.core.ui.theme.AppSpacing
import kotlin.math.max
import kotlin.math.roundToInt

/** The children of the Account header, tagged with `Modifier.layoutId` so [AccountHeaderMeasurePolicy] can tell them apart. */
internal enum class AccountHeaderSlot {
    Back,
    SignOut,
    Avatar,
    Name,
    Email,
    SignedInWithLine,
}

/**
 * Places the Account header's children from one collapse fraction, so the avatar and the name can
 * travel between the identity block and the pinned row instead of cross-fading between two copies.
 *
 * The header is a pinned row, [TopAppBarDefaults.LargeAppBarCollapsedHeight] tall and holding the
 * back arrow and Sign out, above an identity block at least
 * [TopAppBarDefaults.LargeAppBarExpandedHeight] tall. The block grows past that minimum rather than
 * clip when a large font scale needs the room. Collapsing removes the block's height and writes it
 * to [appBarState] as the travel limit, so a list scrolling underneath moves this header with the
 * same state.
 *
 * Expanded, the avatar sits at full size beside the name and email, with the "Signed in with …" line under
 * both. Collapsed, the avatar has shrunk by [avatarCollapsedScale] and sits between the back arrow and
 * the name, which has moved to the pinned row and is narrowed to stop before Sign out. The email and
 * that line never travel: the email trails the name and that line scrolls up with the
 * block, and both are faded out by then. They stay measured even when [showDetails] is false and
 * they are left unplaced, so the block's height, and with it every position, stays the same.
 *
 * [nameLineHeight] is the expanded name style's line height. The block's height is worked out from it
 * rather than from the name as measured, because the measured name changes with the fraction, and a
 * block height that changed with the fraction would move the limit that the fraction is taken from.
 */
@OptIn(ExperimentalMaterial3Api::class)
internal class AccountHeaderMeasurePolicy(
    private val appBarState: TopAppBarState,
    private val collapsedFraction: Float,
    private val showDetails: Boolean,
    private val avatarCollapsedScale: Float,
    private val nameLineHeight: TextUnit,
    private val spacing: AppSpacing,
) : MeasurePolicy {

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val slots = measurables.associateBy { it.layoutId }
        val width = constraints.maxWidth
        val edge = spacing.xxsmall.roundToPx()
        val padding = spacing.normal.roundToPx()
        val gap = spacing.xsmall.roundToPx()
        val pinnedHeight = TopAppBarDefaults.LargeAppBarCollapsedHeight.roundToPx()
        val fraction = collapsedFraction

        val widthOnly = Constraints(maxWidth = width)
        val back = slots.getValue(AccountHeaderSlot.Back).measure(widthOnly)
        val signOut = slots.getValue(AccountHeaderSlot.SignOut).measure(widthOnly)
        val avatar = slots.getValue(AccountHeaderSlot.Avatar).measure(widthOnly)

        val expandedNameX = padding + avatar.width + padding
        val collapsedAvatarSize = avatar.width * avatarCollapsedScale
        val collapsedAvatarX = edge + back.width + gap
        val collapsedNameX = collapsedAvatarX.toFloat() + collapsedAvatarSize + gap
        val nameX = lerp(expandedNameX.toFloat(), collapsedNameX, fraction)
        val nameRightEdge = lerp(
            start = (width - padding).toFloat(),
            stop = (width - edge - signOut.width - gap).toFloat(),
            fraction = fraction,
        )
        val nameWidth = Constraints(maxWidth = (nameRightEdge - nameX).roundToInt().coerceAtLeast(0))
        val emailWidth = Constraints(maxWidth = (width - expandedNameX - padding).coerceAtLeast(0))
        val signedInWithWidth = Constraints(maxWidth = (width - 2 * padding).coerceAtLeast(0))
        val name = slots[AccountHeaderSlot.Name]?.measure(nameWidth)
        val email = slots[AccountHeaderSlot.Email]?.measure(emailWidth)
        val signedInWith = slots[AccountHeaderSlot.SignedInWithLine]?.measure(signedInWithWidth)

        val expandedNameHeight = if (name != null && nameLineHeight.isSpecified) {
            nameLineHeight.toPx().roundToInt()
        } else {
            0
        }
        val identityRowHeight = max(avatar.height, expandedNameHeight + (email?.height ?: 0))
        val contentHeight = padding + identityRowHeight + gap + (signedInWith?.height ?: 0) + padding
        val expandedHeight = max(contentHeight, TopAppBarDefaults.LargeAppBarExpandedHeight.roundToPx())
        updateTravelLimit(expandedHeight)

        val identityTop = pinnedHeight + (expandedHeight - contentHeight) / 2 + padding
        val collapsedOffset = (fraction * expandedHeight).roundToInt()
        return layout(width, pinnedHeight + expandedHeight - collapsedOffset) {
            back.place(edge, (pinnedHeight - back.height) / 2)
            signOut.place(width - edge - signOut.width, (pinnedHeight - signOut.height) / 2)
            if (showDetails) signedInWith?.place(padding, identityTop + identityRowHeight + gap - collapsedOffset)

            val nameHeight = name?.height ?: 0
            val expandedNameY = identityTop + (identityRowHeight - nameHeight - (email?.height ?: 0)) / 2
            val nameY = lerp(expandedNameY, (pinnedHeight - nameHeight) / 2, fraction)
            name?.place(nameX.roundToInt(), nameY)
            if (showDetails) email?.place(expandedNameX, nameY + nameHeight)

            val avatarX = lerp(padding, collapsedAvatarX, fraction)
            val expandedAvatarY = identityTop + (identityRowHeight - avatar.height) / 2
            val collapsedAvatarY = ((pinnedHeight - collapsedAvatarSize) / 2).roundToInt()
            val avatarY = lerp(expandedAvatarY, collapsedAvatarY, fraction)
            placeScaled(avatar, avatarX, avatarY, scale = lerp(1f, avatarCollapsedScale, fraction))
        }
    }

    /** Writes the block's height as the travel limit, and pulls an offset past it (a preview's) back in range. */
    private fun updateTravelLimit(expandedHeight: Int) {
        if (appBarState.heightOffsetLimit != -expandedHeight.toFloat()) {
            appBarState.heightOffsetLimit = -expandedHeight.toFloat()
            appBarState.heightOffset = appBarState.heightOffset
        }
    }

    /** Scales about the top-left corner, so the position given is where the shrunken circle's corner lands. */
    private fun Placeable.PlacementScope.placeScaled(placeable: Placeable, x: Int, y: Int, scale: Float) {
        placeable.placeWithLayer(x, y) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(pivotFractionX = 0f, pivotFractionY = 0f)
        }
    }
}
