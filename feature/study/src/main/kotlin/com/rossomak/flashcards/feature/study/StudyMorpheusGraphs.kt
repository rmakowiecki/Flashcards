@file:Suppress("MagicNumber") // Spec values are tuning, pasted from the network-graph playground.

package com.rossomak.flashcards.feature.study

import com.rossomak.flashcards.core.ui.backdrop.BrandMorpheusGraphSpecs
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.ShapePrimitive.Bloom
import com.rossomak.morpheus.ShapePrimitive.Ribbon

/** Preview never morphs, so it alone keeps its own seed and motion outside the [BrandMorpheusGraphSpecs] family. */
object StudyMorpheusGraphs {

    private const val PREVIEW_SEED = 2040535497

    val preview: MorpheusGraphSpec = MorpheusGraphSpec(
        seed = PREVIEW_SEED,
        layers = listOf(
            BrandMorpheusGraphSpecs.base.copy(
                shapes = listOf(Ribbon(center = 0.5f, width = 0.497f, curve = 0.09f, tilt = 11.65f)),
                driftSpeed = 1.234f,
                waveAmplitude = 0.02f,
                waveSpeed = 0.376f,
            ),
        ),
    )

    /** Rated and Fast alike. */
    val session: MorpheusGraphSpec = BrandMorpheusGraphSpecs.spec(Bloom(x = 0.5f, y = 0f, reach = 0.7f, plateau = 0.15f))

    /** While the XP total counts up. */
    val summaryCounting: MorpheusGraphSpec = BrandMorpheusGraphSpecs.spec(Bloom(x = 0.5f, y = 0.45f, reach = 0.35f))

    val summary: MorpheusGraphSpec = BrandMorpheusGraphSpecs.spec(
        Bloom(x = 1f, y = 0f, reach = 0.6f),
        Bloom(x = 0f, y = 1f, reach = 0.6f),
    )
}
