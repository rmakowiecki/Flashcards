package com.rossomak.flashcards.feature.account

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.rossomak.flashcards.core.domain.model.AuthProvider.GitHub
import com.rossomak.flashcards.core.domain.model.AuthProvider.Google
import com.rossomak.flashcards.core.ui.composables.FlashcardsAvatar
import com.rossomak.flashcards.core.ui.composables.FlashcardsAvatarSize.Large
import com.rossomak.flashcards.core.ui.composables.FlashcardsAvatarSize.Small
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsCollapsingTopAppBar
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsSubtitleWhenCollapsed.Fade
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsTopAppBarBackButton
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsTopAppBarOnSurfaceGraph
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsTopAppBarSize
import com.rossomak.flashcards.core.ui.composables.bars.fadedSlotAlpha
import com.rossomak.flashcards.core.ui.composables.bars.rememberPreviewTopAppBarScrollBehavior
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme

/** The Account screen's top bar. A null name, email or provider leaves its line out. */
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
        navigationIcon = { FlashcardsTopAppBarBackButton(onClick = onNavigateBack) },
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
                labelProgress = { fadedSlotAlpha(collapsedFraction()) },
                onClick = onSignOutClick,
            )
        },
        backdrop = { FlashcardsTopAppBarOnSurfaceGraph() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountHeaderBarPreview(state: AccountScreenState, collapsed: Boolean) {
    FlashcardsTheme {
        AccountHeaderBar(
            state = state,
            scrollBehavior = rememberPreviewTopAppBarScrollBehavior(collapsed),
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
    AccountHeaderBarPreview(state = PreviewState, collapsed = false)
}

@PreviewLightDark
@Composable
private fun AccountHeaderBarCollapsedPreview() {
    AccountHeaderBarPreview(state = PreviewState, collapsed = true)
}

@PreviewLightDark
@Composable
private fun AccountHeaderBarNoNamePreview() {
    AccountHeaderBarPreview(state = AccountScreenState(email = "radek@example.com", provider = GitHub), collapsed = false)
}
