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

/** The graph family of every brand-gradient surface: one seed and one [base] layer, so any two graphs morph within one mesh. */
object BrandMorpheusGraphSpecs {

    val fill: MorpheusGraphFill = MorpheusGraphFill.Solid(Color.White)

    /** A graph on a plain surface takes the theme's primary color. */
    val onSurfaceFill: MorpheusGraphFill
        @Composable
        @ReadOnlyComposable
        get() = MorpheusGraphFill.Solid(MaterialTheme.colorScheme.primary)

    /** Floor for any morph, so a quick change never jumps. */
    val minMorphDuration: Duration = 500.milliseconds

    /** A deliberate jump, such as onboarding's Skip or the Summary settling. */
    val settleMorphDuration: Duration = 1_000.milliseconds

    private const val SEED = 29

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

    private val topAppBarBloom = Bloom(reach = 0.9f, plateau = 0.3f)

    val topAppBar: MorpheusGraphSpec = spec(topAppBarBloom)

    /** [topAppBar] dimmed for a white bar, drawn with [onSurfaceFill]. */
    val topAppBarOnSurface: MorpheusGraphSpec = spec(base.copy(shapes = listOf(topAppBarBloom), alpha = 0.22f))

    fun spec(vararg shapes: ShapePrimitive): MorpheusGraphSpec = spec(base.copy(shapes = shapes.toList()))

    /** [layer] should be a copy of [base] to stay in the family. */
    fun spec(layer: MorpheusGraphLayerSpec): MorpheusGraphSpec = MorpheusGraphSpec(seed = SEED, layers = listOf(layer))
}
