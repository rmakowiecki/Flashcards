@file:OptIn(ExperimentalComposeUiApi::class)
@file:Suppress("MagicNumber") // The sliders' ranges are tuning values.

package com.rossomak.flashcards.web.landing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.morpheus.tuner.MorpheusGraphTuningSheetContent
import com.rossomak.morpheus.tuner.toKotlinSource
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.launch

/** Also the end-edge strip where the wheel scrolls the pane. */
val LANDING_TUNER_PANE_WIDTH: Dp = 380.dp

/** The local-only tuner: Morpheus's tuning sheet for the page on screen under the [LandingTuning] controls. */
@Composable
fun LandingTunerPane(modifier: Modifier = Modifier, tuning: LandingTuning, pageIndex: Int) {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val pageSpec = tuning.pages[pageIndex]
    Surface(modifier = modifier.width(LANDING_TUNER_PANE_WIDTH).fillMaxHeight(), color = MaterialTheme.colorScheme.surface, tonalElevation = MaterialTheme.spacing.xsmall) {
        MorpheusGraphTuningSheetContent(
            spec = pageSpec,
            showViolations = tuning.showViolations,
            onSpecChange = { edited -> tuning.editPage(pageIndex, edited) },
            onShowViolationsChange = { tuning.showViolations = it },
            header = {
                TunerHeader(
                    pageIndex = pageIndex,
                    tuning = tuning,
                    onCopy = {
                        val source = listOf(
                            pageSpec.toKotlinSource(),
                            "minMorphDuration = ${tuning.minMorphDuration.inWholeMilliseconds}.milliseconds",
                            "STOP_AT_EVERY_PAGE = ${tuning.stopAtEveryPage}",
                            "SIDE_BY_SIDE_MIN_ASPECT_RATIO = ${tuning.sideBySideMinAspectRatio}f",
                            with(tuning.reveal) {
                                "RevealMotion(duration = ${duration.inWholeMilliseconds}.milliseconds, rise = ${rise.value.roundToInt()}.dp, " +
                                    "stagger = ${stagger.inWholeMilliseconds}.milliseconds)"
                            },
                        ).joinToString(separator = "\n\n")
                        coroutineScope.launch { clipboard.setClipEntry(ClipEntry.withPlainText(source)) }
                    },
                )
            },
        )
    }
}

@Composable
private fun TunerHeader(pageIndex: Int, tuning: LandingTuning, onCopy: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.normal), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall)) {
        Text(text = "Landing tuner, page ${pageIndex + 1}", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Graph edits apply to every page, shapes only to this one. Press T to hide.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = onCopy) { Text("Copy values") }
        TunerSlider("Min morph time (ms)", tuning.minMorphDuration.inWholeMilliseconds.toFloat(), 200f..3000f) { tuning.minMorphDuration = it.roundToInt().milliseconds }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            Switch(checked = tuning.stopAtEveryPage, onCheckedChange = { tuning.stopAtEveryPage = it })
            Text(text = "One page per swipe (scroll-snap-stop: always)", style = MaterialTheme.typography.bodySmall)
        }
        TunerSlider("Side by side from aspect ratio", tuning.sideBySideMinAspectRatio, 0.5f..2.5f) { tuning.sideBySideMinAspectRatio = it }
        // Each edit copies the current reveal, so it never overwrites a newer value of another field.
        with(tuning.reveal) {
            TunerSlider("Reveal time (ms)", duration.inWholeMilliseconds.toFloat(), 100f..1500f) {
                tuning.reveal = tuning.reveal.copy(duration = it.roundToInt().milliseconds)
            }
            TunerSlider("Reveal rise (dp)", rise.value, 0f..64f) { tuning.reveal = tuning.reveal.copy(rise = it.roundToInt().dp) }
            TunerSlider("Reveal stagger (ms)", stagger.inWholeMilliseconds.toFloat(), 0f..300f) {
                tuning.reveal = tuning.reveal.copy(stagger = it.roundToInt().milliseconds)
            }
        }
    }
}

@Composable
private fun TunerSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column {
        Text(text = "$label: ${(value * ROUNDING_FACTOR).roundToInt() / ROUNDING_FACTOR}", style = MaterialTheme.typography.labelMedium)
        Slider(value = value.coerceIn(range), onValueChange = onValueChange, valueRange = range)
    }
}

private const val ROUNDING_FACTOR = 100f
