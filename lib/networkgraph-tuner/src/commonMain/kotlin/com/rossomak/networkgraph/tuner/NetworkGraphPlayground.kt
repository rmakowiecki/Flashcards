@file:OptIn(ExperimentalNetworkGraphApi::class)

package com.rossomak.networkgraph.tuner

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rossomak.networkgraph.ExperimentalNetworkGraphApi
import com.rossomak.networkgraph.Follow
import com.rossomak.networkgraph.NetworkGraphFrameStats
import com.rossomak.networkgraph.NetworkGraphLayerStats
import com.rossomak.networkgraph.NetworkGraphMorph
import com.rossomak.networkgraph.NetworkGraphSpec
import com.rossomak.networkgraph.networkGraphBackground
import com.rossomak.networkgraph.rememberNetworkGraphMorphState
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A sample and tuner in one: a full-screen pager for finding which network-graph background looks
 * best, and on which surfaces.
 *
 * One [presets] spec per pager page, each shown on the two aspect ratios a background has to hold up
 * on: a wide, short top app bar strip and a tall hero. Then a morph page that scrubs or times a morph
 * between two [morphFamily] specs, and one page per entry of [decks]: a stand-in onboarding pager whose
 * background morphs with its scroll position and whose surface can shrink and grow.
 *
 * The bottom overlay, laid out under the pages rather than over them, reseeds the page's layout, steps
 * a deck page through its deck, steps every page through [styles], copies the page's current spec as Kotlin to the clipboard and
 * [onSpecExport], and opens the tuning sheet, which edits that spec live: the morph's "to" side on the
 * morph page, the settled step on a deck page. Edits live in memory for the playground's lifetime only.
 *
 * Renders with the caller's `MaterialTheme`; [styles] and [deckStyle] carry the caller's brand, and
 * [navigationIcon] goes at the start of every page's app bar.
 */
@Composable
public fun NetworkGraphPlayground(
    modifier: Modifier = Modifier,
    presets: List<NetworkGraphPreset>,
    morphFamily: List<NetworkGraphPreset>,
    decks: List<List<NetworkGraphPreset>>,
    styles: List<NetworkGraphPlaygroundStyle>,
    deckStyle: NetworkGraphPlaygroundStyle,
    onSpecExport: (source: String) -> Unit = {},
    navigationIcon: @Composable () -> Unit = {},
) {
    require(styles.isNotEmpty()) { "The playground needs at least one style" }
    val specs = remember { presets.toMutableStateList() }
    val morphSpecs = remember { morphFamily.toMutableStateList() }
    val deckStates = decks.map { pages -> rememberDeck(pages) }
    val morphSelection = remember { MorphSelection(defaultToIndex = MORPH_DEFAULT_TO_INDEX.coerceAtMost(morphFamily.lastIndex)) }
    var styleIndex by remember { mutableIntStateOf(0) }
    val style = styles[styleIndex.coerceIn(styles.indices)]
    var isTuning by remember { mutableStateOf(false) }
    val pageCount = specs.size + 1 + deckStates.size
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val stats = remember { NetworkGraphFrameStats() }
    val layerStats = remember { NetworkGraphLayerStats() }
    var showViolations by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    RecordFrameIntervals(stats)
    val currentPage = pagerState.currentPage
    val deck = deckStates.getOrNull(currentPage - specs.size - 1)
    val slot = specSlotFor(currentPage, specs, morphSpecs, morphSelection, deck, coroutineScope)

    // The overlay sits under the pager rather than over it, so it never hides a page's content.
    Column(modifier = modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, userScrollEnabled = !isTuning, modifier = Modifier.weight(1f)) { page ->
            val pageLayerStats = layerStats.takeIf { page == pagerState.currentPage } // A neighbour mid-swipe must not overwrite the counts.
            when {
                page < specs.size -> GraphPage(
                    morph = NetworkGraphMorph(specs[page].spec, specs[page].spec, 0f),
                    title = specs[page].name,
                    note = specs[page].note,
                    style = style,
                    stats = stats,
                    layerStats = pageLayerStats,
                    showViolations = showViolations,
                    navigationIcon = navigationIcon,
                )
                page == specs.size -> MorphPage(
                    specs = morphSpecs,
                    selection = morphSelection,
                    style = style,
                    stats = stats,
                    layerStats = pageLayerStats,
                    showViolations = showViolations,
                    navigationIcon = navigationIcon,
                )
                else -> DeckPage(
                    deck = deckStates[page - specs.size - 1],
                    style = deckStyle,
                    stats = stats,
                    layerStats = pageLayerStats,
                    showViolations = showViolations,
                )
            }
        }

        PlaygroundOverlay(
            slot = slot,
            pageLabel = "${currentPage + 1}/$pageCount",
            stats = stats,
            layerSummary = layerStats.summary,
            onPrevious = { coroutineScope.launch { pagerState.animateScrollToPage((currentPage - 1 + pageCount) % pageCount) } },
            onNext = { coroutineScope.launch { pagerState.animateScrollToPage((currentPage + 1) % pageCount) } },
            onStyleToggle = { styleIndex = (styleIndex + 1) % styles.size },
            onCopy = { coroutineScope.launch { copySpec(slot.spec, clipboard, onSpecExport) } },
            onTune = { isTuning = true },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(SpacingNormal)
                .windowInsetsPadding(WindowInsets.navigationBars),
        )
    }

    if (isTuning) {
        TuningSheet(
            slot = slot,
            showViolations = showViolations,
            onShowViolationsChange = { showViolations = it },
            onDismiss = { isTuning = false },
        )
    }
}

@Composable
private fun RecordFrameIntervals(stats: NetworkGraphFrameStats) {
    LaunchedEffect(stats) {
        var previousNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { nowNanos ->
                stats.recordFrame(nowNanos - previousNanos)
                previousNanos = nowNanos
            }
        }
    }
}

/** One stand-in onboarding pager: its steps' specs, editable in place, and its own pager. */
private class Deck(val specs: SnapshotStateList<NetworkGraphPreset>, val pagerState: PagerState)

@Composable
private fun rememberDeck(pages: List<NetworkGraphPreset>): Deck {
    val specs = remember { pages.toMutableStateList() }
    val pagerState = rememberPagerState(pageCount = { specs.size })
    return remember(pagerState) { Deck(specs, pagerState) }
}

private fun specSlotFor(
    page: Int,
    specs: SnapshotStateList<NetworkGraphPreset>,
    morphSpecs: SnapshotStateList<NetworkGraphPreset>,
    morphSelection: MorphSelection,
    deck: Deck?,
    coroutineScope: CoroutineScope,
): SpecSlot = when {
    page < specs.size -> SpecSlot(
        preset = specs[page],
        onSpecChange = { specs[page] = specs[page].copy(spec = it) },
        onReseed = { specs[page] = specs[page].let { preset -> preset.copy(spec = preset.spec.copy(seed = Random.nextInt())) } },
    )
    page == specs.size -> SpecSlot(
        preset = morphSpecs[morphSelection.toIndex],
        onSpecChange = { morphSpecs[morphSelection.toIndex] = morphSpecs[morphSelection.toIndex].copy(spec = it) },
        onReseed = { morphSpecs.reseedTogether() },
        onStart = { coroutineScope.launch { morphSelection.play() } },
        sheetHeader = {
            MorphControls(
                specs = morphSpecs,
                selection = morphSelection,
                onScrub = { fraction -> coroutineScope.launch { morphSelection.scrubTo(fraction) } },
            )
        },
    )
    else -> {
        val currentDeck = checkNotNull(deck) { "Page $page is past the deck pages" }
        val pagerState = currentDeck.pagerState
        val step = pagerState.currentPage
        SpecSlot(
            preset = currentDeck.specs[step],
            onSpecChange = { currentDeck.specs[step] = currentDeck.specs[step].copy(spec = it) },
            onReseed = { currentDeck.specs.reseedTogether() },
            deckStep = DeckStep(
                canGoBack = step > 0,
                canGoForward = step < pagerState.pageCount - 1,
                onBack = { coroutineScope.launch { pagerState.animateScrollToPage(step - 1) } },
                onForward = { coroutineScope.launch { pagerState.animateScrollToPage(step + 1) } },
                onJump = { coroutineScope.launch { pagerState.scrollToPage(if (step == pagerState.pageCount - 1) 0 else pagerState.pageCount - 1) } },
            ),
        )
    }
}

/** Copies [spec] as Kotlin source to the clipboard and hands it to [onSpecExport], ready to paste into a preset list. */
private suspend fun copySpec(spec: NetworkGraphSpec, clipboard: Clipboard, onSpecExport: (String) -> Unit) {
    val source = spec.toKotlinSource()
    onSpecExport(source)
    clipboard.setClipEntry(plainTextClipEntry(source))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TuningSheet(slot: SpecSlot, showViolations: Boolean, onShowViolationsChange: (Boolean) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // No scrim: the whole point of tuning is watching the graph change above the sheet.
        scrimColor = Color.Transparent,
    ) {
        NetworkGraphTuningSheetContent(
            spec = slot.spec,
            showViolations = showViolations,
            onSpecChange = slot.onSpecChange,
            onShowViolationsChange = onShowViolationsChange,
            header = slot.sheetHeader ?: {},
        )
    }
}

/** With the lib's morph family, the morph page starts on uniform field → corner bloom. */
private const val MORPH_DEFAULT_TO_INDEX = 3

/** How far a deck page's surface shrinks, as a share of the page's height. */
private const val DECK_SHRUNK_HEIGHT = 0.45f
private const val DECK_RESIZE_MILLIS = 900

/** How long Start takes to morph from one end to the other. */
private const val MORPH_PLAY_MILLIS = 1_200
private const val MORPH_HALFWAY = 0.5f

/**
 * Which two morph family specs the morph page morphs between, and how far: [progress] runs from 0
 * (from) to 1 (to), scrubbed by the tuning sheet's slider and played end to end by the overlay's Start
 * button.
 */
@Stable
private class MorphSelection(defaultToIndex: Int) {
    var fromIndex by mutableIntStateOf(0)
    var toIndex by mutableIntStateOf(defaultToIndex)
    val progress = Animatable(0f)

    /** Morphs to whichever end is further away; a scrub stops it. */
    suspend fun play() {
        progress.animateTo(if (progress.value < MORPH_HALFWAY) 1f else 0f, tween(MORPH_PLAY_MILLIS, easing = FastOutSlowInEasing))
    }

    suspend fun scrubTo(fraction: Float) = progress.snapTo(fraction)
}

/**
 * The spec the overlay and the tuning sheet act on for the current page, with its label, plus the morph page's
 * extras: [onStart] for the overlay's Start button and [sheetHeader] above the tuning sliders, and a
 * deck page's [deckStep] for the overlay's step buttons.
 */
private class SpecSlot(
    val preset: NetworkGraphPreset,
    val onSpecChange: (NetworkGraphSpec) -> Unit,
    val onReseed: () -> Unit,
    val onStart: (() -> Unit)? = null,
    val sheetHeader: (@Composable () -> Unit)? = null,
    val deckStep: DeckStep? = null,
) {
    val spec: NetworkGraphSpec get() = preset.spec
}

/**
 * Steps a deck page's own pager one step back or forward, morphing its background on the way. Swiping
 * does the same, but a desktop mouse can't drag a pager. [onJump] leaps straight to the last step, or
 * back to the first from there, the way an onboarding Skip does.
 */
private class DeckStep(val canGoBack: Boolean, val canGoForward: Boolean, val onBack: () -> Unit, val onForward: () -> Unit, val onJump: () -> Unit)

/** One new seed for every spec in the list, so they stay one morph family. */
private fun SnapshotStateList<NetworkGraphPreset>.reseedTogether() {
    val seed = Random.nextInt()
    for (index in indices) this[index] = this[index].let { preset -> preset.copy(spec = preset.spec.copy(seed = seed)) }
}

@Suppress("LongParameterList") // The page is a pure function of these.
@Composable
private fun GraphPage(
    morph: NetworkGraphMorph,
    title: String,
    note: String,
    style: NetworkGraphPlaygroundStyle,
    stats: NetworkGraphFrameStats,
    layerStats: NetworkGraphLayerStats?,
    showViolations: Boolean,
    navigationIcon: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        PlaygroundAppBar(
            morph = morph,
            title = title,
            style = style,
            stats = stats,
            showViolations = showViolations,
            navigationIcon = navigationIcon,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(SpacingNormal),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(style.heroBackground, RoundedCornerShape(HeroCornerRadius))
                    .networkGraphBackground(morph, style.fill, stats, layerStats, showViolations),
            ) {
                Text(
                    text = note,
                    color = style.contentColor,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(SpacingNormal),
                )
            }
        }
    }
}

/**
 * A morph between two morph family specs at [MorphSelection.progress]. Its controls live in the
 * tuning sheet ([MorphControls]) and the overlay's Start button, so nothing covers the graph while it
 * morphs.
 */
@Suppress("LongParameterList") // The page is a pure function of these.
@Composable
private fun MorphPage(
    specs: List<NetworkGraphPreset>,
    selection: MorphSelection,
    style: NetworkGraphPlaygroundStyle,
    stats: NetworkGraphFrameStats,
    layerStats: NetworkGraphLayerStats?,
    showViolations: Boolean,
    navigationIcon: @Composable () -> Unit,
) {
    val from = specs[selection.fromIndex]
    val to = specs[selection.toIndex]
    GraphPage(
        morph = NetworkGraphMorph(from.spec, to.spec, selection.progress.value),
        title = "${from.name} → ${to.name}",
        note = "Morph · ${from.note} → ${to.note}",
        style = style,
        stats = stats,
        layerStats = layerStats,
        showViolations = showViolations,
        navigationIcon = navigationIcon,
    )
}

/** The morph page's part of the tuning sheet: both pickers and the scrub slider. */
@Composable
private fun MorphControls(specs: List<NetworkGraphPreset>, selection: MorphSelection, onScrub: (Float) -> Unit) {
    Column {
        MorphPicker(label = "From", specs = specs, selected = selection.fromIndex, onSelect = { selection.fromIndex = it })
        MorphPicker(label = "To", specs = specs, selected = selection.toIndex, onSelect = { selection.toIndex = it })
        Text(text = "Morph ${selection.progress.value.toFixed(2)}", style = MaterialTheme.typography.bodySmall)
        Slider(value = selection.progress.value, onValueChange = onScrub)
        Text(text = "Tuning the \"to\" side", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun MorphPicker(label: String, specs: List<NetworkGraphPreset>, selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(SpacingXSmall),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.CenterVertically))
        specs.forEachIndexed { index, spec ->
            FilterChip(
                selected = index == selected,
                onClick = { onSelect(index) },
                label = { Text(text = spec.name, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

/**
 * Stands in for onboarding: a full-screen [NetworkGraphPlaygroundStyle.heroBackground] whose graph
 * [Follow]s the pager, morphing from one step's spec to the next as it scrolls, scrubbing back when it
 * scrolls back, and running a timed morph when it jumps.
 * The surface button animates the graph's surface between full and [DECK_SHRUNK_HEIGHT] of the page,
 * to show a resize keeping every node in place while the shape follows the new bounds.
 */
@Composable
private fun DeckPage(
    deck: Deck,
    style: NetworkGraphPlaygroundStyle,
    stats: NetworkGraphFrameStats,
    layerStats: NetworkGraphLayerStats?,
    showViolations: Boolean,
) {
    val specs = deck.specs
    val pagerState = deck.pagerState
    var isShrunk by remember { mutableStateOf(false) }
    val heightFraction by animateFloatAsState(
        targetValue = if (isShrunk) DECK_SHRUNK_HEIGHT else 1f,
        animationSpec = tween(DECK_RESIZE_MILLIS),
        label = "deckSurfaceHeight",
    )
    val morphState = rememberNetworkGraphMorphState(specs[pagerState.currentPage].spec)
    morphState.Follow(specs.map { it.spec }) { pagerState.currentPage + pagerState.currentPageOffsetFraction }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(style.heroBackground),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(heightFraction)
                .networkGraphBackground(morphState, style.fill, stats, layerStats, showViolations),
        )
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(SpacingLarge),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(text = "Step ${page + 1} of ${specs.size}", color = style.contentColor, style = MaterialTheme.typography.headlineMedium)
                Text(text = specs[page].name, color = style.contentColor, style = MaterialTheme.typography.bodyLarge)
            }
        }
        FilledTonalButton(
            onClick = { isShrunk = !isShrunk },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(SpacingNormal),
        ) {
            Text(if (isShrunk) "Grow surface" else "Shrink surface")
        }
    }
}

private val HeroCornerRadius = 16.dp

/**
 * A `LargeFlexibleTopAppBar` with transparent colors over the style's [NetworkGraphPlaygroundStyle.barBackground].
 * The graph modifier lands on the bar's outermost node, before its inset padding, so it bleeds behind
 * the status bar for free.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlaygroundAppBar(
    morph: NetworkGraphMorph,
    title: String,
    style: NetworkGraphPlaygroundStyle,
    stats: NetworkGraphFrameStats,
    showViolations: Boolean,
    navigationIcon: @Composable () -> Unit,
) {
    LargeFlexibleTopAppBar(
        modifier = Modifier
            .background(style.barBackground)
            .networkGraphBackground(morph, style.fill, stats, showViolations = showViolations),
        title = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        subtitle = { Text(text = style.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = navigationIcon,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            navigationIconContentColor = style.contentColor,
            titleContentColor = style.contentColor,
            subtitleContentColor = style.contentColor,
        ),
    )
}

private val OverlayElevation = 8.dp
private val OverlayCornerRadius = 24.dp
private const val OVERLAY_ALPHA = 0.92f

@Suppress("LongParameterList") // One callback per overlay button.
@Composable
private fun PlaygroundOverlay(
    slot: SpecSlot,
    pageLabel: String,
    stats: NetworkGraphFrameStats,
    layerSummary: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStyleToggle: () -> Unit,
    onCopy: () -> Unit,
    onTune: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = slot.spec
    val deckStep = slot.deckStep
    val onStart = slot.onStart
    val contentColor = MaterialTheme.colorScheme.inverseOnSurface
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(OverlayCornerRadius),
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = OVERLAY_ALPHA),
        contentColor = contentColor,
        shadowElevation = OverlayElevation,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = SpacingSmall, vertical = SpacingXSmall),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous preset")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "$pageLabel · ${slot.preset.name}", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "frame ${stats.frameMillis.toFixed(1)}ms · graphs ${stats.drawMillis.toFixed(2)}ms · seed ${spec.seed}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    if (layerSummary.isNotEmpty()) {
                        Text(text = "hero shown/pruned · repaired: $layerSummary", style = MaterialTheme.typography.labelSmall)
                    }
                }
                IconButton(onClick = onNext) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next preset")
                }
            }
            Row {
                IconButton(onClick = slot.onReseed) { Icon(imageVector = Icons.Default.Shuffle, contentDescription = "Reseed layout") }
                IconButton(onClick = onStyleToggle) { Icon(imageVector = Icons.Default.Contrast, contentDescription = "Toggle surface style") }
                IconButton(onClick = onCopy) { Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy spec as Kotlin") }
                IconButton(onClick = onTune) { Icon(imageVector = Icons.Default.Tune, contentDescription = "Tune preset") }
                if (deckStep != null) {
                    IconButton(onClick = deckStep.onBack, enabled = deckStep.canGoBack) {
                        Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Previous step")
                    }
                    IconButton(onClick = deckStep.onForward, enabled = deckStep.canGoForward) {
                        Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Next step")
                    }
                    IconButton(onClick = deckStep.onJump) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.LastPage, contentDescription = "Jump to the last or first step")
                    }
                }
                if (onStart != null) {
                    FilledTonalButton(onClick = onStart, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        Text(text = "START", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
