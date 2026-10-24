@file:Suppress("MagicNumber") // Spec values are tuning, pasted from the network-graph playground.

package com.rossomak.flashcards.presentation.splash

import com.rossomak.flashcards.core.ui.backdrop.BrandMorpheusGraphSpecs
import com.rossomak.flashcards.core.ui.backdrop.LaunchBackdropStart
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.ShapePrimitive.Bloom

/**
 * The splash bloom, centered on the logo. Its look matches the onboarding cover's graph, so the handoff
 * to it only moves and widens the bloom. Nothing is drawn outside the bloom, so the rotation the splash
 * screen applies never shows the graph's edge.
 */
object SplashMorpheusGraphs {

    private val base = BrandMorpheusGraphSpecs.base.copy(
        envelopeFloor = 0f,
        hotNodeShare = 0.15f,
        glowStrength = 0.45f,
        alpha = 0.45f,
    )

    private fun bloomOf(reach: Float): MorpheusGraphSpec =
        BrandMorpheusGraphSpecs.spec(base.copy(shapes = listOf(Bloom(x = 0.5f, y = 0.5f, reach = reach, plateau = 0.6f))))

    /** The tight bloom the splash opens from, drawn invisible until it fades in. */
    val seed: MorpheusGraphSpec = bloomOf(reach = 0.14f)

    /** The open bloom the splash settles on and hands over. */
    val bloom: MorpheusGraphSpec = bloomOf(reach = 0.31f)

    /**
     * Where the launch backdrop starts when Splash brings it: the gradient still to slide in, and the
     * [seed] zoomed out and turned back, for the bloom to zoom in and turn into place.
     */
    val backdropStart = LaunchBackdropStart(spec = seed, isGradientHidden = true, graphScale = 0.8f, graphRotationDegrees = -30f)
}
