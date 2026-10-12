package com.rossomak.flashcards.feature.debug.networkgraph

import androidx.compose.ui.graphics.Color
import com.rossomak.networkgraph.NetworkGraphPresets
import com.rossomak.networkgraph.NetworkGraphSpec

private val BrandIndigo = Color(0xFF2A2E8F)

/**
 * The prototype screen's pages: the lib's [NetworkGraphPresets] with Flashcards' brand applied, plus
 * the fake onboarding decks. New presets come from the playground's "copy spec" button, pasted here.
 */
object FlashcardsNetworkGraphPresets {

    /** [NetworkGraphPresets.cornerBloom] in brand indigo on a plain surface. */
    private val cornerBloom = NetworkGraphPresets.cornerBloom.let { spec ->
        spec.copy(
            note = "${spec.note}; brand indigo on surface",
            layers = spec.layers.map { layer -> layer.copy(surfaceColor = BrandIndigo) },
        )
    }

    val all: List<NetworkGraphSpec> = NetworkGraphPresets.all.map { spec ->
        if (spec == NetworkGraphPresets.cornerBloom) cornerBloom else spec
    }

    val morphFamily: List<NetworkGraphSpec> = NetworkGraphPresets.morphFamily

    private fun morphVariantNamed(name: String) = morphFamily.first { it.name == name }

    /** The fake onboarding pager's backgrounds, one per page. */
    val onboardingPages: List<NetworkGraphSpec> = listOf(morphFamily[0], morphFamily[2], morphFamily[3])

    /**
     * The second fake onboarding pager's backgrounds: one shape splitting in two, two primitives of one
     * kind turning into two of another, two merging into one, and a corner bloom crossing the surface.
     */
    val onboardingCometPages: List<NetworkGraphSpec> = listOf(
        "Centre disc",
        "Corner pair",
        "Double ribbon",
        "Centre disc",
        "Corner bloom",
        "Bloom, bottom-start",
    ).map(::morphVariantNamed)
}
