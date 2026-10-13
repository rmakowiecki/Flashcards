package com.rossomak.networkgraph.tuner

import androidx.compose.runtime.Immutable
import com.rossomak.networkgraph.NetworkGraphPresets
import com.rossomak.networkgraph.NetworkGraphSpec

/**
 * A spec with the [name] and [note] the [NetworkGraphPlayground] shows for it. Specs carry no labels of
 * their own, so a tuning tool keeps them alongside.
 */
@Immutable
public data class NetworkGraphPreset(
    val name: String,
    val note: String,
    val spec: NetworkGraphSpec,
)

/** The lib's [NetworkGraphPresets], labeled for the playground. */
public object LabeledNetworkGraphPresets {

    public val all: List<NetworkGraphPreset> = with(NetworkGraphPresets) {
        listOf(
            NetworkGraphPreset("Baseline", "Uniform field, proximity edges, depth, pulse", baseline),
            NetworkGraphPreset("Ribbon mesh", "Wavy triangulated band, fixed topology, single layer", ribbonMesh),
            NetworkGraphPreset("Ribbon + ghost", "Ribbon over a faint, sparser, slower far mesh", ribbonPlusGhost),
            NetworkGraphPreset("Glow constellation", "Ribbon with a hot node subset haloed; long edges fade", glowConstellation),
            NetworkGraphPreset("Corner bloom", "Dense at the top-end corner, thinning out; built for app bars", cornerBloom),
            NetworkGraphPreset("Depth fog", "Uniform single mesh, per-node depth dims and shrinks far nodes", depthFog),
            NetworkGraphPreset("Living Delaunay", "Ribbon re-triangulated every frame; flipped edges fade in and out", livingDelaunay),
        )
    }

    public val morphFamily: List<NetworkGraphPreset> = with(NetworkGraphPresets.MorphFamily) {
        listOf(
            NetworkGraphPreset("Uniform field", "Even mesh over the whole surface", uniformField),
            NetworkGraphPreset("Ribbon", "Band across the lower middle", ribbon),
            NetworkGraphPreset("Ribbon, high and narrow", "The band moved up, thinned and tilted", highNarrowRibbon),
            NetworkGraphPreset("Corner bloom", "Dense at the top-end corner, hot nodes glowing", cornerBloom),
            NetworkGraphPreset("Bloom, bottom-start", "The bloom moved to the opposite corner and tightened", bottomStartBloom),
            NetworkGraphPreset("Ribbon + bloom", "Both shapes at once", ribbonAndBloom),
            NetworkGraphPreset("Uniform + ghost", "Even mesh over a faint, sparser back layer", uniformWithGhost),
            NetworkGraphPreset("Centre disc", "A hard-edged disc in the middle of the surface", centreDisc),
            NetworkGraphPreset("Corner pair", "Blooms in the top-end and bottom-start corners at once", cornerPair),
            NetworkGraphPreset("Double ribbon", "Two thin bands, one high, one low", doubleRibbon),
        )
    }
}
