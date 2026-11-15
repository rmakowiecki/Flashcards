package com.rossomak.flashcards.debugtools.darktheme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.materialkolor.hct.Hct
import com.rossomak.flashcards.core.designsystem.theme.FlashcardsColors
import com.rossomak.flashcards.core.designsystem.theme.darkFlashcardsColors

/**
 * The tuner's inputs. Every dark surface is an HCT tone of one neutral palette at [hue] and [chroma];
 * [toColors] turns them into the dark theme's colors. The defaults reproduce [darkFlashcardsColors]
 * exactly, which a test checks, so the tuner starts from the shipped dark theme.
 */
@Immutable
data class DarkThemeTunerKnobs(
    val hue: Float = 290f,
    val chroma: Float = 10f,
    val backgroundTone: Float = 6f,
    val cardTone: Float = 10f,
    val sheetTone: Float = 12f,
    val dialogTone: Float = 14f,
    val tonalButtonTone: Float = 22f,
    val filledButtonTone: Float = 17f,
    val glassTone: Float = 12f,
    val outlineVariantTone: Float = 30f,
    val glassAlpha: Float = 0.3f,
    val glassEdgeAlpha: Float = 0.12f,
    val semanticTintAlpha: Float = 0.14f,
    val listTintAlpha: Float = 0.05f,
) {
    /** The neutral palette's color at [tone]. */
    fun tone(tone: Float): Color = Color(Hct.from(hue.toDouble(), chroma.toDouble(), tone.toDouble()).toInt())

    /** The card color, the base every dark tint mixes over. */
    val card: Color get() = tone(cardTone)

    /** The on-gradient glass and its progress track. */
    val glass: Color get() = tone(glassTone).copy(alpha = glassAlpha)

    /**
     * [darkFlashcardsColors] with every tuned slot taken from these knobs. The container slots without
     * their own knob keep Material's dark spacing above the card: Low +1, Container +2, High +7,
     * Highest +12, Bright +14 tones.
     */
    fun toColors(): FlashcardsColors = with(darkFlashcardsColors) {
        val background = tone(backgroundTone)
        val outlineVariant = tone(outlineVariantTone)
        copy(
            colorScheme = colorScheme.copy(
                background = background,
                surface = background,
                surfaceDim = background,
                surfaceContainerLowest = card,
                surfaceContainerLow = tone(cardTone + LOW_OFFSET),
                surfaceContainer = tone(cardTone + CONTAINER_OFFSET),
                surfaceContainerHigh = tone(cardTone + HIGH_OFFSET),
                surfaceContainerHighest = tone(cardTone + HIGHEST_OFFSET),
                surfaceBright = tone(cardTone + BRIGHT_OFFSET),
                surfaceVariant = outlineVariant,
                outlineVariant = outlineVariant,
            ),
            brand = brand.copy(
                tonalButtonContainer = tone(tonalButtonTone),
                onGradientFilledContainer = tone(filledButtonTone),
                progressBarTrackOnGradient = glass,
                onGradientContainer = glass,
                onGradientBorder = Color.White.copy(alpha = glassEdgeAlpha),
                listGroupContainer = colorScheme.secondary.copy(alpha = listTintAlpha).compositeOver(card),
                dialogContainer = tone(dialogTone),
                sheetContainer = tone(sheetTone),
            ),
            semantic = with(semantic) {
                copy(
                    positiveContainer = onPositiveContainer.copy(alpha = semanticTintAlpha).compositeOver(card),
                    neutralContainer = onNeutralContainer.copy(alpha = semanticTintAlpha).compositeOver(card),
                    negativeContainer = onNegativeContainer.copy(alpha = semanticTintAlpha).compositeOver(card),
                )
            },
        )
    }

    companion object {
        const val LOW_OFFSET = 1f
        const val CONTAINER_OFFSET = 2f
        const val HIGH_OFFSET = 7f
        const val HIGHEST_OFFSET = 12f
        const val BRIGHT_OFFSET = 14f
    }
}
