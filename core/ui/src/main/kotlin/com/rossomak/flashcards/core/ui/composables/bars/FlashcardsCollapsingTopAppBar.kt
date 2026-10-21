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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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

/** The collapse fraction the title slot sees: the bar's own for the live title, `0` for the expanded-style sizer copy. */
internal val LocalTopAppBarTitleFraction = staticCompositionLocalOf { ExpandedFraction }

/**
 * The app's collapsing top app bar (ADR-0072): a pinned row with [navigationIcon] and [actions] over an
 * expanded block. [title] and [titleLeading] are single nodes that travel into the pinned row instead of
 * cross-fading. The bar can be dragged, and a released drag settles on the nearer end.
 *
 * Slot text gets its style from [LocalTextStyle], lerped from expanded to collapsed. The title is also
 * composed once more, unplaced and silent, to measure the block, so it should hold no state.
 *
 * A [Fade] [subtitle] and the [details] fade out by half the collapse, then are neither placed nor
 * announced. [backdrop] sits over the [style] paint and under every slot, at the expanded height, clipped
 * as the bar collapses. Only the title and subtitle recompose while collapsing; [actions] and [backdrop]
 * read the fraction through a provider.
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
    collapsedTitleStyle: TextStyle = MaterialTheme.typography.titleLargeEmphasized,
    navigationIcon: @Composable () -> Unit = {},
    titleLeading: (@Composable () -> Unit)? = null,
    titleLeadingCollapsedScale: Float = 1f,
    subtitle: (@Composable () -> Unit)? = null,
    subtitleWhenCollapsed: FlashcardsSubtitleWhenCollapsed = Keep,
    details: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.(collapsedFraction: () -> Float) -> Unit = {},
    backdrop: @Composable FlashcardsTopAppBarBackdropScope.() -> Unit = {},
) {
    val appBarState = scrollBehavior.state
    val collapsedFraction = remember(appBarState) { { appBarState.collapsedFraction } }
    val backdropScope = remember(collapsedFraction) { TopAppBarBackdropScope(collapsedFraction) }
    val subtitleStyle = MaterialTheme.typography.titleMediumEmphasized
    val collapsedSubtitleStyle = when (subtitleWhenCollapsed) {
        Keep -> MaterialTheme.typography.labelLargeEmphasized
        Fade -> subtitleStyle
    }
    val containerModifier = when (style) {
        OnSurface -> Modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest)
        OnGradient -> Modifier.background(MaterialTheme.brandColors.topBarGradient)
    }
    val contentColor = when (style) {
        OnSurface -> MaterialTheme.colorScheme.onSurface
        OnGradient -> MaterialTheme.brandColors.onTopBarGradient
    }
    val variantContentColor = when (style) {
        OnSurface -> MaterialTheme.colorScheme.onSurfaceVariant
        OnGradient -> MaterialTheme.brandColors.onTopBarGradient
    }
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
            .then(containerModifier)
            .clipToBounds()
            .topAppBarDrag(scrollBehavior),
        content = {
            Box(modifier = Modifier.layoutId(TopAppBarSlot.Backdrop).clearAndSetSemantics {}) {
                backdropScope.backdrop()
            }
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                Box(modifier = Modifier.layoutId(TopAppBarSlot.Navigation)) { navigationIcon() }
                Row(modifier = Modifier.layoutId(TopAppBarSlot.Actions), verticalAlignment = Alignment.CenterVertically) {
                    actions(collapsedFraction)
                }
                titleLeading?.let { leading ->
                    Box(modifier = Modifier.layoutId(TopAppBarSlot.TitleLeading)) { leading() }
                }
                Box(modifier = Modifier.layoutId(TopAppBarSlot.Title).semantics(mergeDescendants = true) { heading() }) {
                    ProvideCollapsingTextStyle(expanded = titleStyle, collapsed = collapsedTitleStyle, collapsedFraction = collapsedFraction) {
                        CompositionLocalProvider(LocalTopAppBarTitleFraction provides collapsedFraction, content = title)
                    }
                }
                Box(modifier = Modifier.layoutId(TopAppBarSlot.TitleSizer).clearAndSetSemantics {}) {
                    CompositionLocalProvider(LocalTextStyle provides titleStyle, content = title)
                }
            }
            CompositionLocalProvider(LocalContentColor provides variantContentColor) {
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

/** Provides the style lerped at the current fraction, in its own scope so a scroll recomposes only the text under it. */
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

/** A slot that, when it [fades], goes silent once faded out. Alpha and placement are the measure policy's. */
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
