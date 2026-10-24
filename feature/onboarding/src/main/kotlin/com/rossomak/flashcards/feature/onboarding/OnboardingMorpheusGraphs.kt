@file:Suppress("MagicNumber") // Spec values are tuning, pasted from the network-graph playground.

package com.rossomak.flashcards.feature.onboarding

import com.rossomak.flashcards.core.ui.backdrop.BrandMorpheusGraphSpecs
import com.rossomak.flashcards.feature.onboarding.OnboardingStep.AllSet
import com.rossomak.flashcards.feature.onboarding.OnboardingStep.DailyGoal
import com.rossomak.flashcards.feature.onboarding.OnboardingStep.Favorites
import com.rossomak.flashcards.feature.onboarding.OnboardingStep.Mastery
import com.rossomak.flashcards.feature.onboarding.OnboardingStep.SessionModes
import com.rossomak.flashcards.feature.onboarding.OnboardingStep.Structure
import com.rossomak.flashcards.feature.onboarding.OnboardingStep.VoicePrivacy
import com.rossomak.flashcards.feature.onboarding.OnboardingStep.Welcome
import com.rossomak.morpheus.MorpheusGraphLayerSpec
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.ShapePrimitive.Bloom
import com.rossomak.morpheus.ShapePrimitive.Ribbon
import com.rossomak.morpheus.ShapePrimitive.Uniform

/** One graph per onboarding step, all [BrandMorpheusGraphSpecs] family members so any two morph in place. */
object OnboardingMorpheusGraphs {

    private val base = BrandMorpheusGraphSpecs.base

    private fun spec(layer: MorpheusGraphLayerSpec) = BrandMorpheusGraphSpecs.spec(layer)

    fun specFor(step: OnboardingStep): MorpheusGraphSpec = when (step) {
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
    val pages: List<MorpheusGraphSpec> = OnboardingStep.entries.map(::specFor)
}
