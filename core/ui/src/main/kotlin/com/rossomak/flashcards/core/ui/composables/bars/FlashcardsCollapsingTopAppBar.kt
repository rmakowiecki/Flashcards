package com.rossomak.flashcards.core.ui.composables.bars

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.lerp
import com.rossomak.flashcards.core.designsystem.theme.brandColors
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsSubtitleWhenCollapsed.Fade
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsSubtitleWhenCollapsed.Keep
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle.OnGradient
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle.OnSurface

private val ExpandedFraction: () -> Float = { 0f }

/** Collapse fraction for the title slot: the bar's own for the live title, `0` for the sizer copy. */
internal val LocalTopAppBarTitleFraction = staticCompositionLocalOf { ExpandedFraction }

/**
 * The app's collapsing top app bar (ADR-0072): a pinned row ([navigationIcon], [actions]) over an
 * expanded block. [title] and [titleLeading] move into the pinned row instead of cross-fading; a drag
 * moves the bar and settles on the nearer end.
 *
 * Slot text style comes from [LocalTextStyle], lerped expanded to collapsed. [title] is composed twice
 * (once unplaced, to measure the block), so it should hold no state. A [Fade] [subtitle] and [details]
 * fade out by half the collapse. [backdrop] sits under every slot at the expanded height, clipped on
 * collapse. [actions] reads the fraction through a provider.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardsCollapsingTopAppBar(
    size: FlashcardsTopAppBarSize,
    scrollBehavior: TopAppBarScrollBehavior,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    style: FlashcardsComponentStyle = OnSurface,
    titleStyle: TextStyle = size.titleStyle,
    navigationIcon: @Composable () -> Unit = {},
    titleLeading: (@Composable () -> Unit)? = null,
    titleLeadingCollapsedScale: Float = 1f,
    subtitle: (@Composable () -> Unit)? = null,
    subtitleWhenCollapsed: FlashcardsSubtitleWhenCollapsed = Keep,
    details: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.(collapsedFraction: () -> Float) -> Unit = {},
    backdrop: @Composable () -> Unit = {},
) {
    val appBarState = scrollBehavior.state
    val collapsedFraction = remember(appBarState) { { appBarState.collapsedFraction } }
    val subtitleStyle = MaterialTheme.typography.titleMediumEmphasized
    val collapsedSubtitleStyle = when (subtitleWhenCollapsed) {
        Keep -> MaterialTheme.typography.labelLargeEmphasized
        Fade -> subtitleStyle
    }
    val colors = style.topAppBarColors()
    val windowInsets = TopAppBarDefaults.windowInsets
    val spacing = MaterialTheme.spacing
    val measurePolicy = remember(appBarState, windowInsets, size, titleLeadingCollapsedScale, subtitleWhenCollapsed, spacing) {
        TopAppBarMeasurePolicy(
            appBarState = appBarState,
            windowInsets = windowInsets,
            size = size,
            titleLeadingCollapsedScale = titleLeadingCollapsedScale,
            subtitleWhenCollapsed = subtitleWhenCollapsed,
            spacing = spacing,
        )
    }

    Layout(
        modifier = modifier
            .then(colors.container)
            .clipToBounds()
            .topAppBarDrag(scrollBehavior),
        content = {
            Box(modifier = Modifier.layoutId(TopAppBarSlot.Backdrop).clearAndSetSemantics {}) {
                backdrop()
            }
            CompositionLocalProvider(LocalContentColor provides colors.content) {
                Box(modifier = Modifier.layoutId(TopAppBarSlot.Navigation)) { navigationIcon() }
                Row(modifier = Modifier.layoutId(TopAppBarSlot.Actions), verticalAlignment = Alignment.CenterVertically) {
                    actions(collapsedFraction)
                }
                titleLeading?.let { leading ->
                    Box(modifier = Modifier.layoutId(TopAppBarSlot.TitleLeading)) { leading() }
                }
                Box(modifier = Modifier.layoutId(TopAppBarSlot.Title).semantics(mergeDescendants = true) { heading() }) {
                    ProvideCollapsingTextStyle(expanded = titleStyle, collapsed = MaterialTheme.typography.titleLargeEmphasized, collapsedFraction = collapsedFraction) {
                        CompositionLocalProvider(LocalTopAppBarTitleFraction provides collapsedFraction, content = title)
                    }
                }
                Box(modifier = Modifier.layoutId(TopAppBarSlot.TitleSizer).clearAndSetSemantics {}) {
                    CompositionLocalProvider(LocalTextStyle provides titleStyle, content = title)
                }
            }
            CompositionLocalProvider(LocalContentColor provides colors.variantContent) {
                subtitle?.let { subtitleSlot ->
                    FadingSlot(slot = TopAppBarSlot.Subtitle, fades = subtitleWhenCollapsed == Fade, collapsedFraction = collapsedFraction) {
                        ProvideCollapsingTextStyle(expanded = subtitleStyle, collapsed = collapsedSubtitleStyle, collapsedFraction = collapsedFraction, content = subtitleSlot)
                    }
                    Box(modifier = Modifier.layoutId(TopAppBarSlot.SubtitleSizer).clearAndSetSemantics {}) {
                        CompositionLocalProvider(LocalTextStyle provides subtitleStyle, content = subtitleSlot)
                    }
                }
                details?.let { detailsSlot ->
                    FadingSlot(slot = TopAppBarSlot.Details, fades = true, collapsedFraction = collapsedFraction, content = detailsSlot)
                }
            }
        },
        measurePolicy = measurePolicy,
    )
}

private class TopAppBarColors(
    val container: Modifier,
    val content: Color,
    val variantContent: Color,
)

@Composable
@ReadOnlyComposable
private fun FlashcardsComponentStyle.topAppBarColors(): TopAppBarColors = when (this) {
    OnSurface -> TopAppBarColors(
        container = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest),
        content = MaterialTheme.colorScheme.onSurface,
        variantContent = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OnGradient -> TopAppBarColors(
        container = Modifier.background(MaterialTheme.brandColors.topBarGradient),
        content = MaterialTheme.brandColors.onTopBarGradient,
        variantContent = MaterialTheme.brandColors.onTopBarGradient,
    )
}

/** Own scope, so a scroll recomposes only the text under it. */
@Composable
private fun ProvideCollapsingTextStyle(
    expanded: TextStyle,
    collapsed: TextStyle,
    collapsedFraction: () -> Float,
    content: @Composable () -> Unit,
) {
    val style = if (expanded == collapsed) expanded else lerp(expanded, collapsed, collapsedFraction())
    CompositionLocalProvider(LocalTextStyle provides style, content = content)
}

/** Silences a [fades] slot once faded out; alpha and placement live in the measure policy. */
@Composable
private fun FadingSlot(
    slot: TopAppBarSlot,
    fades: Boolean,
    collapsedFraction: () -> Float,
    content: @Composable () -> Unit,
) {
    val isFadedOut by remember(collapsedFraction) { derivedStateOf { fadedSlotAlpha(collapsedFraction()) == 0f } }
    val silence = if (fades && isFadedOut) Modifier.clearAndSetSemantics {} else Modifier
    Box(modifier = Modifier.layoutId(slot).then(silence)) { content() }
}
