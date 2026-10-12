package com.rossomak.flashcards.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.rossomak.flashcards.core.ui.R

/**
 * Bundled variable Roboto, instanced at the three weights the M3 type scale uses (Normal for the
 * regular slots, Medium and Bold for the regular and `*Emphasized` slots that ask for them), plus
 * ExtraBold for the hero figures below.
 */
private val robotoFontFamily = FontFamily(
    robotoFont(FontWeight.Normal),
    robotoFont(FontWeight.Medium),
    robotoFont(FontWeight.Bold),
    robotoFont(FontWeight.ExtraBold),
)

private fun robotoFont(weight: FontWeight) = Font(
    resId = R.font.roboto,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** The stock M3 type scale, every slot and its `*Emphasized` twin set in [robotoFontFamily]. */
val AppTypography = Typography(fontFamily = robotoFontFamily)

/** Code shown as its own block of text (code blocks, raw debug data): [Typography.bodySmall] in monospace. */
val Typography.code: TextStyle
    get() = bodySmall.copy(fontFamily = FontFamily.Monospace)

/** A hero figure at display size, such as the Session Summary's XP total: [Typography.displaySmall] in ExtraBold. */
val Typography.displaySmallExtraBold: TextStyle
    get() = displaySmall.copy(fontWeight = FontWeight.ExtraBold)

/** A hero figure at title size, such as the Level card's Level number: [Typography.titleLarge] in ExtraBold. */
val Typography.titleLargeExtraBold: TextStyle
    get() = titleLarge.copy(fontWeight = FontWeight.ExtraBold)

/**
 * Code inside running text: monospace at a fraction of the surrounding text's size. Monospace glyphs
 * are wider and taller than Roboto's, so at full size the code would outweigh its sentence; the
 * relative size keeps the ratio the same next to any slot.
 */
val InlineCodeSpanStyle = SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 0.85.em)

/** Emphasis inside running text, at the weight the body `*Emphasized` slots use. */
val EmphasizedSpanStyle = SpanStyle(fontWeight = FontWeight.Medium)
