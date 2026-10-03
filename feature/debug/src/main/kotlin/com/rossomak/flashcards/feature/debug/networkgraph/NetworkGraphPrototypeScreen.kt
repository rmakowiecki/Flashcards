package com.rossomak.flashcards.feature.debug.networkgraph

import android.content.ClipData
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.rossomak.flashcards.core.common.logd
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.brandColors
import com.rossomak.flashcards.core.ui.theme.spacing
import java.util.Locale
import kotlin.math.floor
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * PROTOTYPE, throwaway — answers "which animated network-graph background looks best, and on which
 * surfaces". Delete the whole `networkgraph` package and the two routing hooks in
 * [com.rossomak.flashcards.feature.debug.DebugScreen] / `MainScreenDebugTabs` once a winner is
 * promoted.
 *
 * One [NetworkGraphPresets] variant per pager page, each shown on the two aspect ratios a winner has
 * to hold up on: a wide, short top app bar strip and a tall hero. Two more pages follow the presets:
 * a morph page that scrubs or times a morph between two [NetworkGraphPresets.morphFamily] variants,
 * and a fake onboarding pager whose background morphs with its scroll position and whose surface can
 * shrink and grow. Registered on the app's outer graph, so it runs full-screen without the bottom bar.
 *
 * The bottom overlay, laid out under the pages rather than over them, reseeds the page's layout,
 * flips the [FlashcardsComponentStyle] for every page, copies the page's current spec as Kotlin
 * (clipboard + logcat), and opens the tuning sheet, which edits that spec live: the morph's "to" side
 * on the morph page, the settled step on the onboarding page. Edits live in memory for this screen's
 * lifetime only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkGraphPrototypeScreen(modifier: Modifier = Modifier, onNavigateBack: () -> Unit) {
    val specs = remember { NetworkGraphPresets.all.toMutableStateList() }
    val morphSpecs = remember { NetworkGraphPresets.morphFamily.toMutableStateList() }
    val onboardingSpecs = remember { NetworkGraphPresets.onboardingPages.toMutableStateList() }
    val morphSelection = remember { MorphSelection() }
    var style by remember { mutableStateOf(FlashcardsComponentStyle.entries.first()) }
    var isTuning by remember { mutableStateOf(false) }
    val pageCount = specs.size + EXTRA_PAGES
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val onboardingPagerState = rememberPagerState(pageCount = { onboardingSpecs.size })
    val stats = remember { NetworkGraphFrameStats() }
    val layerStats = remember { NetworkGraphLayerStats() }
    var showViolations by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    RecordFrameIntervals(stats)
    val currentPage = pagerState.currentPage
    val slot = specSlotFor(currentPage, specs, morphSpecs, morphSelection, onboardingSpecs, onboardingPagerState.currentPage, coroutineScope)

    // The overlay sits under the pager rather than over it, so it never hides a page's content.
    Column(modifier = modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, userScrollEnabled = !isTuning, modifier = Modifier.weight(1f)) { page ->
            val pageLayerStats = layerStats.takeIf { page == pagerState.currentPage } // A neighbour mid-swipe must not overwrite the counts.
            when {
                page < specs.size -> GraphPage(
                    morph = NetworkGraphMorph(specs[page], specs[page], 0f),
                    title = specs[page].name,
                    note = specs[page].note,
                    style = style,
                    stats = stats,
                    layerStats = pageLayerStats,
                    showViolations = showViolations,
                    onNavigateBack = onNavigateBack,
                )
                page == specs.size -> MorphPage(
                    specs = morphSpecs,
                    selection = morphSelection,
                    style = style,
                    stats = stats,
                    layerStats = pageLayerStats,
                    showViolations = showViolations,
                    onNavigateBack = onNavigateBack,
                )
                else -> OnboardingPage(
                    specs = onboardingSpecs,
                    pagerState = onboardingPagerState,
                    stats = stats,
                    layerStats = pageLayerStats,
                    showViolations = showViolations,
                )
            }
        }

        PresetOverlay(
            spec = slot.spec,
            pageLabel = "${currentPage + 1}/$pageCount",
            stats = stats,
            layerSummary = layerStats.summary,
            onPrevious = { coroutineScope.launch { pagerState.animateScrollToPage((currentPage - 1 + pageCount) % pageCount) } },
            onNext = { coroutineScope.launch { pagerState.animateScrollToPage((currentPage + 1) % pageCount) } },
            onReseed = slot.onReseed,
            onStart = slot.onStart,
            onStyleToggle = { style = FlashcardsComponentStyle.entries[(style.ordinal + 1) % FlashcardsComponentStyle.entries.size] },
            onCopy = { coroutineScope.launch { copySpec(slot.spec, clipboard) } },
            onTune = { isTuning = true },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(MaterialTheme.spacing.normal)
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

@Suppress("LongParameterList") // Prototype plumbing: every list a page's spec can live in.
private fun specSlotFor(
    page: Int,
    specs: SnapshotStateList<NetworkGraphSpec>,
    morphSpecs: SnapshotStateList<NetworkGraphSpec>,
    morphSelection: MorphSelection,
    onboardingSpecs: SnapshotStateList<NetworkGraphSpec>,
    onboardingPage: Int,
    coroutineScope: CoroutineScope,
): SpecSlot = when {
    page < specs.size -> SpecSlot(
        spec = specs[page],
        onSpecChange = { specs[page] = it },
        onReseed = { specs[page] = specs[page].copy(seed = Random.nextInt()) },
    )
    page == specs.size -> SpecSlot(
        spec = morphSpecs[morphSelection.toIndex],
        onSpecChange = { morphSpecs[morphSelection.toIndex] = it },
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
    else -> SpecSlot(
        spec = onboardingSpecs[onboardingPage],
        onSpecChange = { onboardingSpecs[onboardingPage] = it },
        onReseed = { onboardingSpecs.reseedTogether() },
    )
}

/** Copies [spec] as Kotlin source to the clipboard and logcat, ready to paste into [NetworkGraphPresets]. */
private suspend fun copySpec(spec: NetworkGraphSpec, clipboard: Clipboard) {
    val source = spec.toKotlinSource()
    logd { "Network graph spec:\n$source" }
    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("NetworkGraphSpec", source)))
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

/** The morph page and the onboarding page, after the presets. */
private const val EXTRA_PAGES = 2

/** The morph page starts on uniform field → corner bloom. */
private const val MORPH_DEFAULT_TO_INDEX = 3

/** How far the onboarding page's surface shrinks, as a share of the page's height. */
private const val ONBOARDING_SHRUNK_HEIGHT = 0.45f
private const val ONBOARDING_RESIZE_MILLIS = 900

/** How long Start takes to morph from one end to the other. */
private const val MORPH_PLAY_MILLIS = 1_200
private const val MORPH_HALFWAY = 0.5f

/**
 * Which two [NetworkGraphPresets.morphFamily] variants the morph page morphs between, and how far:
 * [progress] runs from 0 (from) to 1 (to), scrubbed by the tuning sheet's slider and played end to
 * end by the overlay's Start button.
 */
@Stable
private class MorphSelection {
    var fromIndex by mutableIntStateOf(0)
    var toIndex by mutableIntStateOf(MORPH_DEFAULT_TO_INDEX)
    val progress = Animatable(0f)

    /** Morphs to whichever end is further away; a scrub stops it. */
    suspend fun play() {
        progress.animateTo(if (progress.value < MORPH_HALFWAY) 1f else 0f, tween(MORPH_PLAY_MILLIS, easing = FastOutSlowInEasing))
    }

    suspend fun scrubTo(fraction: Float) = progress.snapTo(fraction)
}

/**
 * The spec the overlay and the tuning sheet act on for the current page, plus the morph page's
 * extras: [onStart] for the overlay's Start button and [sheetHeader] above the tuning sliders.
 */
private class SpecSlot(
    val spec: NetworkGraphSpec,
    val onSpecChange: (NetworkGraphSpec) -> Unit,
    val onReseed: () -> Unit,
    val onStart: (() -> Unit)? = null,
    val sheetHeader: (@Composable () -> Unit)? = null,
)

/** One new seed for every spec in the list, so they stay one morph family. */
private fun SnapshotStateList<NetworkGraphSpec>.reseedTogether() {
    val seed = Random.nextInt()
    for (index in indices) this[index] = this[index].copy(seed = seed)
}

@Suppress("LongParameterList") // Prototype plumbing; the page is a pure function of these.
@Composable
private fun GraphPage(
    morph: NetworkGraphMorph,
    title: String,
    note: String,
    style: FlashcardsComponentStyle,
    stats: NetworkGraphFrameStats,
    layerStats: NetworkGraphLayerStats?,
    showViolations: Boolean,
    onNavigateBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        NetworkGraphAppBarDemo(
            morph = morph,
            title = title,
            style = style,
            stats = stats,
            showViolations = showViolations,
            onNavigateBack = onNavigateBack,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(MaterialTheme.spacing.normal),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(heroBackgroundFor(style), RoundedCornerShape(HeroCornerRadius))
                    .networkGraphBackground(morph, style, stats, layerStats, showViolations),
            ) {
                Text(
                    text = note,
                    color = contentColorFor(style),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(MaterialTheme.spacing.normal),
                )
            }
        }
    }
}

/**
 * A morph between two [NetworkGraphPresets.morphFamily] variants at [MorphSelection.progress]. Its
 * controls live in the tuning sheet ([MorphControls]) and the overlay's Start button, so nothing
 * covers the graph while it morphs.
 */
@Suppress("LongParameterList") // Prototype plumbing; the page is a pure function of these.
@Composable
private fun MorphPage(
    specs: List<NetworkGraphSpec>,
    selection: MorphSelection,
    style: FlashcardsComponentStyle,
    stats: NetworkGraphFrameStats,
    layerStats: NetworkGraphLayerStats?,
    showViolations: Boolean,
    onNavigateBack: () -> Unit,
) {
    val from = specs[selection.fromIndex]
    val to = specs[selection.toIndex]
    GraphPage(
        morph = NetworkGraphMorph(from, to, selection.progress.value),
        title = "${from.name} → ${to.name}",
        note = "Morph · ${from.note} → ${to.note}",
        style = style,
        stats = stats,
        layerStats = layerStats,
        showViolations = showViolations,
        onNavigateBack = onNavigateBack,
    )
}

/** The morph page's part of the tuning sheet: both pickers and the scrub slider. The "to" picker offers only variants "from" morphs into smoothly. */
@Composable
private fun MorphControls(specs: List<NetworkGraphSpec>, selection: MorphSelection, onScrub: (Float) -> Unit) {
    val from = specs[selection.fromIndex]
    Column {
        MorphPicker(label = "From", specs = specs, selected = selection.fromIndex, isEnabled = { true }, onSelect = { selection.fromIndex = it })
        MorphPicker(label = "To", specs = specs, selected = selection.toIndex, isEnabled = { from.morphsSmoothlyInto(specs[it]) }, onSelect = { selection.toIndex = it })
        Text(text = "Morph %.2f".format(Locale.US, selection.progress.value), style = MaterialTheme.typography.bodySmall)
        Slider(value = selection.progress.value, onValueChange = onScrub)
        Text(text = "Tuning the \"to\" side", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun MorphPicker(label: String, specs: List<NetworkGraphSpec>, selected: Int, isEnabled: (Int) -> Boolean, onSelect: (Int) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.CenterVertically))
        specs.forEachIndexed { index, spec ->
            FilterChip(
                selected = index == selected,
                enabled = isEnabled(index),
                onClick = { onSelect(index) },
                label = { Text(text = spec.name, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

/**
 * Stands in for onboarding: a full-screen gradient whose background morphs from one step's spec to
 * the next as the pager scrolls, scrubbing back when it scrolls back. The surface button animates the
 * graph's surface between full and [ONBOARDING_SHRUNK_HEIGHT] of the page, to show a resize keeping
 * every node in place while the shape follows the new bounds.
 */
@Composable
private fun OnboardingPage(
    specs: List<NetworkGraphSpec>,
    pagerState: PagerState,
    stats: NetworkGraphFrameStats,
    layerStats: NetworkGraphLayerStats?,
    showViolations: Boolean,
) {
    var isShrunk by remember { mutableStateOf(false) }
    val heightFraction by animateFloatAsState(
        targetValue = if (isShrunk) ONBOARDING_SHRUNK_HEIGHT else 1f,
        animationSpec = tween(ONBOARDING_RESIZE_MILLIS),
        label = "onboardingSurfaceHeight",
    )
    val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
    val lower = floor(position).toInt().coerceIn(0, specs.lastIndex)
    val upper = (lower + 1).coerceAtMost(specs.lastIndex)
    val morph = NetworkGraphMorph(specs[lower], specs[upper], (position - lower).coerceIn(0f, 1f))
    val contentColor = MaterialTheme.brandColors.onGradientContent

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.brandColors.screenGradient),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(heightFraction)
                .networkGraphBackground(morph, FlashcardsComponentStyle.OnGradient, stats, layerStats, showViolations),
        )
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(MaterialTheme.spacing.large),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(text = "Step ${page + 1} of ${specs.size}", color = contentColor, style = MaterialTheme.typography.headlineMedium)
                Text(text = specs[page].name, color = contentColor, style = MaterialTheme.typography.bodyLarge)
            }
        }
        FilledTonalButton(
            onClick = { isShrunk = !isShrunk },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(MaterialTheme.spacing.normal),
        ) {
            Text(if (isShrunk) "Grow surface" else "Shrink surface")
        }
    }
}

private val HeroCornerRadius = 16.dp

/** The app bar's background: [FlashcardsComponentStyle.OnGradient]'s brand top-bar gradient; [FlashcardsComponentStyle.OnSurface]'s plain themed surface. */
@Composable
private fun cardBackgroundFor(style: FlashcardsComponentStyle) = when (style) {
    FlashcardsComponentStyle.OnSurface -> SolidColor(MaterialTheme.colorScheme.surfaceContainerHigh)
    FlashcardsComponentStyle.OnGradient -> MaterialTheme.brandColors.topBarGradient
}

/** The hero stands in for a full-screen surface, so it gets the full-screen gradient. */
@Composable
private fun heroBackgroundFor(style: FlashcardsComponentStyle) = when (style) {
    FlashcardsComponentStyle.OnSurface -> SolidColor(MaterialTheme.colorScheme.surfaceContainerHigh)
    FlashcardsComponentStyle.OnGradient -> MaterialTheme.brandColors.screenGradient
}

@Composable
private fun contentColorFor(style: FlashcardsComponentStyle) = when (style) {
    FlashcardsComponentStyle.OnSurface -> MaterialTheme.colorScheme.onSurface
    FlashcardsComponentStyle.OnGradient -> MaterialTheme.brandColors.onGradientContent
}

private fun styleLabel(style: FlashcardsComponentStyle) = when (style) {
    FlashcardsComponentStyle.OnSurface -> "On surface"
    FlashcardsComponentStyle.OnGradient -> "On gradient"
}

/**
 * Built the same way [com.rossomak.flashcards.core.ui.composables.bars.FlashcardsTopAppBar] is
 * (`LargeFlexibleTopAppBar` + transparent colors) rather than being that component, whose gradient is
 * hardcoded. The graph modifier lands on the bar's outermost node, before its inset padding, so it
 * bleeds behind the status bar for free.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkGraphAppBarDemo(
    morph: NetworkGraphMorph,
    title: String,
    style: FlashcardsComponentStyle,
    stats: NetworkGraphFrameStats,
    showViolations: Boolean,
    onNavigateBack: () -> Unit,
) {
    val contentColor = contentColorFor(style)
    LargeFlexibleTopAppBar(
        modifier = Modifier
            .background(cardBackgroundFor(style))
            .networkGraphBackground(morph, style, stats, showViolations = showViolations),
        title = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        subtitle = { Text(text = styleLabel(style), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = contentColor)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            navigationIconContentColor = contentColor,
            titleContentColor = contentColor,
            subtitleContentColor = contentColor,
        ),
    )
}

private val OverlayElevation = 8.dp
private val OverlayCornerRadius = 24.dp

@Suppress("LongParameterList") // One callback per overlay button; a prototype is not worth an event type.
@Composable
private fun PresetOverlay(
    spec: NetworkGraphSpec,
    pageLabel: String,
    stats: NetworkGraphFrameStats,
    layerSummary: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReseed: () -> Unit,
    onStart: (() -> Unit)?,
    onStyleToggle: () -> Unit,
    onCopy: () -> Unit,
    onTune: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = MaterialTheme.colorScheme.inverseOnSurface
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(OverlayCornerRadius),
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f),
        contentColor = contentColor,
        shadowElevation = OverlayElevation,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.xsmall),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous preset")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "$pageLabel · ${spec.name}", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "frame %.1fms · graphs %.2fms · seed %d".format(stats.frameMillis, stats.drawMillis, spec.seed),
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
                IconButton(onClick = onReseed) { Icon(imageVector = Icons.Default.Shuffle, contentDescription = "Reseed layout") }
                IconButton(onClick = onStyleToggle) { Icon(imageVector = Icons.Default.Contrast, contentDescription = "Toggle surface style") }
                IconButton(onClick = onCopy) { Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy spec as Kotlin") }
                IconButton(onClick = onTune) { Icon(imageVector = Icons.Default.Tune, contentDescription = "Tune preset") }
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

@PreviewLightDark
@Composable
private fun NetworkGraphPrototypeScreenPreview() {
    FlashcardsTheme {
        Surface {
            NetworkGraphPrototypeScreen(onNavigateBack = {})
        }
    }
}
