package com.rossomak.flashcards.presentation.debug

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rossomak.flashcards.core.designsystem.theme.FlashcardsMotion
import kotlin.math.roundToInt

private const val MAX_DURATION_MS = 2000f
private const val MAX_DELAY_MS = 1000f

private val easingOptions: List<Pair<String, Easing>> = listOf(
    "Emphasized" to FlashcardsMotion.EmphasizedEasing,
    "Standard" to FlashcardsMotion.StandardEasing,
    "Linear" to LinearEasing,
    "FastOutLinearIn" to FastOutLinearInEasing,
    "LinearOutSlowIn" to LinearOutSlowInEasing,
    "Overshoot" to CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f),
    "Anticipate" to CubicBezierEasing(0.36f, 0f, 0.66f, -0.56f),
)

/** Live harness settings: deck controls, then every [CardAnimationsDebugMotion] field. No scrim, so the card stays visible. */
@Suppress("LongMethod", "LongParameterList") // a flat list of throwaway debug controls.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CardAnimationsDebugSettingsSheet(
    motion: CardAnimationsDebugMotion,
    sheet: CardAnimationsDebugSheet,
    cardLabels: List<String>,
    currentCardIndex: Int,
    onMotionChange: (CardAnimationsDebugMotion) -> Unit,
    onSheetChange: (CardAnimationsDebugSheet) -> Unit,
    onReload: () -> Unit,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        scrimColor = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            SectionTitle("Deck")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CardAnimationsDebugSheet.entries.forEach { option ->
                    FilterChip(selected = option == sheet, onClick = { onSheetChange(option) }, label = { Text(option.label) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onReload) { Text("Reload deck") }
                OutlinedButton(onClick = { onMotionChange(CardAnimationsDebugMotion()) }) { Text("Reset all to defaults") }
            }
            cardLabels.forEachIndexed { index, label ->
                TextButton(onClick = { onJump(index) }) {
                    Text(
                        text = "${if (index == currentCardIndex) "▶ " else ""}${index + 1}. $label",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            SectionTitle("Reveal")
            MsSlider("Reveal duration", motion.revealDurationMs, MAX_DURATION_MS) { onMotionChange(motion.copy(revealDurationMs = it)) }
            EasingPicker("Reveal easing", motion.revealEasing) { onMotionChange(motion.copy(revealEasing = it)) }

            SectionTitle("Card swap")
            MsSlider("Swap duration", motion.swapDurationMs, MAX_DURATION_MS) { onMotionChange(motion.copy(swapDurationMs = it)) }
            MsSlider("Incoming stagger", motion.swapStaggerMs, MAX_DELAY_MS) { onMotionChange(motion.copy(swapStaggerMs = it)) }
            EasingPicker("Leave easing", motion.swapLeaveEasing) { onMotionChange(motion.copy(swapLeaveEasing = it)) }
            EasingPicker("Arrive easing", motion.swapArriveEasing) { onMotionChange(motion.copy(swapArriveEasing = it)) }
            SwitchRow("Scale", motion.swapScales) { onMotionChange(motion.copy(swapScales = it)) }
            FloatSlider("Outgoing scale", motion.outgoingScale, 0.5f..1.2f) { onMotionChange(motion.copy(outgoingScale = it)) }
            FloatSlider("Incoming scale", motion.incomingScale, 0.5f..1.2f) { onMotionChange(motion.copy(incomingScale = it)) }
            SwitchRow("Fade", motion.swapFades) { onMotionChange(motion.copy(swapFades = it)) }
            SwitchRow("Vertical drift", motion.swapDrifts) { onMotionChange(motion.copy(swapDrifts = it)) }
            FloatSlider("Drift (share of height)", motion.driftFraction, 0f..0.3f) { onMotionChange(motion.copy(driftFraction = it)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
    Text(text = text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SwitchRow(label: String, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = isChecked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    Slider(value = value.coerceIn(range), onValueChange = onValueChange, valueRange = range)
}

@Composable
private fun MsSlider(label: String, value: Int, max: Float, onValueChange: (Int) -> Unit) {
    LabeledSlider("$label: $value ms", value.toFloat(), 0f..max) { onValueChange(it.roundToInt()) }
}

@Composable
private fun FloatSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    LabeledSlider("$label: ${"%.2f".format(value)}", value, range) { onValueChange((it * 100).roundToInt() / 100f) }
}

@Composable
private fun EasingPicker(label: String, selected: Easing, onSelect: (Easing) -> Unit) {
    Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        easingOptions.forEach { (name, easing) ->
            FilterChip(selected = easing == selected, onClick = { onSelect(easing) }, label = { Text(name) })
        }
    }
}
