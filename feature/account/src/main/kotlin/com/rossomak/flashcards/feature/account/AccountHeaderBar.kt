package com.rossomak.flashcards.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.rossomak.flashcards.core.ui.R as CoreUiR
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsTextButton
import com.rossomak.flashcards.core.ui.theme.spacing
import kotlin.math.roundToInt

private val AVATAR_SIZE = 88.dp
private const val MANAGE_LINK_TAG = "manage"

/**
 * The Account screen's top bar. Built by hand because no stock M3 bar fits it: the pinned row (back
 * arrow, Sign out) is [TopAppBarDefaults.LargeAppBarCollapsedHeight] and the block below it is at
 * least [TopAppBarDefaults.LargeAppBarExpandedHeight], the same sizes as a large app bar, but the
 * block holds an avatar beside the name and email, and the "Signed in with Google" line under both,
 * which a large bar's title slot cannot carry.
 *
 * The block translates up under the pinned row as [scrollBehavior] collapses it. It grows past the
 * M3 height rather than clipping when a large font scale needs more room, and the scroll behavior's
 * limit follows the measured height.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountHeaderBar(
    modifier: Modifier = Modifier,
    state: AccountScreenState,
    scrollBehavior: TopAppBarScrollBehavior,
    onNavigateBack: () -> Unit,
    onSignOutClick: () -> Unit,
    onManageAccountClick: () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(modifier = Modifier.windowInsetsPadding(TopAppBarDefaults.windowInsets)) {
            PinnedActionsRow(
                onNavigateBack = onNavigateBack,
                onSignOutClick = onSignOutClick,
            )
            CollapsingIdentityBlock(
                state = state,
                scrollBehavior = scrollBehavior,
                onManageAccountClick = onManageAccountClick,
            )
        }
    }
}

@Composable
private fun PinnedActionsRow(
    onNavigateBack: () -> Unit,
    onSignOutClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TopAppBarDefaults.LargeAppBarCollapsedHeight)
            .padding(horizontal = MaterialTheme.spacing.xxsmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(CoreUiR.string.common_navigate_back_cd),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        FlashcardsTextButton(
            text = stringResource(R.string.account_sign_out_button),
            onClick = onSignOutClick,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollapsingIdentityBlock(
    state: AccountScreenState,
    scrollBehavior: TopAppBarScrollBehavior,
    onManageAccountClick: () -> Unit,
) {
    val expandedHeightPx = with(LocalDensity.current) { TopAppBarDefaults.LargeAppBarExpandedHeight.roundToPx() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    constraints.copy(minHeight = expandedHeightPx, maxHeight = Constraints.Infinity),
                )
                scrollBehavior.state.heightOffsetLimit = -placeable.height.toFloat()
                val heightOffset = scrollBehavior.state.heightOffset
                layout(placeable.width, (placeable.height + heightOffset).roundToInt()) {
                    placeable.place(0, heightOffset.roundToInt())
                }
            }
            .clipToBounds(),
        contentAlignment = Alignment.Center,
        propagateMinConstraints = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.normal),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.normal),
            ) {
                AccountAvatar()
                Column(modifier = Modifier.weight(1f)) {
                    state.displayName?.let { displayName ->
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    state.email?.let { email ->
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            SignedInWithGoogleLine(onManageAccountClick = onManageAccountClick)
        }
    }
}

/** Placeholder until the shared avatar component exists: swapping it in is a change to this function only. */
@Composable
private fun AccountAvatar() {
    Icon(
        imageVector = Icons.Filled.AccountCircle,
        contentDescription = null,
        modifier = Modifier.size(AVATAR_SIZE),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** One string with a clickable span, so a screen reader announces a single line with a link in it. */
@Composable
private fun SignedInWithGoogleLine(onManageAccountClick: () -> Unit) {
    val signedInLabel = stringResource(R.string.account_signed_in_with_google_label)
    val separator = stringResource(CoreUiR.string.common_middle_dot_separator)
    val manageLabel = stringResource(R.string.account_manage_button)
    val manageStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = buildAnnotatedString {
            append(signedInLabel)
            append(separator)
            withLink(
                LinkAnnotation.Clickable(
                    tag = MANAGE_LINK_TAG,
                    styles = TextLinkStyles(style = manageStyle),
                    linkInteractionListener = { onManageAccountClick() },
                ),
            ) {
                append(manageLabel)
            }
        },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
