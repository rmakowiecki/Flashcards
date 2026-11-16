@file:Suppress("MagicNumber") // Tuning values for the graph shapes.

package com.rossomak.flashcards.web.landing

import androidx.compose.ui.graphics.Color
import com.rossomak.morpheus.MAX_SHAPE_PRIMITIVES
import com.rossomak.morpheus.MorpheusGraphComets
import com.rossomak.morpheus.MorpheusGraphFill
import com.rossomak.morpheus.MorpheusGraphLayerSpec
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.ShapePrimitive.Bloom
import com.rossomak.morpheus.ShapePrimitive.Ribbon
import com.rossomak.morpheus.ShapePrimitive.Uniform

/**
 * The website's own graph family, one spec per landing page, behind every page. Each spec has the
 * same two layers, a fine, faint back mesh and a dense, bright front one, and every layer is a copy
 * of [back] or [front], so any two specs morph within the same meshes and the nodes stay put. Every
 * page composes the front from wide ribbons sweeping across the whole window and blooms anchored in
 * its corners; the text keeps legible through its own soft scrim ([textScrim]) rather than through a
 * thinner graph.
 *
 * Not the app's `BrandMorpheusGraphSpecs`, which lives in the Android-only `:core:ui`; the site
 * shares no graph with the app.
 */
object LandingGraphs {

    private const val SEED = 53

    private val fill: MorpheusGraphFill = MorpheusGraphFill.Solid(Color.White)

    /**
     * The fine mesh behind: small, dim nodes with no glow, the same shape on every page, so a page turn
     * reshapes only the front mesh. It waves and pulses gently for life.
     */
    private val back: MorpheusGraphLayerSpec = MorpheusGraphLayerSpec(
        density = 46f,
        jitter = 0.45f,
        driftAmplitude = 0.45f,
        driftSpeed = 1f,
        waveAmplitude = 0.02f,
        waveSpeed = 0.2f,
        maxEdgeFactor = 2.4f,
        minAngleDegrees = 14f,
        lengthFalloff = 0.5f,
        strokeWidthDp = 0.6f,
        edgeAlpha = 0.5f,
        pulseDepth = 0.3f,
        nodeSizeDp = 1.5f,
        nodeSizeVariance = 0.35f,
        glowStrength = 0.15f,
        depthDimming = 0.5f,
        comets = MorpheusGraphComets(spark = 0.3f),
        shapes = listOf(Uniform(weight = 0.55f), Ribbon(center = 0.5f, width = 0.8f, curve = 0.08f)),
        alpha = 0.32f,
    )

    /**
     * The mesh in front: one continuous mesh per shape, bright edges that pulse, a few hot nodes as an
     * accent, a steady wave and drift, and comets that carry the light across every morph.
     */
    private val front: MorpheusGraphLayerSpec = MorpheusGraphLayerSpec(
        density = 40f,
        jitter = 0.42f,
        driftAmplitude = 0.55f,
        driftSpeed = 1.4f,
        waveAmplitude = 0.035f,
        waveSpeed = 0.35f,
        maxEdgeFactor = 2.5f,
        minAngleDegrees = 14f,
        lengthFalloff = 0.45f,
        strokeWidthDp = 1.35f,
        edgeAlpha = 0.82f,
        pulseDepth = 0.4f,
        nodeSizeDp = 2.8f,
        nodeSizeVariance = 0.55f,
        hotNodeShare = 0.06f,
        glowRadiusDp = 16f,
        glowStrength = 0.4f,
        depthDimming = 0.25f,
        comets = MorpheusGraphComets(strength = 1.5f, size = 1.3f, spark = 1f, sizeDeviation = 0.4f, stagger = 0.3f),
        // The leaving and the arriving shape sweep the whole morph together, so the first pixels of a slow
        // scroll already grow the next page's shape instead of only fading this one.
        morphGap = -1f,
        alpha = 0.88f,
    )

    /** The hero's cover: a wide band sweeping low under the tagline, a bloom in the top-end corner and a smaller one opposite. */
    val hero: MorpheusGraphSpec = spec(
        back = back.copy(alpha = 0.38f),
        front = front.copy(
            shapes = listOf(
                Ribbon(center = 0.82f, width = 0.36f, curve = 0.12f, tilt = -7f),
                Bloom(x = 1f, y = 0f, reach = 0.46f),
                Bloom(x = 0f, y = 0.1f, reach = 0.3f),
            ),
            hotNodeShare = 0.1f,
            glowStrength = 0.5f,
            alpha = 0.95f,
        ),
    )

    /**
     * One spec per feature page, in [LandingFeature] order: each a wide ribbon or two across the whole
     * window and a bloom in a corner, at a new angle, height, curve and corner from the page before, so
     * part of every shape leaves and part arrives and the comets carry the light between them.
     */
    private val features: List<MorpheusGraphSpec> = listOf(
        // Home: a band climbing from the lower start to the upper end, a bloom in the bottom-end corner.
        spec(
            back = back,
            front = front.copy(shapes = listOf(Ribbon(center = 0.42f, width = 0.34f, curve = 0.1f, tilt = -16f), Bloom(x = 1f, y = 1f, reach = 0.5f))),
        ),
        // Search: a deep, curving band falling to the lower end, a bloom in the top-start corner.
        spec(
            back = back,
            front = front.copy(
                shapes = listOf(Ribbon(center = 0.66f, width = 0.4f, curve = 0.16f, tilt = 10f), Bloom(x = 0f, y = 0f, reach = 0.5f)),
                waveAmplitude = 0.045f,
            ),
        ),
        // Selection Mode: two bands, a wide one high and a thin one low, a bloom in the top-end corner.
        spec(
            back = back,
            front = front.copy(
                shapes = listOf(
                    Ribbon(center = 0.24f, width = 0.32f, curve = 0.08f, tilt = -8f),
                    Ribbon(center = 0.86f, width = 0.18f, curve = 0.06f, tilt = 5f),
                    Bloom(x = 1f, y = 0f, reach = 0.4f),
                ),
            ),
        ),
        // Sort and filter: one wide band at a steep angle across the middle, a bloom in the bottom-start corner.
        spec(
            back = back,
            front = front.copy(
                shapes = listOf(Ribbon(center = 0.5f, width = 0.36f, curve = 0.12f, tilt = 20f), Bloom(x = 0f, y = 1f, reach = 0.55f)),
                waveAmplitude = 0.03f,
            ),
        ),
        // Preview: bands along the top and the bottom edges framing the page, a bloom in the top-end corner.
        spec(
            back = back,
            front = front.copy(
                shapes = listOf(
                    Ribbon(center = 0.12f, width = 0.26f, curve = 0.1f, tilt = 4f),
                    Ribbon(center = 0.9f, width = 0.28f, curve = 0.1f, tilt = -4f),
                    Bloom(x = 1f, y = 0f, reach = 0.42f),
                ),
            ),
        ),
        // Rated and voice: a strongly curving band below the middle, blooms in the top-start and bottom-end corners.
        spec(
            back = back,
            front = front.copy(
                shapes = listOf(
                    Ribbon(center = 0.64f, width = 0.34f, curve = 0.24f, tilt = -5f),
                    Bloom(x = 0f, y = 0f, reach = 0.46f),
                    Bloom(x = 1f, y = 1f, reach = 0.36f),
                ),
                pulseDepth = 0.55f,
            ),
        ),
        // Fast and read-aloud: one broad band waving through the middle like sound, a bloom in the bottom-end corner.
        spec(
            back = back,
            front = front.copy(
                shapes = listOf(Ribbon(center = 0.42f, width = 0.46f, curve = 0.26f, tilt = 3f), Bloom(x = 1f, y = 1f, reach = 0.45f)),
                waveAmplitude = 0.06f,
                waveSpeed = 0.5f,
            ),
        ),
        // Session complete: a band rising high toward the end, a big bloom in the top-start corner and a lit top-end one.
        spec(
            back = back,
            front = front.copy(
                shapes = listOf(
                    Ribbon(center = 0.72f, width = 0.32f, curve = 0.1f, tilt = -14f),
                    Bloom(x = 0f, y = 0f, reach = 0.6f),
                    Bloom(x = 1f, y = 0f, reach = 0.3f),
                ),
                hotNodeShare = 0.1f,
                glowStrength = 0.5f,
            ),
        ),
    )

    /** The closing page, and the backdrop of the legal pages: one calm band along the bottom and a small bloom in the top-start corner. */
    val closing: MorpheusGraphSpec = spec(
        back = back.copy(driftSpeed = 0.7f),
        front = front.copy(
            shapes = listOf(Ribbon(center = 0.88f, width = 0.3f, curve = 0.06f, tilt = -3f), Bloom(x = 0f, y = 0f, reach = 0.32f)),
            driftSpeed = 1f,
            waveAmplitude = 0.025f,
            alpha = 0.75f,
        ),
    )

    /** One spec per landing page, in page order: the hero, each feature, the closing page. */
    val pages: List<MorpheusGraphSpec> = listOf(hero) + features + closing

    private fun spec(back: MorpheusGraphLayerSpec, front: MorpheusGraphLayerSpec): MorpheusGraphSpec {
        require(back.shapes.size <= MAX_SHAPE_PRIMITIVES && front.shapes.size <= MAX_SHAPE_PRIMITIVES)
        return MorpheusGraphSpec(seed = SEED, layers = listOf(back, front), fill = fill)
    }
}
