package com.rossomak.flashcards.core.ui.composables.bars

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.composables.FlashcardsAvatar
import com.rossomak.flashcards.core.ui.composables.FlashcardsAvatarSize
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsSubtitleWhenCollapsed.Fade
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsSubtitleWhenCollapsed.Keep
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsTopAppBarSize.Large
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsTopAppBarSize.Medium
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle.OnGradient
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle.OnSurface
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme

private const val SHOWCASE_TITLE = "Kotlin coroutines and structured concurrency"

@ShowkaseComposable(name = "Top app bar — Medium, subtitle fades", group = "Bars")
@Composable
fun FlashcardsTopAppBarMediumShowcase() = ShowcaseBar(size = Medium, style = OnSurface, title = "Settings", subtitleWhenCollapsed = Fade)

@ShowkaseComposable(name = "Top app bar — Large on gradient", group = "Bars")
@Composable
fun FlashcardsTopAppBarLargeOnGradientShowcase() = ShowcaseBar(size = Large, style = OnGradient, backdrop = { FlashcardsTopAppBarBrandGraph() })

@ShowkaseComposable(name = "Top app bar — Large on gradient, collapsed", group = "Bars")
@Composable
fun FlashcardsTopAppBarLargeOnGradientCollapsedShowcase() = ShowcaseBar(size = Large, style = OnGradient, collapsed = true, backdrop = { FlashcardsTopAppBarBrandGraph() })

@OptIn(ExperimentalMaterial3Api::class)
@ShowkaseComposable(name = "Top app bar — leading element and details", group = "Bars")
@Composable
fun FlashcardsCollapsingTopAppBarIdentityShowcase() {
    val displayName = "Ada Lovelace"
    FlashcardsTheme {
        Surface {
            FlashcardsCollapsingTopAppBar(
                size = Large,
                scrollBehavior = rememberPreviewTopAppBarScrollBehavior(),
                title = { Text(text = displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                titleStyle = MaterialTheme.typography.headlineSmallEmphasized,
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                titleLeading = {
                    FlashcardsAvatar(photoUrl = null, displayName = displayName, size = FlashcardsAvatarSize.Large, contentDescription = null)
                },
                titleLeadingCollapsedScale = FlashcardsAvatarSize.Small.diameter / FlashcardsAvatarSize.Large.diameter,
                subtitle = { Text(text = "ada@example.com", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                subtitleWhenCollapsed = Fade,
                details = { Text(text = "Signed in with Google", style = MaterialTheme.typography.bodyMedium) },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShowcaseBar(
    size: FlashcardsTopAppBarSize,
    style: FlashcardsComponentStyle,
    title: String = SHOWCASE_TITLE,
    subtitleWhenCollapsed: FlashcardsSubtitleWhenCollapsed = Keep,
    collapsed: Boolean = false,
    backdrop: @Composable () -> Unit = {},
) {
    FlashcardsTheme {
        Surface {
            FlashcardsTopAppBar(
                title = title,
                size = size,
                scrollBehavior = rememberPreviewTopAppBarScrollBehavior(collapsed),
                style = style,
                subtitle = "Android · Topic",
                subtitleWhenCollapsed = subtitleWhenCollapsed,
                navigationIcon = { FlashcardsTopAppBarBackButton(onClick = {}) },
                actions = { ShowcaseActions() },
                backdrop = backdrop,
            )
        }
    }
}

@Composable
private fun RowScope.ShowcaseActions() {
    IconButton(onClick = {}) {
        Icon(imageVector = Icons.Filled.BookmarkBorder, contentDescription = null)
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsTopAppBarLargeOnGradientCollapsedPreview() {
    FlashcardsTopAppBarLargeOnGradientCollapsedShowcase()
}

@PreviewLightDark
@Composable
private fun FlashcardsCollapsingTopAppBarIdentityPreview() {
    FlashcardsCollapsingTopAppBarIdentityShowcase()
}
