package com.rossomak.flashcards.web.landing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rossomak.flashcards.web.components.RevealMotion
import com.rossomak.morpheus.MorpheusGraphLayerSpec
import com.rossomak.morpheus.MorpheusGraphSpec
import kotlin.time.Duration

/** The values the local tuner edits live. They start at the shipped values and last until reload. */
class LandingTuning {
    var pages: List<MorpheusGraphSpec> by mutableStateOf(LandingGraphs.pages)
        private set
    var minMorphDuration: Duration by mutableStateOf(LandingTimings.minMorphDuration)
    var showViolations: Boolean by mutableStateOf(false)

    var stopAtEveryPage: Boolean by mutableStateOf(LandingTimings.STOP_AT_EVERY_PAGE)
    var sideBySideMinAspectRatio: Float by mutableStateOf(FeatureLayout.SIDE_BY_SIDE_MIN_ASPECT_RATIO)
    var reveal: RevealMotion by mutableStateOf(RevealMotion.Default)

    /** See [withPageEdit]. */
    fun editPage(pageIndex: Int, edited: MorpheusGraphSpec) {
        pages = pages.withPageEdit(pageIndex, edited)
    }
}

/**
 * Replaces page [pageIndex] with [edited]. Other pages take every changed layer field except shapes, so
 * all stay in one morph family. An added or removed layer applies to every page.
 */
fun List<MorpheusGraphSpec>.withPageEdit(pageIndex: Int, edited: MorpheusGraphSpec): List<MorpheusGraphSpec> {
    val before = this[pageIndex]
    return mapIndexed { index, spec ->
        if (index == pageIndex) edited else spec.copy(layers = spec.layers.followingEdit(before.layers, edited.layers))
    }
}

private fun List<MorpheusGraphLayerSpec>.followingEdit(before: List<MorpheusGraphLayerSpec>, edited: List<MorpheusGraphLayerSpec>): List<MorpheusGraphLayerSpec> =
    if (before.size == edited.size) {
        mapIndexed { index, layer -> layer.withChangesBetween(before[index], edited[index]) }
    } else {
        edited.mapIndexed { index, layer -> layer.copy(shapes = getOrNull(index)?.shapes ?: layer.shapes) }
    }

/** This layer with every non-shape field changed from [before] to [edited] taken over. */
private fun MorpheusGraphLayerSpec.withChangesBetween(before: MorpheusGraphLayerSpec, edited: MorpheusGraphLayerSpec): MorpheusGraphLayerSpec = this
    .takingChange(before.edgeRule, edited.edgeRule) { copy(edgeRule = it) }
    .takingChange(before.density, edited.density) { copy(density = it) }
    .takingChange(before.fillChance, edited.fillChance) { copy(fillChance = it) }
    .takingChange(before.jitter, edited.jitter) { copy(jitter = it) }
    .takingChange(before.driftAmplitude, edited.driftAmplitude) { copy(driftAmplitude = it) }
    .takingChange(before.driftSpeed, edited.driftSpeed) { copy(driftSpeed = it) }
    .takingChange(before.envelopeFloor, edited.envelopeFloor) { copy(envelopeFloor = it) }
    .takingChange(before.waveAmplitude, edited.waveAmplitude) { copy(waveAmplitude = it) }
    .takingChange(before.waveSpeed, edited.waveSpeed) { copy(waveSpeed = it) }
    .takingChange(before.maxEdgeFactor, edited.maxEdgeFactor) { copy(maxEdgeFactor = it) }
    .takingChange(before.minAngleDegrees, edited.minAngleDegrees) { copy(minAngleDegrees = it) }
    .takingChange(before.edgeKeepChance, edited.edgeKeepChance) { copy(edgeKeepChance = it) }
    .takingChange(before.repairReach, edited.repairReach) { copy(repairReach = it) }
    .takingChange(before.strictTriangles, edited.strictTriangles) { copy(strictTriangles = it) }
    .takingChange(before.strokeWidthDp, edited.strokeWidthDp) { copy(strokeWidthDp = it) }
    .takingChange(before.edgeAlpha, edited.edgeAlpha) { copy(edgeAlpha = it) }
    .takingChange(before.lengthFalloff, edited.lengthFalloff) { copy(lengthFalloff = it) }
    .takingChange(before.pulseDepth, edited.pulseDepth) { copy(pulseDepth = it) }
    .takingChange(before.nodeSizeDp, edited.nodeSizeDp) { copy(nodeSizeDp = it) }
    .takingChange(before.nodeSizeVariance, edited.nodeSizeVariance) { copy(nodeSizeVariance = it) }
    .takingChange(before.hotNodeShare, edited.hotNodeShare) { copy(hotNodeShare = it) }
    .takingChange(before.glowRadiusDp, edited.glowRadiusDp) { copy(glowRadiusDp = it) }
    .takingChange(before.glowStrength, edited.glowStrength) { copy(glowStrength = it) }
    .takingChange(before.depthDimming, edited.depthDimming) { copy(depthDimming = it) }
    .takingChange(before.comets, edited.comets) { copy(comets = it) }
    .takingChange(before.morphGap, edited.morphGap) { copy(morphGap = it) }
    .takingChange(before.alpha, edited.alpha) { copy(alpha = it) }
    .takingChange(before.fill, edited.fill) { copy(fill = it) }

private inline fun <T> MorpheusGraphLayerSpec.takingChange(before: T, edited: T, applyTo: MorpheusGraphLayerSpec.(T) -> MorpheusGraphLayerSpec): MorpheusGraphLayerSpec =
    if (before == edited) this else applyTo(edited)
