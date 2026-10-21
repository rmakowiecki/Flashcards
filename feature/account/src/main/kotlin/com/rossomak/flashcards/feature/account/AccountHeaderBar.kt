package com.rossomak.flashcards.feature.account

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.rossomak.flashcards.core.domain.model.AuthProvider.GitHub
import com.rossomak.flashcards.core.domain.model.AuthProvider.Google
import com.rossomak.flashcards.core.ui.R as CoreUiR
import com.rossomak.flashcards.core.ui.backdrop.BrandMorpheusGraphSpecs
import com.rossomak.flashcards.core.ui.composables.FlashcardsAvatar
import com.rossomak.flashcards.core.ui.composables.FlashcardsAvatarSize.Large
import com.rossomak.flashcards.core.ui.composables.FlashcardsAvatarSize.Small
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsCollapsingTopAppBar
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsSubtitleWhenCollapsed.Fade
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsTopAppBarGraph
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsTopAppBarSize
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme

/** Collapse fraction by which Sign out's label is gone, in step with the email and the provider line under the name. */
private const val SIGN_OUT_LABEL_GONE_AT_FRACTION = 0.5f

/**
 * The Account screen's top bar: a large collapsing bar whose avatar and name travel into the pinned row.
 * The email and the "Signed in with …" line fade out, and Sign out drops its label. A null name, email
 * or provider leaves its line out.
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
    FlashcardsCollapsingTopAppBar(
        size = FlashcardsTopAppBarSize.Large,
        scrollBehavior = scrollBehavior,
        title = {
            state.displayName?.let { displayName ->
                Text(
                    text = displayName,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        modifier = modifier,
        titleStyle = MaterialTheme.typography.headlineSmallEmphasized,
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(CoreUiR.string.common_navigate_back_cd),
                )
            }
        },
        // Decorative: the name beside it already says whose account this is.
        titleLeading = {
            FlashcardsAvatar(
                photoUrl = state.photoUrl,
                displayName = state.displayName,
                size = Large,
                contentDescription = null,
            )
        },
        titleLeadingCollapsedScale = Small.diameter / Large.diameter,
        // The email and the provider line stack under the name as one subtitle, so both fade out together.
        subtitle = if (state.email == null && state.provider == null) {
            null
        } else {
            {
                Column {
                    state.email?.let { email ->
                        Text(text = email, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    state.provider?.let { provider ->
                        AccountSignedInWithLine(provider = provider, onManageAccountClick = onManageAccountClick)
                    }
                }
            }
        },
        subtitleWhenCollapsed = Fade,
        actions = { collapsedFraction ->
            AccountSignOutAction(
                labelProgress = { (1f - collapsedFraction() / SIGN_OUT_LABEL_GONE_AT_FRACTION).coerceIn(0f, 1f) },
                onClick = onSignOutClick,
            )
        },
        backdrop = { FlashcardsTopAppBarGraph(spec = BrandMorpheusGraphSpecs.topAppBarOnSurface, fill = BrandMorpheusGraphSpecs.onSurfaceFill) },
    )
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
    provider = Google,
)

@PreviewLightDark
@Composable
private fun AccountHeaderBarExpandedPreview() {
    AccountHeaderBarPreview(state = PreviewState, initialHeightOffset = 0f)
}

/** The offset starts past the end of travel; the bar pulls it back to exactly collapsed. */
@PreviewLightDark
@Composable
private fun AccountHeaderBarCollapsedPreview() {
    AccountHeaderBarPreview(state = PreviewState, initialHeightOffset = -Float.MAX_VALUE)
}

@PreviewLightDark
@Composable
private fun AccountHeaderBarNoNamePreview() {
    AccountHeaderBarPreview(state = AccountScreenState(email = "radek@example.com", provider = GitHub), initialHeightOffset = 0f)
}
