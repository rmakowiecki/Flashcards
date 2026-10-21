package com.rossomak.flashcards.core.ui.composables.bars

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AddToHomeScreen
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AppBarRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.backdrop.BrandMorpheusGraphSpecs
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

/** An offset past any travel limit: the bar pulls it back to exactly collapsed, so a static preview renders collapsed. */
private const val COLLAPSED = -Float.MAX_VALUE

private const val EXPANDED = 0f

private const val SHOWCASE_TITLE = "Compose"
private const val SHOWCASE_SUBTITLE = "Android · Topic"

@ShowkaseComposable(name = "Top app bar — Medium", group = "Bars")
@Composable
fun FlashcardsTopAppBarMediumShowcase() = ShowcaseBar(size = Medium, style = OnSurface, initialHeightOffset = EXPANDED)

@ShowkaseComposable(name = "Top app bar — Medium, collapsed", group = "Bars")
@Composable
fun FlashcardsTopAppBarMediumCollapsedShowcase() = ShowcaseBar(size = Medium, style = OnSurface, initialHeightOffset = COLLAPSED)

@ShowkaseComposable(name = "Top app bar — Medium on gradient", group = "Bars")
@Composable
fun FlashcardsTopAppBarMediumOnGradientShowcase() = ShowcaseBar(size = Medium, style = OnGradient, initialHeightOffset = EXPANDED)

@ShowkaseComposable(name = "Top app bar — Medium on gradient, collapsed", group = "Bars")
@Composable
fun FlashcardsTopAppBarMediumOnGradientCollapsedShowcase() = ShowcaseBar(size = Medium, style = OnGradient, initialHeightOffset = COLLAPSED)

@ShowkaseComposable(name = "Top app bar — Large", group = "Bars")
@Composable
fun FlashcardsTopAppBarLargeShowcase() = ShowcaseBar(size = Large, style = OnSurface, initialHeightOffset = EXPANDED)

@ShowkaseComposable(name = "Top app bar — Large, collapsed", group = "Bars")
@Composable
fun FlashcardsTopAppBarLargeCollapsedShowcase() = ShowcaseBar(size = Large, style = OnSurface, initialHeightOffset = COLLAPSED)

@ShowkaseComposable(name = "Top app bar — Large on gradient", group = "Bars")
@Composable
fun FlashcardsTopAppBarLargeOnGradientShowcase() = ShowcaseBar(size = Large, style = OnGradient, initialHeightOffset = EXPANDED)

@ShowkaseComposable(name = "Top app bar — Large on gradient, collapsed", group = "Bars")
@Composable
fun FlashcardsTopAppBarLargeOnGradientCollapsedShowcase() = ShowcaseBar(size = Large, style = OnGradient, initialHeightOffset = COLLAPSED)

@ShowkaseComposable(name = "Top app bar — Large on gradient, graph kept, collapsed", group = "Bars")
@Composable
fun FlashcardsTopAppBarGraphKeptShowcase() = ShowcaseBar(
    size = Large,
    style = OnGradient,
    initialHeightOffset = COLLAPSED,
    backdrop = { FlashcardsTopAppBarGraph(spec = BrandMorpheusGraphSpecs.topAppBar, fill = BrandMorpheusGraphSpecs.fill) },
)

@OptIn(ExperimentalMaterial3Api::class)
@ShowkaseComposable(name = "Top app bar — Large on gradient, graph fading, mid-collapse", group = "Bars")
@Composable
fun FlashcardsTopAppBarGraphFadingShowcase() {
    val halfOfMinimumTravel = with(LocalDensity.current) {
        (TopAppBarDefaults.LargeAppBarExpandedHeight - TopAppBarDefaults.LargeAppBarCollapsedHeight).toPx() / 2
    }
    ShowcaseBar(
        size = Large,
        style = OnGradient,
        initialHeightOffset = -halfOfMinimumTravel,
        backdrop = {
            FlashcardsTopAppBarGraph(
                spec = BrandMorpheusGraphSpecs.topAppBar,
                fill = BrandMorpheusGraphSpecs.fill,
                fadesOnCollapse = true,
            )
        },
    )
}

@ShowkaseComposable(name = "Top app bar — Large with white graph", group = "Bars")
@Composable
fun FlashcardsTopAppBarOnSurfaceGraphShowcase() = ShowcaseBar(
    size = Large,
    style = OnSurface,
    initialHeightOffset = EXPANDED,
    backdrop = { FlashcardsTopAppBarGraph(spec = BrandMorpheusGraphSpecs.topAppBarOnSurface, fill = BrandMorpheusGraphSpecs.onSurfaceFill) },
)

@ShowkaseComposable(name = "Top app bar — subtitle fades", group = "Bars")
@Composable
fun FlashcardsTopAppBarSubtitleFadesShowcase() = ShowcaseBar(
    size = Medium,
    style = OnSurface,
    initialHeightOffset = EXPANDED,
    subtitle = "Version 1.0 (42) · debug",
    subtitleWhenCollapsed = Fade,
)

@ShowkaseComposable(name = "Top app bar — two-line title", group = "Bars")
@Composable
fun FlashcardsTopAppBarTwoLineTitleShowcase() = ShowcaseBar(
    size = Large,
    style = OnGradient,
    initialHeightOffset = EXPANDED,
    title = "Kotlin coroutines and structured concurrency",
)

/** The low-level bar with a leading element that travels and details that fade, the way an identity header uses it. */
@OptIn(ExperimentalMaterial3Api::class)
@ShowkaseComposable(name = "Top app bar — leading element and details", group = "Bars")
@Composable
fun FlashcardsCollapsingTopAppBarIdentityShowcase() {
    val displayName = "Ada Lovelace"
    FlashcardsTheme {
        Surface {
            FlashcardsCollapsingTopAppBar(
                size = Large,
                scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState()),
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
    initialHeightOffset: Float,
    title: String = SHOWCASE_TITLE,
    subtitle: String = SHOWCASE_SUBTITLE,
    subtitleWhenCollapsed: FlashcardsSubtitleWhenCollapsed = Keep,
    backdrop: @Composable FlashcardsTopAppBarBackdropScope.() -> Unit = {},
) {
    FlashcardsTheme {
        Surface {
            FlashcardsTopAppBar(
                title = title,
                size = size,
                scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
                    rememberTopAppBarState(initialHeightOffset = initialHeightOffset),
                ),
                style = style,
                subtitle = subtitle,
                subtitleWhenCollapsed = subtitleWhenCollapsed,
                onNavigateBack = {},
                actions = { ShowcaseActions() },
                backdrop = backdrop,
            )
        }
    }
}

/**
 * The details-screen action set: a bookmark toggle in the bar and, behind the overflow indicator,
 * the items that don't fit. [AppBarRow] renders `maxItemCount - 1` items inline and moves the rest
 * into the menu, so two items with a max of two means one visible icon plus the ⋮.
 */
@Composable
private fun RowScope.ShowcaseActions() {
    AppBarRow(
        overflowIndicator = { menuState ->
            IconButton(onClick = { menuState.show() }) {
                Icon(imageVector = Icons.Filled.MoreVert, contentDescription = null)
            }
        },
        maxItemCount = 2,
    ) {
        clickableItem(
            onClick = {},
            icon = { Icon(imageVector = Icons.Filled.BookmarkBorder, contentDescription = null) },
            label = "Bookmark",
        )
        clickableItem(
            onClick = {},
            icon = { Icon(imageVector = Icons.AutoMirrored.Filled.AddToHomeScreen, contentDescription = null) },
            label = "Add to home screen",
        )
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsTopAppBarLargeOnGradientCollapsedPreview() {
    FlashcardsTopAppBarGraphKeptShowcase()
}

@PreviewLightDark
@Composable
private fun FlashcardsTopAppBarOnSurfaceGraphPreview() {
    FlashcardsTopAppBarOnSurfaceGraphShowcase()
}

@PreviewLightDark
@Composable
private fun FlashcardsCollapsingTopAppBarIdentityPreview() {
    FlashcardsCollapsingTopAppBarIdentityShowcase()
}
