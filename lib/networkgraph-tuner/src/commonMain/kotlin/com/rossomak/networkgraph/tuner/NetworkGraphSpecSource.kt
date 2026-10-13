package com.rossomak.networkgraph.tuner

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.rossomak.networkgraph.NetworkGraphLayerSpec
import com.rossomak.networkgraph.NetworkGraphPresets
import com.rossomak.networkgraph.NetworkGraphSpec
import com.rossomak.networkgraph.ShapePrimitive

// Each primitive's constructor defaults, read off a default instance so they never drift from the lib.
private val UniformDefaults = ShapePrimitive.Uniform()
private val RibbonDefaults = ShapePrimitive.Ribbon()
private val BloomDefaults = ShapePrimitive.Bloom()

/**
 * The spec as Kotlin source in the style of [NetworkGraphPresets], ready to paste into a preset list. Lists
 * only the layer fields that differ from the defaults.
 */
public fun NetworkGraphSpec.toKotlinSource(): String {
    val defaults = NetworkGraphLayerSpec().sourceArguments()
    val layerSources = layers.joinToString(separator = ",\n") { layer ->
        val arguments = layer.sourceArguments().filter { (name, value) -> defaults[name] != value }
        if (arguments.isEmpty()) {
            "        NetworkGraphLayerSpec()"
        } else {
            arguments.entries.joinToString(
                separator = ",\n",
                prefix = "        NetworkGraphLayerSpec(\n",
                postfix = ",\n        )",
            ) { (name, value) -> "            $name = $value" }
        }
    }
    return """
        |NetworkGraphSpec(
        |    seed = $seed,
        |    layers = listOf(
        |$layerSources,
        |    ),
        |)
    """.trimMargin()
}

private fun NetworkGraphLayerSpec.sourceArguments(): Map<String, String> = linkedMapOf(
    "shapes" to shapes.joinToString(separator = ", ", prefix = "listOf(", postfix = ")") { it.toSource() },
    "edgeRule" to "EdgeRule.$edgeRule",
    "density" to density.toSource(),
    "fillChance" to fillChance.toSource(),
    "jitter" to jitter.toSource(),
    "driftAmplitude" to driftAmplitude.toSource(),
    "driftSpeed" to driftSpeed.toSource(),
    "envelopeFloor" to envelopeFloor.toSource(),
    "waveAmplitude" to waveAmplitude.toSource(),
    "waveSpeed" to waveSpeed.toSource(),
    "maxEdgeFactor" to maxEdgeFactor.toSource(),
    "minAngleDegrees" to minAngleDegrees.toSource(),
    "edgeKeepChance" to edgeKeepChance.toSource(),
    "repairReach" to repairReach.toSource(),
    "strictTriangles" to strictTriangles.toString(),
    "strokeWidthDp" to strokeWidthDp.toSource(),
    "edgeAlpha" to edgeAlpha.toSource(),
    "lengthFalloff" to lengthFalloff.toSource(),
    "pulseDepth" to pulseDepth.toSource(),
    "nodeSizeDp" to nodeSizeDp.toSource(),
    "nodeSizeVariance" to nodeSizeVariance.toSource(),
    "hotNodeShare" to hotNodeShare.toSource(),
    "glowRadiusDp" to glowRadiusDp.toSource(),
    "glowStrength" to glowStrength.toSource(),
    "depthDimming" to depthDimming.toSource(),
    "cometStrength" to cometStrength.toSource(),
    "cometSpark" to cometSpark.toString(),
    "alpha" to alpha.toSource(),
    "surfaceColor" to (surfaceColor?.let { color -> "Color(0x${color.toArgbHex()})" } ?: "null"),
)

/** The primitive as a constructor call, listing only the arguments that differ from its defaults. */
private fun ShapePrimitive.toSource(): String {
    val arguments = when (this) {
        is ShapePrimitive.Uniform -> listOf("weight" to (weight to UniformDefaults.weight))
        is ShapePrimitive.Ribbon -> listOf(
            "weight" to (weight to RibbonDefaults.weight),
            "center" to (center to RibbonDefaults.center),
            "width" to (width to RibbonDefaults.width),
            "curve" to (curve to RibbonDefaults.curve),
            "tilt" to (tilt to RibbonDefaults.tilt),
        )
        is ShapePrimitive.Bloom -> listOf(
            "weight" to (weight to BloomDefaults.weight),
            "x" to (x to BloomDefaults.x),
            "y" to (y to BloomDefaults.y),
            "reach" to (reach to BloomDefaults.reach),
            "plateau" to (plateau to BloomDefaults.plateau),
        )
    }
    val changed = arguments.filter { (_, values) -> values.first != values.second }
    return changed.joinToString(separator = ", ", prefix = "ShapePrimitive.${this::class.simpleName}(", postfix = ")") { (name, values) ->
        "$name = ${values.first.toSource()}"
    }
}

/** A Kotlin float literal rounded to three decimals, trailing zeros trimmed: `0.5f`, `2.0f`. */
internal fun Float.toSource(): String = toFixed(SOURCE_DECIMALS).trimEnd('0').let { trimmed ->
    if (trimmed.endsWith('.')) "${trimmed}0f" else "${trimmed}f"
}

/** The color's ARGB as eight uppercase hex digits, alpha first: `FF2A2E8F`. */
internal fun Color.toArgbHex(): String = toArgb().toUInt().toString(HEX_RADIX).uppercase().padStart(ARGB_HEX_DIGITS, '0')

private const val SOURCE_DECIMALS = 3
private const val HEX_RADIX = 16
private const val ARGB_HEX_DIGITS = 8
