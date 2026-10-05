package com.rossomak.flashcards.feature.account

import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.rossomak.flashcards.core.ui.R as CoreUiR
import com.rossomak.flashcards.core.ui.composables.FlashcardsAvatar
import com.rossomak.flashcards.core.ui.composables.FlashcardsAvatarSize.Large
import com.rossomak.flashcards.core.ui.composables.FlashcardsAvatarSize.Small
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.spacing

/**
 * Collapse fraction by which the email, the Google line and Sign out's label have faded out. Past it
 * the email and the Google line are neither placed nor announced, which also takes the Manage link
 * out of reach once it is invisible.
 */
private const val DETAILS_FADE_END_FRACTION = 0.5f

/**
 * The Account screen's top bar. Built by hand because no stock M3 bar lets one avatar and one name
 * travel from the identity block into the pinned row as it collapses; see [AccountHeaderMeasurePolicy]
 * for the geometry. It takes the same sizes as a large app bar and is driven by the same
 * [scrollBehavior], so list scrolling collapses it like one, and it can also be dragged directly.
 *
 * Expanded, it shows the avatar beside the name and email, and the "Signed in with Google" line
 * under both. Collapsed, a smaller avatar and the name sit between the back arrow and Sign out,
 * which has dropped its label, and everything else has faded away. A null name or email just leaves
 * its line out.
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
    val collapsedFraction = scrollBehavior.state.collapsedFraction
    val detailsProgress = (1f - collapsedFraction / DETAILS_FADE_END_FRACTION).coerceIn(0f, 1f)
    val hiddenSemantics = if (detailsProgress > 0f) Modifier else Modifier.clearAndSetSemantics {}
    val nameStyle = lerp(
        start = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        stop = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        fraction = collapsedFraction,
    )

    Surface(
        modifier = modifier.accountHeaderDrag(scrollBehavior),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Layout(
            modifier = Modifier.windowInsetsPadding(TopAppBarDefaults.windowInsets),
            content = {
                IconButton(
                    modifier = Modifier.layoutId(AccountHeaderSlot.Back),
                    onClick = onNavigateBack,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(CoreUiR.string.common_navigate_back_cd),
                    )
                }
                AccountSignOutAction(
                    modifier = Modifier.layoutId(AccountHeaderSlot.SignOut),
                    labelProgress = detailsProgress,
                    onClick = onSignOutClick,
                )
                // Decorative: the name beside it already says whose account this is.
                FlashcardsAvatar(
                    photoUrl = state.photoUrl,
                    displayName = state.displayName,
                    size = Large,
                    contentDescription = null,
                    modifier = Modifier.layoutId(AccountHeaderSlot.Avatar),
                )
                state.displayName?.let { displayName ->
                    Text(
                        text = displayName,
                        modifier = Modifier.layoutId(AccountHeaderSlot.Name),
                        style = nameStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Always composed, even once faded out: dropping them would change the measured block
                // height mid-collapse and make the name and avatar jump.
                state.email?.let { email ->
                    Text(
                        text = email,
                        modifier = Modifier
                            .layoutId(AccountHeaderSlot.Email)
                            .alpha(detailsProgress)
                            .then(hiddenSemantics),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AccountSignedInWithGoogleLine(
                    modifier = Modifier
                        .layoutId(AccountHeaderSlot.GoogleLine)
                        .alpha(detailsProgress)
                        .then(hiddenSemantics),
                    onManageAccountClick = onManageAccountClick,
                )
            },
            measurePolicy = AccountHeaderMeasurePolicy(
                appBarState = scrollBehavior.state,
                collapsedFraction = collapsedFraction,
                showDetails = detailsProgress > 0f,
                avatarCollapsedScale = Small.diameter / Large.diameter,
                nameLineHeight = MaterialTheme.typography.headlineSmall.lineHeight,
                spacing = MaterialTheme.spacing,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountHeaderBarPreview(
    state: AccountScreenState,
    initialHeightOffset: Float,
) {
    FlashcardsTheme {
        AccountHeaderBar(
            state = state,
            scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
                rememberTopAppBarState(initialHeightOffset = initialHeightOffset),
            ),
            onNavigateBack = {},
            onSignOutClick = {},
            onManageAccountClick = {},
        )
    }
}

private val PreviewState = AccountScreenState(
    displayName = "Radek Makowiecki",
    email = "radek@example.com",
)

@PreviewLightDark
@Composable
private fun AccountHeaderBarExpandedPreview() {
    AccountHeaderBarPreview(state = PreviewState, initialHeightOffset = 0f)
}

/** The offset starts past the end of travel; the header pulls it back to exactly collapsed. */
@PreviewLightDark
@Composable
private fun AccountHeaderBarCollapsedPreview() {
    AccountHeaderBarPreview(state = PreviewState, initialHeightOffset = -Float.MAX_VALUE)
}

@PreviewLightDark
@Composable
private fun AccountHeaderBarNoNamePreview() {
    AccountHeaderBarPreview(state = AccountScreenState(email = "radek@example.com"), initialHeightOffset = 0f)
}
