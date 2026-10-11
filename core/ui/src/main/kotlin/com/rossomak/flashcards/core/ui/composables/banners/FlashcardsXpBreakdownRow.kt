package com.rossomak.flashcards.core.ui.composables.banners

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.composables.banners.FlashcardsXpBreakdownTone.Gain
import com.rossomak.flashcards.core.ui.composables.banners.FlashcardsXpBreakdownTone.Loss
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.brandColors
import com.rossomak.flashcards.core.ui.theme.cornerRadius
import com.rossomak.flashcards.core.ui.theme.semanticColors
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.spacing

/** Container fill alpha for the [FlashcardsXpBreakdownTone.Loss] row's red tint. */
private const val LOSS_CONTAINER_ALPHA = 0.33f

/** Border alpha for the [FlashcardsXpBreakdownTone.Loss] row. */
private const val LOSS_BORDER_ALPHA = 0.5f

/**
 * One line of a session's XP breakdown, drawn on the brand gradient: a leading icon, a recessed
 * [label] naming the rule that fired ("Card Mastery", "Streak"), and the [value] line that shows
 * the arithmetic and its total ("11 cards × 100 = +1,100 XP").
 *
 * Gradient-only by design — the XP reveal is a gradient hero moment, and there is no surface
 * treatment for it. It therefore takes no
 * [com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle]; adding one later
 * is additive.
 *
 * Both [label] and [value] arrive fully formatted. Plurals, number grouping, and the ×/= glyphs
 * belong to the feature's string resources: the three rules mocked so far already have three
 * different sentence shapes, so a structured API would need a formula enum on day one.
 *
 * The row does not size itself — pass `Modifier.fillMaxWidth()` from the call site.
 */
@Composable
fun FlashcardsXpBreakdownRow(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: FlashcardsXpBreakdownTone = Gain,
) {
    val brandColors = MaterialTheme.brandColors
    val semanticColors = MaterialTheme.semanticColors
    val containerColor = when (tone) {
        Gain -> brandColors.onGradientContainer
        Loss -> semanticColors.onGradientLossContainer.copy(alpha = LOSS_CONTAINER_ALPHA)
    }
    val contentColor = when (tone) {
        Gain -> brandColors.onGradientContent
        Loss -> brandColors.onGradientLoss
    }
    val borderColor = when (tone) {
        Gain -> brandColors.onGradientBorder
        Loss -> semanticColors.onGradientLossContainer.copy(alpha = LOSS_BORDER_ALPHA)
    }

    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(MaterialTheme.cornerRadius.medium),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(width = MaterialTheme.sizes.onGradientBorder, color = borderColor),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.normal,
                vertical = MaterialTheme.spacing.small,
            ),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                // Decorative: the label names the rule this icon stands for.
                contentDescription = null,
                modifier = Modifier.size(MaterialTheme.sizes.xpBreakdownRowIcon),
            )
            Column {
                Text(
                    text = label,
                    color = contentColor,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@ShowkaseComposable(name = "XP breakdown", group = "Banners")
@Preview
@Composable
fun FlashcardsXpBreakdownRowShowcase() {
    FlashcardsTheme {
        Box(
            modifier = Modifier
                .background(MaterialTheme.brandColors.topBarGradient)
                .padding(MaterialTheme.spacing.small),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall)) {
                FlashcardsXpBreakdownRow(
                    label = "Card Mastery",
                    value = "11 cards × 100 = +1,100 XP",
                    icon = Icons.Filled.WorkspacePremium,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlashcardsXpBreakdownRow(
                    label = "De-mastery",
                    value = "4 cards × 80 = −320 XP",
                    icon = Icons.AutoMirrored.Filled.TrendingDown,
                    modifier = Modifier.fillMaxWidth(),
                    tone = Loss,
                )
                FlashcardsXpBreakdownRow(
                    label = "Streak",
                    value = "8 day streak × 250 = +2,000 XP",
                    icon = Icons.Filled.LocalFireDepartment,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
