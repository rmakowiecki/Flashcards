package com.rossomak.networkgraph

import androidx.compose.ui.geometry.Offset
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test

/** A layer's mesh is a pure function of its spec, seed and frame times: no platform or run-to-run randomness. */
class LayerFieldDeterminismTest {

    private val frameTimesSeconds = listOf(0f, 0.5f, 2.25f, 7f)

    @Test
    fun `two fields with the same spec and seed draw the same mesh on every frame for every edge rule`() {
        EdgeRule.entries.forEach { edgeRule ->
            val layer = NetworkGraphPresets.glowConstellation.layers.single().copy(edgeRule = edgeRule)

            meshFrames(layer, SEED) shouldBe meshFrames(layer, SEED)
        }
    }

    @Test
    fun `a different seed lays the nodes out differently`() {
        val layer = NetworkGraphPresets.ribbonMesh.layers.single()

        meshFrames(layer, SEED).first().homes shouldNotBe meshFrames(layer, OTHER_SEED).first().homes
    }

    private fun meshFrames(layer: NetworkGraphLayerSpec, seed: Int): List<MeshFrame> {
        val field = LayerField(layer, seed, SURFACE_HEIGHT, PX_PER_DP)
        return frameTimesSeconds.map { timeSeconds ->
            field.advance(timeSeconds, SURFACE_WIDTH, SURFACE_HEIGHT)
            MeshFrame(
                homes = (0 until field.nodeCount).map { field.nodeHome(it) },
                nodeAlphas = (0 until field.nodeCount).map { field.nodeAlpha(it) },
                edgeKeys = (0 until field.drawnEdgeCount).map { field.drawnEdgeKey(it) },
                edgeAlphas = (0 until field.drawnEdgeCount).map { field.drawnEdgeAlpha(it, timeSeconds) },
            )
        }
    }

    private data class MeshFrame(val homes: List<Offset>, val nodeAlphas: List<Float>, val edgeKeys: List<Long>, val edgeAlphas: List<Float>)

    private companion object {
        const val SEED = 4
        const val OTHER_SEED = 5
        const val PX_PER_DP = 2.75f
        const val SURFACE_WIDTH = 1080f
        const val SURFACE_HEIGHT = 1900f
    }
}
