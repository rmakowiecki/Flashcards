@file:Suppress("MagicNumber") // Tuning values from the Morpheus playground.

package com.rossomak.flashcards.presentation.splash

import com.rossomak.flashcards.core.ui.backdrop.BrandMorpheusGraphSpecs
import com.rossomak.flashcards.core.ui.backdrop.LaunchBackdropStart
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.ShapePrimitive.Bloom

/** The splash bloom on [BrandMorpheusGraphSpecs.cover]. Nothing is drawn outside it, so the rotation never shows the graph's edge. */
object SplashMorpheusGraphs {

    private val base = BrandMorpheusGraphSpecs.cover.copy(envelopeFloor = 0f)

    private fun bloomOf(reach: Float): MorpheusGraphSpec =
        BrandMorpheusGraphSpecs.spec(base.copy(shapes = listOf(Bloom(x = 0.5f, y = 0.5f, reach = reach, plateau = 0.6f))))

    val seed: MorpheusGraphSpec = bloomOf(reach = 0.14f)

    val bloom: MorpheusGraphSpec = bloomOf(reach = 0.31f)

    val backdropStart = LaunchBackdropStart(spec = seed, isGradientHidden = true, graphScale = 0.8f, graphRotationDegrees = -30f)
}
