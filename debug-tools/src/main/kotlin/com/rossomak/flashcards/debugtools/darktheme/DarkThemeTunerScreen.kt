package com.rossomak.flashcards.debugtools.darktheme

import android.content.ClipData
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import com.rossomak.flashcards.core.designsystem.theme.FlashcardsColors
import com.rossomak.flashcards.core.designsystem.theme.brandColors
import com.rossomak.flashcards.core.designsystem.theme.code
import com.rossomak.flashcards.core.designsystem.theme.cornerRadius
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.core.ui.backdrop.blurBackdropScope
import com.rossomak.flashcards.core.ui.backdrop.blurBackdropSource
import com.rossomak.flashcards.core.ui.composables.FlashcardsMetadataBadge
import com.rossomak.flashcards.core.ui.composables.banners.FlashcardsInfoBanner
import com.rossomak.flashcards.core.ui.composables.banners.FlashcardsInfoBannerTone
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsGradientTopBar
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledButton
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsTextButton
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsTonalButton
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsListItemPosition
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsSelectableListRow
import com.rossomak.flashcards.core.ui.composables.lists.flashcardsListGroupContainer
import com.rossomak.flashcards.core.ui.composables.lists.flashcardsListItemShape
import com.rossomak.flashcards.core.ui.composables.progress.FlashcardsLinearProgressBar
import com.rossomak.flashcards.core.ui.composables.rating.FlashcardsRatingButtonRow
import com.rossomak.flashcards.debugtools.R
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Live sliders for the dark theme's colors. Values apply app-wide (dark mode only) until process
 * death, so set them here, open the real screens to judge them, then copy the values into the theme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DarkThemeTunerScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit,
) {
    val knobs = DarkThemeTunerState.knobs
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            FlashcardsGradientTopBar(
                title = stringResource(R.string.dark_theme_tuner_title),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.debug_back_cd),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            TunerGradientPreview()
            TunerListPreview()
            TunerSurfaceBannersPreview()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.spacing.normal),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.none),
            ) {
                Text(
                    text = stringResource(R.string.dark_theme_tuner_dark_only_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                KnobSection(R.string.dark_theme_tuner_general_section_title) {
                    Knob(R.string.dark_theme_tuner_hue_label, knobs.hue, HUE_RANGE) { updateKnobs { copy(hue = it) } }
                    Knob(R.string.dark_theme_tuner_chroma_label, knobs.chroma, CHROMA_RANGE) { updateKnobs { copy(chroma = it) } }
                    Knob(R.string.dark_theme_tuner_background_tone_label, knobs.backgroundTone, TONE_RANGE) { updateKnobs { copy(backgroundTone = it) } }
                    Knob(R.string.dark_theme_tuner_card_tone_label, knobs.cardTone, TONE_RANGE) { updateKnobs { copy(cardTone = it) } }
                    Knob(R.string.dark_theme_tuner_sheet_tone_label, knobs.sheetTone, TONE_RANGE) { updateKnobs { copy(sheetTone = it) } }
                    Knob(R.string.dark_theme_tuner_dialog_tone_label, knobs.dialogTone, TONE_RANGE) { updateKnobs { copy(dialogTone = it) } }
                    Knob(R.string.dark_theme_tuner_tonal_tone_label, knobs.tonalButtonTone, TONE_RANGE) { updateKnobs { copy(tonalButtonTone = it) } }
                    Knob(R.string.dark_theme_tuner_filled_tone_label, knobs.filledButtonTone, TONE_RANGE) { updateKnobs { copy(filledButtonTone = it) } }
                    FilledButtonContrast(DarkThemeTunerState.colors)
                    Knob(R.string.dark_theme_tuner_glass_tone_label, knobs.glassTone, TONE_RANGE) { updateKnobs { copy(glassTone = it) } }
                    Knob(R.string.dark_theme_tuner_outline_tone_label, knobs.outlineVariantTone, OUTLINE_TONE_RANGE) { updateKnobs { copy(outlineVariantTone = it) } }
                    Knob(R.string.dark_theme_tuner_glass_alpha_label, knobs.glassAlpha, UNIT_RANGE) { updateKnobs { copy(glassAlpha = it) } }
                    Knob(R.string.dark_theme_tuner_glass_edge_label, knobs.glassEdgeAlpha, EDGE_ALPHA_RANGE) { updateKnobs { copy(glassEdgeAlpha = it) } }
                    Knob(R.string.dark_theme_tuner_list_tint_label, knobs.listTintAlpha, LIST_TINT_RANGE) { updateKnobs { copy(listTintAlpha = it) } }
                }
                KnobSection(R.string.dark_theme_tuner_semantic_section_title) {
                    Knob(R.string.dark_theme_tuner_semantic_tint_label, knobs.semanticTintAlpha, SEMANTIC_TINT_RANGE) { updateKnobs { copy(semanticTintAlpha = it) } }
                }
                TunerReadout(knobs = knobs)
                FlashcardsTextButton(
                    text = stringResource(R.string.dark_theme_tuner_reset_button),
                    onClick = DarkThemeTunerState::reset,
                )
            }
        }
    }
}

/** A titled group of knobs that folds away; open on first show. */
@Composable
private fun KnobSection(@StringRes titleRes: Int, content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
        )
    }
    if (expanded) {
        Column { content() }
    }
}

/** The gradient with each on-gradient material on it, plus an opaque card like the session's rating sheet. */
@Composable
private fun TunerGradientPreview() {
    val brandColors = MaterialTheme.brandColors
    Box(modifier = Modifier.fillMaxWidth().blurBackdropScope()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .blurBackdropSource()
                .background(brandColors.screenGradient),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.normal, vertical = MaterialTheme.spacing.xsmall),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                FlashcardsMetadataBadge(
                    label = stringResource(R.string.dark_theme_tuner_badge_label),
                    icon = Icons.Default.Schedule,
                    style = FlashcardsComponentStyle.OnGradient,
                )
                FlashcardsTonalButton(
                    text = stringResource(R.string.dark_theme_tuner_tonal_button),
                    onClick = {},
                    style = FlashcardsComponentStyle.OnGradient,
                )
                FlashcardsFilledButton(
                    text = stringResource(R.string.dark_theme_tuner_filled_button),
                    onClick = {},
                    style = FlashcardsComponentStyle.OnGradient,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                Text(
                    text = stringResource(R.string.dark_theme_tuner_gradient_text_message),
                    style = MaterialTheme.typography.titleSmall,
                    color = brandColors.onGradientContent,
                )
                FlashcardsLinearProgressBar(
                    progress = PREVIEW_PROGRESS,
                    modifier = Modifier.weight(1f),
                    style = FlashcardsComponentStyle.OnGradient,
                )
            }
            FlashcardsInfoBanner(
                text = stringResource(R.string.dark_theme_tuner_banner_message),
                icon = Icons.Default.Info,
                style = FlashcardsComponentStyle.OnGradient,
            )
            // Opaque, like the bottom sheet that hosts the rating buttons in a session.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(MaterialTheme.cornerRadius.card),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                FlashcardsRatingButtonRow(
                    modifier = Modifier.padding(vertical = MaterialTheme.spacing.xsmall),
                    showLabels = false,
                    onRatingSelect = {},
                )
            }
        }
    }
}

/**
 * Info banners in each tone, on the screen and inside a dialog-colored surface.
 */
@Composable
private fun TunerSurfaceBannersPreview() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.normal, vertical = MaterialTheme.spacing.xsmall),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
    ) {
        FlashcardsInfoBanner(
            text = stringResource(R.string.dark_theme_tuner_banner_info_message),
            icon = Icons.Default.Info,
            tone = FlashcardsInfoBannerTone.Info,
        )
        FlashcardsInfoBanner(
            text = stringResource(R.string.dark_theme_tuner_banner_positive_message),
            icon = Icons.Default.CheckCircle,
            tone = FlashcardsInfoBannerTone.Positive,
        )
        FlashcardsInfoBanner(
            text = stringResource(R.string.dark_theme_tuner_banner_warning_message),
            icon = Icons.Default.Warning,
            tone = FlashcardsInfoBannerTone.Warning,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(MaterialTheme.cornerRadius.large),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            FlashcardsInfoBanner(
                text = stringResource(R.string.dark_theme_tuner_banner_dialog_message),
                icon = Icons.Default.Info,
                tone = FlashcardsInfoBannerTone.Info,
                modifier = Modifier.padding(MaterialTheme.spacing.normal),
            )
        }
    }
}

/** A checkable grouped list on the screen background, like Category Details, to judge cards against the background. */
@Composable
private fun TunerListPreview() {
    val titles = listOf(
        stringResource(R.string.dark_theme_tuner_list_first_label),
        stringResource(R.string.dark_theme_tuner_list_second_label),
        stringResource(R.string.dark_theme_tuner_list_third_label),
    )
    val selection = remember { mutableStateListOf(true, false, false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.normal, vertical = MaterialTheme.spacing.xsmall)
            .flashcardsListGroupContainer(),
    ) {
        titles.forEachIndexed { index, title ->
            FlashcardsSelectableListRow(
                title = title,
                selected = selection[index],
                onSelectedChange = { selection[index] = it },
                modifier = Modifier.flashcardsListItemShape(FlashcardsListItemPosition.of(index, titles.size)),
            )
        }
    }
}

/** The filled on-gradient button's label contrast against its pill. */
@Composable
private fun FilledButtonContrast(colors: FlashcardsColors) {
    Text(
        text = stringResource(R.string.dark_theme_tuner_filled_button_contrast_message, formatContrast(colors.filledButtonContrast())),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** WCAG contrast of the filled button's label against its pill. */
private fun FlashcardsColors.filledButtonContrast(): Float {
    val label = brand.onGradientFilled.luminance()
    val pill = brand.onGradientFilledContainer.luminance()
    val lighter = maxOf(label, pill)
    val darker = minOf(label, pill)
    return (lighter + CONTRAST_OFFSET) / (darker + CONTRAST_OFFSET)
}

/**
 * The colors the knobs produce as `Color.kt` constants, ready to paste over the dark ones there, with
 * the knob values above them as a comment; a button copies it.
 */
@Composable
private fun TunerReadout(knobs: DarkThemeTunerKnobs) {
    val readout = remember(knobs) { knobs.readout() }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Text(text = readout, style = MaterialTheme.typography.code)
    FlashcardsTonalButton(
        text = stringResource(R.string.dark_theme_tuner_copy_button),
        onClick = {
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(CLIP_LABEL, readout))) }
        },
    )
}

private fun DarkThemeTunerKnobs.readout(): String {
    val knobValues = listOf(
        "hue" to hue,
        "chroma" to chroma,
        "backgroundTone" to backgroundTone,
        "cardTone" to cardTone,
        "sheetTone" to sheetTone,
        "dialogTone" to dialogTone,
        "tonalButtonTone" to tonalButtonTone,
        "filledButtonTone" to filledButtonTone,
        "glassTone" to glassTone,
        "outlineVariantTone" to outlineVariantTone,
        "glassAlpha" to glassAlpha,
        "glassEdgeAlpha" to glassEdgeAlpha,
        "semanticTintAlpha" to semanticTintAlpha,
        "listTintAlpha" to listTintAlpha,
    ).joinToString(separator = "\n") { (name, value) -> "// $name = ${formatKnob(value)}" }
    val colors = toColors()
    val constants = with(colors) {
        listOf(
            "backgroundDark" to colorScheme.background,
            "surfaceContainerLowestDark" to colorScheme.surfaceContainerLowest,
            "surfaceContainerLowDark" to colorScheme.surfaceContainerLow,
            "surfaceContainerDark" to colorScheme.surfaceContainer,
            "surfaceContainerHighDark" to colorScheme.surfaceContainerHigh,
            "surfaceContainerHighestDark" to colorScheme.surfaceContainerHighest,
            "surfaceBrightDark" to colorScheme.surfaceBright,
            "outlineVariantDark" to colorScheme.outlineVariant,
            "secondaryContainerDark" to colorScheme.secondaryContainer,
            "onGradientFilledContainerDark" to brand.onGradientFilledContainer,
            "onGradientContainerDark" to brand.onGradientContainer,
            "onGradientBorderDark" to brand.onGradientBorder,
            "positiveContainerDark" to semantic.positiveContainer,
            "neutralContainerDark" to semantic.neutralContainer,
            "negativeContainerDark" to semantic.negativeContainer,
        )
    }.joinToString(separator = "\n") { (name, color) -> "internal val $name = ${color.literal()}" }
    return "$knobValues\n// filled button contrast = ${formatContrast(colors.filledButtonContrast())}\n$constants"
}

@Composable
private fun Knob(@StringRes labelRes: Int, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.dark_theme_tuner_value_label, stringResource(labelRes), formatKnob(value)),
            modifier = Modifier.weight(KNOB_LABEL_WEIGHT),
            style = MaterialTheme.typography.labelMedium,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f - KNOB_LABEL_WEIGHT),
            valueRange = range,
        )
    }
}

private fun updateKnobs(change: DarkThemeTunerKnobs.() -> DarkThemeTunerKnobs) {
    DarkThemeTunerState.knobs = DarkThemeTunerState.knobs.change()
}

private fun formatKnob(value: Float): String = String.format(Locale.US, "%.3f", value)

private fun formatContrast(value: Float): String = String.format(Locale.US, "%.2f:1", value)

/** This color as a Kotlin `Color(0xAARRGGBB)` literal. */
private fun Color.literal(): String = String.format(Locale.US, "Color(0x%08X)", toArgb())

private const val CLIP_LABEL = "Dark theme tuning"
private const val PREVIEW_PROGRESS = 0.4f
private const val KNOB_LABEL_WEIGHT = 0.45f
private val HUE_RANGE = 0f..360f
private val CHROMA_RANGE = 0f..24f
private val TONE_RANGE = 0f..40f
private val OUTLINE_TONE_RANGE = 10f..60f
private val UNIT_RANGE = 0f..1f
private val EDGE_ALPHA_RANGE = 0f..0.4f
private val SEMANTIC_TINT_RANGE = 0.05f..0.5f
private val LIST_TINT_RANGE = 0f..0.3f
private const val CONTRAST_OFFSET = 0.05f
