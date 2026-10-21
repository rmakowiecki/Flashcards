package com.rossomak.flashcards.core.ui.composables.bars

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.rossomak.flashcards.core.ui.backdrop.BrandMorpheusGraphSpecs
import com.rossomak.morpheus.MorpheusGraphFill
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.morpheusGraphBackground
import com.rossomak.morpheus.rememberMorpheusGraphMorphState

/** Gone before fully collapsed, so the collapsed bar shows no faint remnant. */
private const val GRAPH_GONE_AT_COLLAPSED_FRACTION = 0.6f

/**
 * A network graph backdrop. It stays and is clipped as the bar collapses, or with [fadesOnCollapse]
 * morphs toward [BrandMorpheusGraphSpecs.hidden], read in a snapshot flow so a scroll never recomposes.
 */
@Composable
fun FlashcardsTopAppBarBackdropScope.FlashcardsTopAppBarGraph(
    spec: MorpheusGraphSpec,
    fill: MorpheusGraphFill,
    modifier: Modifier = Modifier,
    fadesOnCollapse: Boolean = false,
) {
    if (fadesOnCollapse) {
        val graphState = rememberMorpheusGraphMorphState(spec)
        LaunchedEffect(graphState, spec) {
            val hiddenGraph = BrandMorpheusGraphSpecs.hidden(spec)
            snapshotFlow { (collapsedFraction() / GRAPH_GONE_AT_COLLAPSED_FRACTION).coerceIn(0f, 1f) }
                .collect { fadeFraction -> graphState.seekTo(hiddenGraph, fadeFraction) }
        }
        Box(modifier = modifier.fillMaxSize().morpheusGraphBackground(graphState, fill))
    } else {
        Box(modifier = modifier.fillMaxSize().morpheusGraphBackground(spec, fill))
    }
}
