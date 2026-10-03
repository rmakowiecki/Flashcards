package com.rossomak.flashcards.feature.debug.networkgraph

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rossomak.flashcards.core.ui.theme.spacing
import java.util.Locale

/**
 * PROTOTYPE — throwaway, see [NetworkGraphPrototypeScreen].
 *
 * Live editor for one [NetworkGraphSpec]: a layer picker (with add-back-layer / remove), the layer's
 * edge rule as a segmented row, then the curated sliders, shape weights first. Sliders for a shape
 * whose weight is zero are hidden. Edits are in-memory only; "copy spec" on the screen is how they survive.
 * [header] goes above everything else, inside the same scroll. [showViolations] is screen-wide,
 * not part of the spec: it rings any node breaking the two-edge rule.
 */
@Composable
fun NetworkGraphTuningSheetContent(
    modifier: Modifier = Modifier,
    spec: NetworkGraphSpec,
    showViolations: Boolean,
    onSpecChange: (NetworkGraphSpec) -> Unit,
    onShowViolationsChange: (Boolean) -> Unit,
    header: @Composable () -> Unit = {},
) {
    var selectedLayer by remember { mutableIntStateOf(spec.layers.lastIndex) }
    val layerIndex = selectedLayer.coerceIn(0, spec.layers.lastIndex)
    val layer = spec.layers[layerIndex]
    val onLayerChange: (NetworkGraphLayerSpec) -> Unit = { updated ->
        onSpecChange(spec.copy(layers = spec.layers.toMutableList().also { it[layerIndex] = updated }))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MaterialTheme.spacing.normal)
            .padding(bottom = MaterialTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        header()
        LayerPicker(
            layerCount = spec.layers.size,
            selectedLayer = layerIndex,
            onLayerSelect = { selectedLayer = it },
            onBackLayerAdd = {
                onSpecChange(spec.copy(layers = listOf(NetworkGraphPresets.defaultBackLayer) + spec.layers))
                selectedLayer = 0
            },
            onLayerRemove = {
                onSpecChange(spec.copy(layers = spec.layers.filterIndexed { index, _ -> index != layerIndex }))
                selectedLayer = (layerIndex - 1).coerceAtLeast(0)
            },
        )
        EnumRow(
            options = EdgeRule.entries,
            selected = layer.edgeRule,
            onSelect = { onLayerChange(layer.copy(edgeRule = it)) },
        )
        SwitchRow(
            label = "Strict triangles (no strings)",
            checked = layer.strictTriangles,
            onCheckedChange = { onLayerChange(layer.copy(strictTriangles = it)) },
        )
        SwitchRow(label = "Show violations (all layers)", checked = showViolations, onCheckedChange = onShowViolationsChange)
        layerSliders.filter { it.appliesTo(layer) }.forEach { slider ->
            SliderRow(
                label = slider.label,
                value = slider.read(layer),
                range = slider.range,
                onValueChange = { onLayerChange(slider.write(layer, it)) },
            )
        }
    }
}

@Composable
private fun LayerPicker(
    layerCount: Int,
    selectedLayer: Int,
    onLayerSelect: (Int) -> Unit,
    onBackLayerAdd: () -> Unit,
    onLayerRemove: () -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalArrangement = Arrangement.Center,
    ) {
        repeat(layerCount) { index ->
            val label = when {
                layerCount == 1 -> "Layer"
                index == 0 -> "Back"
                index == layerCount - 1 -> "Front"
                else -> "Layer ${index + 1}"
            }
            FilterChip(selected = index == selectedLayer, onClick = { onLayerSelect(index) }, label = { Text(label) })
        }
        if (layerCount < MAX_LAYERS) {
            AssistChip(
                onClick = onBackLayerAdd,
                label = { Text("Back layer") },
                leadingIcon = { Icon(imageVector = Icons.Default.Add, contentDescription = null) },
            )
        }
        if (layerCount > 1) {
            IconButton(onClick = onLayerRemove) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove selected layer")
            }
        }
    }
}

@Composable
private fun <T : Enum<T>> EnumRow(options: List<T>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(text = option.name, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column {
        Row {
            Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Text(text = "%.2f".format(Locale.US, value), style = MaterialTheme.typography.labelMedium)
        }
        Slider(value = value.coerceIn(range), onValueChange = onValueChange, valueRange = range, modifier = Modifier.padding(top = 0.dp))
    }
}

private const val MAX_LAYERS = 2

private class LayerSlider(
    val label: String,
    val range: ClosedFloatingPointRange<Float>,
    val read: (NetworkGraphLayerSpec) -> Float,
    val write: (NetworkGraphLayerSpec, Float) -> NetworkGraphLayerSpec,
    val appliesTo: (NetworkGraphLayerSpec) -> Boolean = { true },
)

private val hasRibbon: (NetworkGraphLayerSpec) -> Boolean = { it.ribbonWeight > 0f }
private val hasBloom: (NetworkGraphLayerSpec) -> Boolean = { it.bloomWeight > 0f }
private val isShaped: (NetworkGraphLayerSpec) -> Boolean = { it.uniformWeight < 1f }

@Suppress("MagicNumber")
private val layerSliders = listOf(
    LayerSlider("Uniform weight", 0f..1f, { it.uniformWeight }, { spec, value -> spec.copy(uniformWeight = value) }),
    LayerSlider("Ribbon weight", 0f..1f, { it.ribbonWeight }, { spec, value -> spec.copy(ribbonWeight = value) }),
    LayerSlider("Bloom weight", 0f..1f, { it.bloomWeight }, { spec, value -> spec.copy(bloomWeight = value) }),
    LayerSlider("Density (cells on window long side)", 4f..48f, { it.density }, { spec, value -> spec.copy(density = value) }),
    LayerSlider("Fill chance", 0.3f..1f, { it.fillChance }, { spec, value -> spec.copy(fillChance = value) }),
    LayerSlider("Jitter (cells)", 0f..0.5f, { it.jitter }, { spec, value -> spec.copy(jitter = value) }),
    LayerSlider("Drift amplitude (cells)", 0f..1.2f, { it.driftAmplitude }, { spec, value -> spec.copy(driftAmplitude = value) }),
    LayerSlider("Drift speed", 0f..4f, { it.driftSpeed }, { spec, value -> spec.copy(driftSpeed = value) }),
    LayerSlider("Ribbon center", 0f..1f, { it.ribbonCenter }, { spec, value -> spec.copy(ribbonCenter = value) }, hasRibbon),
    LayerSlider("Ribbon width", 0.05f..1f, { it.ribbonWidth }, { spec, value -> spec.copy(ribbonWidth = value) }, hasRibbon),
    LayerSlider("Ribbon meander", 0f..0.4f, { it.ribbonCurve }, { spec, value -> spec.copy(ribbonCurve = value) }, hasRibbon),
    LayerSlider("Ribbon tilt (°)", -45f..45f, { it.ribbonTilt }, { spec, value -> spec.copy(ribbonTilt = value) }, hasRibbon),
    LayerSlider("Bloom reach", 0.1f..1.5f, { it.bloomReach }, { spec, value -> spec.copy(bloomReach = value) }, hasBloom),
    LayerSlider("Bloom center x", 0f..1f, { it.bloomX }, { spec, value -> spec.copy(bloomX = value) }, hasBloom),
    LayerSlider("Bloom center y", 0f..1f, { it.bloomY }, { spec, value -> spec.copy(bloomY = value) }, hasBloom),
    LayerSlider("Outlier density", 0f..0.3f, { it.envelopeFloor }, { spec, value -> spec.copy(envelopeFloor = value) }, isShaped),
    LayerSlider("Wave amplitude", 0f..0.2f, { it.waveAmplitude }, { spec, value -> spec.copy(waveAmplitude = value) }),
    LayerSlider("Wave speed", 0f..2f, { it.waveSpeed }, { spec, value -> spec.copy(waveSpeed = value) }),
    LayerSlider("Max edge (cells)", 0.8f..5f, { it.maxEdgeFactor }, { spec, value -> spec.copy(maxEdgeFactor = value) }),
    LayerSlider("Min triangle angle (°)", 0f..40f, { it.minAngleDegrees }, { spec, value -> spec.copy(minAngleDegrees = value) }),
    LayerSlider("Edge keep chance", 0.2f..1f, { it.edgeKeepChance }, { spec, value -> spec.copy(edgeKeepChance = value) }),
    LayerSlider("Repair reach (× max edge)", 1f..3f, { it.repairReach }, { spec, value -> spec.copy(repairReach = value) }),
    LayerSlider("Stroke width (dp)", 0.3f..3f, { it.strokeWidthDp }, { spec, value -> spec.copy(strokeWidthDp = value) }),
    LayerSlider("Edge alpha", 0f..1f, { it.edgeAlpha }, { spec, value -> spec.copy(edgeAlpha = value) }),
    LayerSlider("Edge pulse depth", 0f..1f, { it.pulseDepth }, { spec, value -> spec.copy(pulseDepth = value) }),
    LayerSlider("Length falloff", 0f..1f, { it.lengthFalloff }, { spec, value -> spec.copy(lengthFalloff = value) }),
    LayerSlider("Node size (dp)", 0f..8f, { it.nodeSizeDp }, { spec, value -> spec.copy(nodeSizeDp = value) }),
    LayerSlider("Node size variance", 0f..1f, { it.nodeSizeVariance }, { spec, value -> spec.copy(nodeSizeVariance = value) }),
    LayerSlider("Hot node share", 0f..1f, { it.hotNodeShare }, { spec, value -> spec.copy(hotNodeShare = value) }),
    LayerSlider("Glow radius (dp)", 2f..48f, { it.glowRadiusDp }, { spec, value -> spec.copy(glowRadiusDp = value) }),
    LayerSlider("Glow strength", 0f..1f, { it.glowStrength }, { spec, value -> spec.copy(glowStrength = value) }),
    LayerSlider("Depth dimming", 0f..1f, { it.depthDimming }, { spec, value -> spec.copy(depthDimming = value) }),
    LayerSlider("Layer alpha", 0f..1f, { it.alpha }, { spec, value -> spec.copy(alpha = value) }),
)
