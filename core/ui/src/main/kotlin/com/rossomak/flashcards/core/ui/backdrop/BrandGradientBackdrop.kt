package com.rossomak.flashcards.core.ui.backdrop

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.rossomak.flashcards.core.designsystem.theme.brandColors
import com.rossomak.morpheus.MorpheusGraphMorphState
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.morpheusGraphBackground
import com.rossomak.morpheus.rememberMorpheusGraphMorphState
import kotlin.time.Duration

/**
 * The brand gradient as a [blurBackdropSource]; the first child of a `Box` carrying [blurBackdropScope].
 *
 * With a [graph] (a [BrandMorpheusGraphSpecs] spec), the network graph is drawn over the gradient, inside
 * the source, so translucent components blur it with the gradient. A changed [graph] morphs from the
 * screen as it is over [graphMorphDuration]; the first composition shows it at once. [graphAlpha] fades
 * the graph alone, read in the draw phase.
 */
@Composable
fun BoxScope.BrandGradientBackdrop(
    modifier: Modifier = Modifier,
    graph: MorpheusGraphSpec? = null,
    graphMorphDuration: Duration = BrandMorpheusGraphSpecs.minMorphDuration,
    graphAlpha: () -> Float = { 1f },
) {
    if (graph == null) {
        Box(modifier = brandGradientSource(modifier))
        return
    }
    val graphState = rememberMorpheusGraphMorphState(graph)
    val currentMorphDuration by rememberUpdatedState(graphMorphDuration)
    LaunchedEffect(graph) {
        graphState.animateTo(graph, tween(currentMorphDuration.inWholeMilliseconds.toInt(), easing = FastOutSlowInEasing))
    }
    BrandGradientBackdrop(graphState = graphState, modifier = modifier, graphAlpha = graphAlpha)
}

/** [BrandGradientBackdrop] with a graph driven by its caller, for a screen that seeks or follows the morph itself. */
@Composable
fun BoxScope.BrandGradientBackdrop(graphState: MorpheusGraphMorphState, modifier: Modifier = Modifier, graphAlpha: () -> Float = { 1f }) {
    Box(modifier = brandGradientSource(modifier)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = graphAlpha() }
                .morpheusGraphBackground(graphState, BrandMorpheusGraphSpecs.fill),
        )
    }
}

@Composable
private fun BoxScope.brandGradientSource(modifier: Modifier): Modifier = modifier
    .matchParentSize()
    .blurBackdropSource()
    .background(MaterialTheme.brandColors.screenGradient)
