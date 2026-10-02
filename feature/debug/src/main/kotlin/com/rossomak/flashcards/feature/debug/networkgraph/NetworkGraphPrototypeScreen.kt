package com.rossomak.flashcards.feature.debug.networkgraph

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.rossomak.flashcards.core.common.logd
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.brandColors
import com.rossomak.flashcards.core.ui.theme.spacing
import kotlin.random.Random
import kotlinx.coroutines.launch

/**
 * PROTOTYPE, throwaway — answers "which animated network-graph background looks best, and on which
 * surfaces". Delete the whole `networkgraph` package and the two routing hooks in
 * [com.rossomak.flashcards.feature.debug.DebugScreen] / `MainScreenDebugTabs` once a winner is
 * promoted.
 *
 * One [NetworkGraphPresets] variant per pager page, each shown on the two aspect ratios a winner has
 * to hold up on: a wide, short top app bar strip and a tall hero. Registered on the app's outer graph,
 * so it runs full-screen without the bottom bar. The bottom overlay reseeds the page's
 * layout, flips the [FlashcardsComponentStyle] for every page, copies the page's current spec as
 * Kotlin (clipboard + logcat), and opens the tuning sheet, which edits the page's spec live. Edits
 * live in memory for this screen's lifetime only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkGraphPrototypeScreen(modifier: Modifier = Modifier, onNavigateBack: () -> Unit) {
    val specs = remember { NetworkGraphPresets.all.toMutableStateList() }
    var styleIndex by remember { mutableIntStateOf(0) }
    val style = FlashcardsComponentStyle.entries[styleIndex]
    var isTuning by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { specs.size })
    val stats = remember { NetworkGraphFrameStats() }
    val layerStats = remember { NetworkGraphLayerStats() }
    var showViolations by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        var previousNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { nowNanos ->
                stats.recordFrame(nowNanos - previousNanos)
                previousNanos = nowNanos
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, userScrollEnabled = !isTuning, modifier = Modifier.fillMaxSize()) { page ->
            PresetPage(
                spec = specs[page],
                style = style,
                stats = stats,
                // Only the settled page reports counts, or a neighbour mid-swipe would overwrite them.
                layerStats = layerStats.takeIf { page == pagerState.currentPage },
                showViolations = showViolations,
                onNavigateBack = onNavigateBack,
            )
        }

        val currentPage = pagerState.currentPage
        PresetOverlay(
            spec = specs[currentPage],
            pageLabel = "${currentPage + 1}/${specs.size}",
            stats = stats,
            layerSummary = layerStats.summary,
            onPrevious = { coroutineScope.launch { pagerState.animateScrollToPage((currentPage - 1 + specs.size) % specs.size) } },
            onNext = { coroutineScope.launch { pagerState.animateScrollToPage((currentPage + 1) % specs.size) } },
            onReseed = { specs[currentPage] = specs[currentPage].copy(seed = Random.nextInt()) },
            onStyleToggle = { styleIndex = (styleIndex + 1) % FlashcardsComponentStyle.entries.size },
            onCopy = {
                val source = specs[currentPage].toKotlinSource()
                logd { "Network graph spec:\n$source" }
                coroutineScope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("NetworkGraphSpec", source))) }
            },
            onTune = { isTuning = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(MaterialTheme.spacing.normal)
                .windowInsetsPadding(WindowInsets.navigationBars),
        )
    }

    if (isTuning) {
        ModalBottomSheet(
            onDismissRequest = { isTuning = false },
            // No scrim: the whole point of tuning is watching the graph change above the sheet.
            scrimColor = Color.Transparent,
        ) {
            val page = pagerState.currentPage
            NetworkGraphTuningSheetContent(
                spec = specs[page],
                showViolations = showViolations,
                onSpecChange = { specs[page] = it },
                onShowViolationsChange = { showViolations = it },
            )
        }
    }
}

@Suppress("LongParameterList") // Prototype plumbing; the page is a pure function of these.
@Composable
private fun PresetPage(
    spec: NetworkGraphSpec,
    style: FlashcardsComponentStyle,
    stats: NetworkGraphFrameStats,
    layerStats: NetworkGraphLayerStats?,
    showViolations: Boolean,
    onNavigateBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        NetworkGraphAppBarDemo(spec = spec, style = style, stats = stats, showViolations = showViolations, onNavigateBack = onNavigateBack)
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
                    .networkGraphBackground(spec, style, stats, layerStats, showViolations),
            ) {
                Text(
                    text = spec.note,
                    color = contentColorFor(style),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(MaterialTheme.spacing.normal),
                )
            }
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
    spec: NetworkGraphSpec,
    style: FlashcardsComponentStyle,
    stats: NetworkGraphFrameStats,
    showViolations: Boolean,
    onNavigateBack: () -> Unit,
) {
    val contentColor = contentColorFor(style)
    LargeFlexibleTopAppBar(
        modifier = Modifier
            .background(cardBackgroundFor(style))
            .networkGraphBackground(spec, style, stats, showViolations = showViolations),
        title = { Text(text = spec.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
