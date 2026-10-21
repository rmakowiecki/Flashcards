package com.rossomak.flashcards.debugtools.morpheus

import androidx.compose.ui.graphics.Color
import com.rossomak.flashcards.core.ui.backdrop.BrandMorpheusGraphSpecs
import com.rossomak.flashcards.feature.auth.LoginMorpheusGraphs
import com.rossomak.flashcards.feature.study.StudyMorpheusGraphs
import com.rossomak.morpheus.MorpheusGraphFill
import com.rossomak.morpheus.MorpheusGraphPresets
import com.rossomak.morpheus.tuner.LabeledMorpheusGraphPresets
import com.rossomak.morpheus.tuner.MorpheusGraphPreset

private val BrandIndigo = Color(0xFF2A2E8F)

/**
 * The prototype screen's pages: the lib's [MorpheusGraphPresets] with Flashcards' brand applied, plus
 * the fake onboarding decks and the app's own screen graphs. New presets come from the playground's "copy spec" button, pasted here.
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

    /** The app's screen graphs, read from where the screens take them, so tuning starts from what ships. */
    val screens: List<MorpheusGraphPreset> = listOf(
        MorpheusGraphPreset(name = "Login", note = "Login", spec = LoginMorpheusGraphs.graph),
        MorpheusGraphPreset(name = "Preview", note = "Preview Study Session", spec = StudyMorpheusGraphs.preview),
        MorpheusGraphPreset(name = "Session", note = "Rated and Fast sessions", spec = StudyMorpheusGraphs.session),
        MorpheusGraphPreset(name = "Summary, counting", note = "Summary while the XP total counts up", spec = StudyMorpheusGraphs.summaryCounting),
        MorpheusGraphPreset(name = "Summary", note = "Settled Summary", spec = StudyMorpheusGraphs.summary),
        MorpheusGraphPreset(name = "Details top bar", note = "Category and Subcategory Details top bar", spec = BrandMorpheusGraphSpecs.topAppBar),
        whiteTopBar,
    )

    /** Brand indigo stands in for the theme's primary, which the tuner cannot resolve. */
    private val whiteTopBar: MorpheusGraphPreset
        get() = BrandMorpheusGraphSpecs.topAppBarOnSurface.let { spec ->
            MorpheusGraphPreset(
                name = "White top bar",
                note = "White top app bars; brand indigo standing in for the theme's primary",
                spec = spec.copy(layers = spec.layers.map { layer -> layer.copy(fill = MorpheusGraphFill.Solid(BrandIndigo)) }),
            )
        }
}
