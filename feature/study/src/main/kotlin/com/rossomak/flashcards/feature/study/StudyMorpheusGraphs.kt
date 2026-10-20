@file:Suppress("MagicNumber") // Spec values are tuning, pasted from the network-graph playground.

package com.rossomak.flashcards.feature.study

import com.rossomak.flashcards.core.ui.backdrop.BrandMorpheusGraphSpecs
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.ShapePrimitive.Bloom
import com.rossomak.morpheus.ShapePrimitive.Ribbon

/**
 * The network graphs behind the study screens, each placed where its screen has the least content. The
 * session and Summary graphs are [BrandMorpheusGraphSpecs] family members, so the Summary's morph stays
 * within one mesh. Preview never morphs, so its graph keeps a seed and motion of its own.
 */
object StudyMorpheusGraphs {

    private const val PREVIEW_SEED = 2040535497

    /** One wide, wavy band through the middle of the screen, sloping down toward the end side. */
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

    /** Rated and Fast alike: a wide bloom from the top centre, behind the header and thinning out over the card. */
    val session: MorpheusGraphSpec = BrandMorpheusGraphSpecs.spec(Bloom(x = 0.5f, y = 0f, reach = 0.7f, plateau = 0.15f))

    /** The Summary while the XP total counts up, around it. */
    val summaryCounting: MorpheusGraphSpec = BrandMorpheusGraphSpecs.spec(Bloom(x = 0.5f, y = 0.45f, reach = 0.35f))

    /** The settled Summary: two opposite corners, clear of the headline, pills and Level card. */
    val summary: MorpheusGraphSpec = BrandMorpheusGraphSpecs.spec(
        Bloom(x = 1f, y = 0f, reach = 0.6f),
        Bloom(x = 0f, y = 1f, reach = 0.6f),
    )
}
