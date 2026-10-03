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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import com.rossomak.flashcards.feature.debug.networkgraph.ShapePrimitive.Bloom
import com.rossomak.flashcards.feature.debug.networkgraph.ShapePrimitive.Ribbon
import com.rossomak.flashcards.feature.debug.networkgraph.ShapePrimitive.Uniform
import java.util.Locale

/**
 * PROTOTYPE — throwaway, see [NetworkGraphPrototypeScreen].
 *
 * Live editor for one [NetworkGraphSpec]: a layer picker (with add-back-layer / remove), the layer's
 * edge rule as a segmented row, its shape as one collapsible card per [ShapePrimitive] (add, remove,
 * up to [MAX_SHAPE_PRIMITIVES]), then the curated sliders. Edits are in-memory only; "copy spec" on the
 * screen is how they survive.
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
        ShapeEditor(shapes = layer.shapes, onShapesChange = { onLayerChange(layer.copy(shapes = it)) })
        SwitchRow(
            label = "Comet spark (comet nodes heat up)",
            checked = layer.cometSpark,
            onCheckedChange = { onLayerChange(layer.copy(cometSpark = it)) },
        )
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

/** One card per primitive, only one expanded at a time, then a chip per kind to add one more. */
@Composable
private fun ShapeEditor(shapes: List<ShapePrimitive>, onShapesChange: (List<ShapePrimitive>) -> Unit) {
    var expanded by remember { mutableIntStateOf(-1) }
    Text(text = "Shape", style = MaterialTheme.typography.labelLarge)
    shapes.forEachIndexed { index, primitive ->
        ShapeCard(
            label = "${primitive.kindName} ${shapes.take(index + 1).count { it::class == primitive::class }}",
            primitive = primitive,
            isExpanded = index == expanded,
            canRemove = shapes.size > 1,
            onExpandToggle = { expanded = if (expanded == index) -1 else index },
            onPrimitiveChange = { updated -> onShapesChange(shapes.toMutableList().also { it[index] = updated }) },
            onRemove = {
                onShapesChange(shapes.filterIndexed { other, _ -> other != index })
                expanded = -1
            },
        )
    }
    if (shapes.size < MAX_SHAPE_PRIMITIVES) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            newPrimitives.forEach { primitive ->
                AssistChip(
                    onClick = {
                        onShapesChange(shapes + primitive)
                        expanded = shapes.size
                    },
                    label = { Text(primitive.kindName) },
                    leadingIcon = { Icon(imageVector = Icons.Default.Add, contentDescription = null) },
                )
            }
        }
    }
}

@Suppress("LongParameterList") // Prototype plumbing: one card's state and its three actions.
@Composable
private fun ShapeCard(
    label: String,
    primitive: ShapePrimitive,
    isExpanded: Boolean,
    canRemove: Boolean,
    onExpandToggle: () -> Unit,
    onPrimitiveChange: (ShapePrimitive) -> Unit,
    onRemove: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$label · weight %.2f".format(Locale.US, primitive.weight),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                IconButton(onClick = onExpandToggle) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse $label" else "Expand $label",
                    )
                }
                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove $label")
                    }
                }
            }
            if (isExpanded) {
                primitiveSliders(primitive).forEach { slider ->
                    SliderRow(
                        label = slider.label,
                        value = slider.read(primitive),
                        range = slider.range,
                        onValueChange = { onPrimitiveChange(slider.write(primitive, it)) },
                    )
                }
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

private val isShaped: (NetworkGraphLayerSpec) -> Boolean = { layer -> layer.shapes.none { it is Uniform && it.weight >= 1f } }

private val ShapePrimitive.kindName: String
    get() = when (this) {
        is Uniform -> "Uniform"
        is Ribbon -> "Ribbon"
        is Bloom -> "Bloom"
    }

/** What each add chip inserts: a centered bloom rather than another corner one, so it shows up at once. */
private val newPrimitives: List<ShapePrimitive> = listOf(
    Uniform(weight = 0.3f),
    Ribbon(),
    Bloom(x = 0.5f, y = 0.5f, reach = 0.3f),
)

private class PrimitiveSlider<T : ShapePrimitive>(
    val label: String,
    val range: ClosedFloatingPointRange<Float>,
    val read: (T) -> Float,
    val write: (T, Float) -> T,
)

@Suppress("UNCHECKED_CAST", "MagicNumber") // Each list is only ever paired with its own kind.
private fun primitiveSliders(primitive: ShapePrimitive): List<PrimitiveSlider<ShapePrimitive>> = when (primitive) {
    is Uniform -> listOf<PrimitiveSlider<Uniform>>(
        PrimitiveSlider("Weight", 0f..1f, { it.weight }, { shape, value -> shape.copy(weight = value) }),
    )
    is Ribbon -> listOf<PrimitiveSlider<Ribbon>>(
        PrimitiveSlider("Weight", 0f..1f, { it.weight }, { shape, value -> shape.copy(weight = value) }),
        PrimitiveSlider("Center", 0f..1f, { it.center }, { shape, value -> shape.copy(center = value) }),
        PrimitiveSlider("Width", 0.05f..1f, { it.width }, { shape, value -> shape.copy(width = value) }),
        PrimitiveSlider("Meander", 0f..0.4f, { it.curve }, { shape, value -> shape.copy(curve = value) }),
        PrimitiveSlider("Tilt (°)", -45f..45f, { it.tilt }, { shape, value -> shape.copy(tilt = value) }),
    )
    is Bloom -> listOf<PrimitiveSlider<Bloom>>(
        PrimitiveSlider("Weight", 0f..1f, { it.weight }, { shape, value -> shape.copy(weight = value) }),
        PrimitiveSlider("Reach", 0.1f..1.5f, { it.reach }, { shape, value -> shape.copy(reach = value) }),
        PrimitiveSlider("Plateau (0 soft, 1 disc)", 0f..0.95f, { it.plateau }, { shape, value -> shape.copy(plateau = value) }),
        PrimitiveSlider("Center x", 0f..1f, { it.x }, { shape, value -> shape.copy(x = value) }),
        PrimitiveSlider("Center y", 0f..1f, { it.y }, { shape, value -> shape.copy(y = value) }),
    )
} as List<PrimitiveSlider<ShapePrimitive>>

@Suppress("MagicNumber")
private val layerSliders = listOf(
    LayerSlider("Density (cells on window long side)", 4f..48f, { it.density }, { spec, value -> spec.copy(density = value) }),
    LayerSlider("Fill chance", 0.3f..1f, { it.fillChance }, { spec, value -> spec.copy(fillChance = value) }),
    LayerSlider("Jitter (cells)", 0f..0.5f, { it.jitter }, { spec, value -> spec.copy(jitter = value) }),
    LayerSlider("Drift amplitude (cells)", 0f..1.2f, { it.driftAmplitude }, { spec, value -> spec.copy(driftAmplitude = value) }),
    LayerSlider("Drift speed", 0f..4f, { it.driftSpeed }, { spec, value -> spec.copy(driftSpeed = value) }),
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
    LayerSlider("Comet strength", 0f..2f, { it.cometStrength }, { spec, value -> spec.copy(cometStrength = value) }),
    LayerSlider("Layer alpha", 0f..1f, { it.alpha }, { spec, value -> spec.copy(alpha = value) }),
)
