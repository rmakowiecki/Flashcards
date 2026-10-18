package com.rossomak.flashcards.debugtools.morpheus

import androidx.compose.ui.graphics.Color
import com.rossomak.morpheus.MorpheusGraphFill
import com.rossomak.morpheus.MorpheusGraphPresets
import com.rossomak.morpheus.tuner.LabeledMorpheusGraphPresets
import com.rossomak.morpheus.tuner.MorpheusGraphPreset

private val BrandIndigo = Color(0xFF2A2E8F)

/**
 * The prototype screen's pages: the lib's [MorpheusGraphPresets] with Flashcards' brand applied, plus
 * the fake onboarding decks. New presets come from the playground's "copy spec" button, pasted here.
 */
object FlashcardsMorpheusGraphPresets {

    /** [MorpheusGraphPresets.cornerBloom] in brand indigo on a plain surface. */
    private val cornerBloom = LabeledMorpheusGraphPresets.all.first { it.spec == MorpheusGraphPresets.cornerBloom }.let { preset ->
        preset.copy(
            note = "${preset.note}; brand indigo on surface",
            spec = preset.spec.copy(layers = preset.spec.layers.map { layer -> layer.copy(fill = MorpheusGraphFill.Solid(BrandIndigo)) }),
        )
    }

    val all: List<MorpheusGraphPreset> = LabeledMorpheusGraphPresets.all.map { preset ->
        if (preset.spec == MorpheusGraphPresets.cornerBloom) cornerBloom else preset
    }

    val morphFamily: List<MorpheusGraphPreset> = LabeledMorpheusGraphPresets.morphFamily

    private fun morphVariantNamed(name: String) = morphFamily.first { it.name == name }

    /** The fake onboarding pager's backgrounds, one per page. */
    val onboardingPages: List<MorpheusGraphPreset> = listOf(morphFamily[0], morphFamily[2], morphFamily[3])

    /**
     * The second fake onboarding pager's backgrounds: one shape splitting in two, two primitives of one
     * kind turning into two of another, two merging into one, and a corner bloom crossing the surface.
     */
    val onboardingCometPages: List<MorpheusGraphPreset> = listOf(
        "Centre disc",
        "Corner pair",
        "Double ribbon",
        "Centre disc",
        "Corner bloom",
        "Bloom, bottom-start",
    ).map(::morphVariantNamed)
}
