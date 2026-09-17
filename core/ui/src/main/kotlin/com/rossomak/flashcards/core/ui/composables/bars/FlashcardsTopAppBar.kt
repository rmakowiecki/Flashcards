package com.rossomak.flashcards.core.ui.composables.bars

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AddToHomeScreen
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AppBarRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.R
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.brandColors

/**
 * The app's detail-screen top bar: a [LargeFlexibleTopAppBar] painted with
 * [com.rossomak.flashcards.core.ui.theme.BrandColors.topBarGradient].
 *
 * The gradient is drawn via [modifier] rather than as a container color — `colors` is forced
 * transparent below so the bar's own container paint doesn't cover it — so it sizes to the bar and
 * shrinks with it as the bar collapses — and, because [modifier] lands on the bar's outermost node,
 * before its internal status-bar inset padding, the gradient bleeds behind the status bar for free.
 *
 * [subtitle] goes straight into the flexible app bar's own `subtitle` slot — M3 1.5 graduated that
 * slot out of `internal`, so there's no more hand-stacking it under [title] in a shared `Column` and
 * no more hand-interpolating the title's text style across the two rows: `LargeFlexibleTopAppBar`
 * composes title and subtitle into both rows itself, at the correct per-row size, and cross-fades
 * them on scroll the same way the rest of M3's app bars do.
 *
 * One behavior change worth knowing: the flexible large title role is `displaySmall` (36sp), not
 * the classic large app bar's `headlineMedium` (28sp) — that's M3's own token for this component,
 * not a local choice.
 *
 * Settings, Debug and other plain screens deliberately keep the stock M3 app bars — this component
 * exists for the gradient treatment alone, so it has no on-surface variant.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FlashcardsTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onNavigateBack: (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val onGradientContent = MaterialTheme.brandColors.onTopBarGradient

    LargeFlexibleTopAppBar(
        modifier = modifier.background(MaterialTheme.brandColors.topBarGradient),
        title = {
            Text(text = title, modifier = Modifier.basicMarquee(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        subtitle = subtitle?.let {
            { Text(text = it, modifier = Modifier.basicMarquee(), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        navigationIcon = {
            if (onNavigateBack != null) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_navigate_back_cd),
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            navigationIconContentColor = onGradientContent,
            titleContentColor = onGradientContent,
            actionIconContentColor = onGradientContent,
            subtitleContentColor = onGradientContent,
        ),
        scrollBehavior = scrollBehavior,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@ShowkaseComposable(name = "Top app bar", group = "Bars")
@Composable
fun FlashcardsTopAppBarShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsTopAppBar(
                title = "Compose",
                subtitle = "Android · Topic",
                onNavigateBack = {},
                scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState()),
                actions = { ShowcaseActions() },
            )
        }
    }
}

/**
 * The collapsed state. `heightOffsetLimit` is only published once the bar has measured itself, and
 * an offset assigned before then is clamped to zero — hence the effect that follows the limit
 * rather than a one-shot assignment. A static `@Preview` renders one frame without running
 * effects, so this reads as expanded there and as collapsed in the Showkase browser.
 */
@OptIn(ExperimentalMaterial3Api::class)
@ShowkaseComposable(name = "Top app bar — collapsed", group = "Bars")
@Composable
fun FlashcardsTopAppBarCollapsedShowcase() {
    FlashcardsTheme {
        Surface {
            val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
            LaunchedEffect(scrollBehavior) {
                snapshotFlow { scrollBehavior.state.heightOffsetLimit }
                    .collect { limit -> scrollBehavior.state.heightOffset = limit }
            }
            FlashcardsTopAppBar(
                title = "Compose",
                subtitle = "Android · Topic",
                onNavigateBack = {},
                scrollBehavior = scrollBehavior,
                actions = { ShowcaseActions() },
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
private fun FlashcardsTopAppBarPreview() {
    FlashcardsTopAppBarShowcase()
}
