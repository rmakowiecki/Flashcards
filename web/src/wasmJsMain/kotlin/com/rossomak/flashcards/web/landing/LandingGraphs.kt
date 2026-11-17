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
 * The site's own graph family, one spec per landing page. Every layer copies [back] or [front], so all
 * specs share one morph family and nodes stay put. Separate from the app's `BrandMorpheusGraphSpecs`.
 */
object LandingGraphs {

    private const val SEED = 53

    private val fill: MorpheusGraphFill = MorpheusGraphFill.Solid(Color.White)

    /** The faint back mesh, the same shape on every page. */
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
        // No comets: a morph only fades one shape out and the next one in.
        comets = MorpheusGraphComets(strength = 0f),
        shapes = listOf(Uniform(weight = 0.55f), Ribbon(center = 0.5f, width = 0.8f, curve = 0.08f)),
        alpha = 0.32f,
    )

    /** The bright front mesh, whose shapes change per page. */
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
        comets = MorpheusGraphComets(strength = 0f),
        // Both shapes sweep the whole morph, so a slow scroll grows the next shape from its first pixels.
        morphGap = -1f,
        alpha = 0.61f,
    )

    /** The hero's brighter cover. */
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
            alpha = 0.67f,
        ),
    )

    /** One spec per feature, in [LandingFeature] order, each differing from the one before. */
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

    /** The closing page's and legal pages' calm band. */
    val closing: MorpheusGraphSpec = spec(
        back = back.copy(driftSpeed = 0.7f),
        front = front.copy(
            shapes = listOf(Ribbon(center = 0.88f, width = 0.3f, curve = 0.06f, tilt = -3f)),
            driftSpeed = 1f,
            waveAmplitude = 0.025f,
            alpha = 0.53f,
        ),
    )

    /** One spec per landing page, in page order. */
    val pages: List<MorpheusGraphSpec> = listOf(hero) + features + closing

    private fun spec(back: MorpheusGraphLayerSpec, front: MorpheusGraphLayerSpec): MorpheusGraphSpec {
        require(back.shapes.size <= MAX_SHAPE_PRIMITIVES && front.shapes.size <= MAX_SHAPE_PRIMITIVES)
        return MorpheusGraphSpec(seed = SEED, layers = listOf(back, front), fill = fill)
    }
}
