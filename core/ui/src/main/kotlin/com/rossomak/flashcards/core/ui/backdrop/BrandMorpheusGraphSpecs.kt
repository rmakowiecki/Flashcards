@file:Suppress("MagicNumber") // Spec values are tuning, pasted from the network-graph playground.

package com.rossomak.flashcards.core.ui.backdrop

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.rossomak.morpheus.MorpheusGraphFill
import com.rossomak.morpheus.MorpheusGraphLayerSpec
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.ShapePrimitive
import com.rossomak.morpheus.ShapePrimitive.Bloom
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The one network-graph family the app draws: one seed and one [base] layer, so any two of the app's
 * graphs morph within one mesh and a screen differs from the next only in where its nodes sit. A
 * screen keeps its graph calm by placing its shapes away from its content, not by a dimmer layer of
 * its own.
 */
object BrandMorpheusGraphSpecs {

    /** Every graph on a brand gradient is white. */
    val fill: MorpheusGraphFill = MorpheusGraphFill.Solid(Color.White)

    /** A graph on a plain surface takes the theme's primary color. */
    val onSurfaceFill: MorpheusGraphFill
        @Composable
        @ReadOnlyComposable
        get() = MorpheusGraphFill.Solid(MaterialTheme.colorScheme.primary)

    /** The least a morph between two brand graphs takes, so a quick change still morphs instead of jumping. */
    val minMorphDuration: Duration = 500.milliseconds

    /** The morph of a deliberate jump between two brand graphs, such as onboarding's Skip or the Summary settling. */
    val settleMorphDuration: Duration = 1_000.milliseconds

    private const val SEED = 29

    /** The layer every brand graph shares: dense and dimmed, a few haloed nodes, far nodes fading for depth. */
    val base: MorpheusGraphLayerSpec = MorpheusGraphLayerSpec(
        density = 26f,
        jitter = 0.42f,
        driftAmplitude = 0.45f,
        driftSpeed = 1.2f,
        maxEdgeFactor = 2.4f,
        minAngleDegrees = 14f,
        lengthFalloff = 0.5f,
        strokeWidthDp = 0.7f,
        edgeAlpha = 0.5f,
        nodeSizeDp = 2f,
        nodeSizeVariance = 0.4f,
        hotNodeShare = 0.06f,
        glowStrength = 0.3f,
        depthDimming = 0.4f,
        cometSpark = 0.6f,
        alpha = 0.42f,
    )

    private val TopAppBarBloom = Bloom(reach = 0.9f, plateau = 0.3f)

    /** A gradient top app bar's graph, from the top-end corner across most of the bar. */
    val topAppBar: MorpheusGraphSpec = spec(TopAppBarBloom)

    /** [topAppBar] dimmed for a white top app bar, drawn with [onSurfaceFill]. */
    val topAppBarOnSurface: MorpheusGraphSpec = spec(base.copy(shapes = listOf(TopAppBarBloom), alpha = 0.22f))

    /** [base] in the shape of [shapes]. */
    fun spec(vararg shapes: ShapePrimitive): MorpheusGraphSpec = spec(base.copy(shapes = shapes.toList()))

    /** [layer] as a one-layer brand graph; [layer] should be a copy of [base] to stay in the family. */
    fun spec(layer: MorpheusGraphLayerSpec): MorpheusGraphSpec = MorpheusGraphSpec(seed = SEED, layers = listOf(layer))

    /** [spec] with every layer invisible, for a graph to fade in from or out to. */
    fun hidden(spec: MorpheusGraphSpec): MorpheusGraphSpec = spec.copy(layers = spec.layers.map { it.copy(alpha = 0f) })
}
