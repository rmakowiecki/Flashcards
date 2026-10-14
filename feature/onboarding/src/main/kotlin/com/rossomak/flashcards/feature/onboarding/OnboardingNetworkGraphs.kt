@file:Suppress("MagicNumber") // Spec values are tuning, pasted from the network-graph playground.

package com.rossomak.flashcards.feature.onboarding

import androidx.compose.ui.graphics.Color
import com.rossomak.flashcards.feature.onboarding.OnboardingStep.AllSet
import com.rossomak.flashcards.feature.onboarding.OnboardingStep.DailyGoal
import com.rossomak.flashcards.feature.onboarding.OnboardingStep.Favorites
import com.rossomak.flashcards.feature.onboarding.OnboardingStep.Mastery
import com.rossomak.flashcards.feature.onboarding.OnboardingStep.SessionModes
import com.rossomak.flashcards.feature.onboarding.OnboardingStep.Structure
import com.rossomak.flashcards.feature.onboarding.OnboardingStep.VoicePrivacy
import com.rossomak.flashcards.feature.onboarding.OnboardingStep.Welcome
import com.rossomak.networkgraph.NetworkGraphFill
import com.rossomak.networkgraph.NetworkGraphLayerSpec
import com.rossomak.networkgraph.NetworkGraphSpec
import com.rossomak.networkgraph.ShapePrimitive.Bloom
import com.rossomak.networkgraph.ShapePrimitive.Ribbon
import com.rossomak.networkgraph.ShapePrimitive.Uniform
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The network graph behind each onboarding step. Every spec is one layer of one morph family (same
 * seed, density, jitter and fixed strict triangulation), so swiping between any two steps, Skip's jump
 * included, morphs one mesh in place: only the shape, motion, glow and alpha differ. None sets a fill:
 * all of them inherit [fill].
 */
object OnboardingNetworkGraphs {

    /** Every onboarding graph is white; the steps differ only in how dim their layer is. */
    val fill: NetworkGraphFill = NetworkGraphFill.Solid(Color.White)

    /** The least a step-to-step morph takes, so a quick flick or a Next tap still morphs instead of jumping. */
    val minMorphDuration: Duration = 500.milliseconds

    private const val SEED = 29

    /** The morph family every step shares, dense and dimmed. */
    private val base = NetworkGraphLayerSpec(
        density = 26f,
        jitter = 0.42f,
        driftAmplitude = 0.35f,
        maxEdgeFactor = 2.4f,
        minAngleDegrees = 14f,
        lengthFalloff = 0.5f,
        strokeWidthDp = 0.7f,
        edgeAlpha = 0.5f,
        nodeSizeDp = 2f,
        nodeSizeVariance = 0.4f,
        glowStrength = 0.3f,
        cometSpark = 0.6f,
        alpha = 0.35f,
    )

    private fun spec(layer: NetworkGraphLayerSpec) = NetworkGraphSpec(seed = SEED, layers = listOf(layer))

    fun specFor(step: OnboardingStep): NetworkGraphSpec = when (step) {
        Welcome -> spec(
            base.copy(
                shapes = listOf(Bloom(x = 0.5f, y = 0.38f, reach = 0.42f, plateau = 0.35f)),
                hotNodeShare = 0.15f,
                glowStrength = 0.45f,
                alpha = 0.45f,
            ),
        )
        Structure -> spec(
            base.copy(
                shapes = listOf(
                    Ribbon(center = 0.22f, width = 0.18f, curve = 0.06f, tilt = 6f),
                    Ribbon(center = 0.8f, width = 0.2f, curve = 0.06f, tilt = -6f),
                ),
                waveAmplitude = 0.02f,
            ),
        )
        Mastery -> spec(base.copy(shapes = listOf(Bloom(reach = 0.7f)), hotNodeShare = 0.3f, glowRadiusDp = 18f, glowStrength = 0.5f, alpha = 0.4f))
        SessionModes -> spec(base.copy(shapes = listOf(Ribbon(center = 0.7f, width = 0.35f, tilt = -10f)), waveAmplitude = 0.04f, waveSpeed = 0.35f))
        DailyGoal -> spec(base.copy(shapes = listOf(Bloom(x = 0f, y = 1f, reach = 0.65f)), hotNodeShare = 0.1f))
        VoicePrivacy -> spec(
            base.copy(
                shapes = listOf(Ribbon(center = 0.3f, width = 0.22f, curve = 0.14f)),
                waveAmplitude = 0.05f,
                waveSpeed = 0.5f,
                pulseDepth = 0.6f,
                alpha = 0.3f,
            ),
        )
        Favorites -> spec(base.copy(shapes = listOf(Bloom(reach = 0.5f), Bloom(x = 0f, y = 1f, reach = 0.5f)), hotNodeShare = 0.2f))
        AllSet -> spec(base.copy(shapes = listOf(Uniform()), hotNodeShare = 0.2f, glowStrength = 0.4f, driftSpeed = 1.3f, alpha = 0.4f))
    }

    /** One spec per pager page, in page order. */
    val pages: List<NetworkGraphSpec> = OnboardingStep.entries.map(::specFor)

    /** [step]'s spec with every layer invisible, which the entrance fades in from. */
    fun hiddenFor(step: OnboardingStep): NetworkGraphSpec = specFor(step).let { spec -> spec.copy(layers = spec.layers.map { it.copy(alpha = 0f) }) }
}
