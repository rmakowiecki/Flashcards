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
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.backdrop.blurBackdrop
import com.rossomak.flashcards.core.ui.composables.banners.FlashcardsInfoBannerTone.Info
import com.rossomak.flashcards.core.ui.composables.banners.FlashcardsInfoBannerTone.Positive
import com.rossomak.flashcards.core.ui.composables.banners.FlashcardsInfoBannerTone.Warning
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle.OnGradient
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle.OnSurface
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.brandColors
import com.rossomak.flashcards.core.ui.theme.cornerRadius
import com.rossomak.flashcards.core.ui.theme.semanticColors
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.spacing

private val BANNER_ICON_SIZE = 16.dp

/**
 * The tip/info banner — a single-line-or-wrapping strip that explains a rule, a consequence, or a
 * restriction next to the thing it applies to ("Your performance updates the card scores used to
 * plan future sessions", "This card is private to you").
 *
 * The banner is deliberately minimal: no title, no dismiss affordance, no action. A tip that needs
 * a heading or a button is a different component, and a dismissible tip needs persisted "seen"
 * state, which is a feature decision rather than a component default.
 *
 * [icon] is required — every banner carries one, and it is what makes a banner scannable in a
 * dense form. It is rendered decoratively (the text carries the meaning), so it is not announced.
 *
 * The banner does not size itself: pass `Modifier.fillMaxWidth()` from the call site to get the
 * full-width strip the design uses, per the Compose component guidelines on forwarding [modifier]
 * unchanged.
 *
 * @param tone Color coding for [FlashcardsComponentStyle.OnSurface]. Ignored for
 *   [FlashcardsComponentStyle.OnGradient], which is always the translucent white treatment — a
 *   gradient banner is never color-coded.
 */
@Composable
fun FlashcardsInfoBanner(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: FlashcardsInfoBannerTone = Info,
    style: FlashcardsComponentStyle = OnSurface,
    blursBackdrop: Boolean = style == OnGradient,
) {
    val semanticColors = MaterialTheme.semanticColors
    val brandColors = MaterialTheme.brandColors
    val containerColor = when (style) {
        OnSurface -> when (tone) {
            Positive -> semanticColors.positiveContainer
            Info -> semanticColors.neutralContainer
            Warning -> semanticColors.negativeContainer
        }
        OnGradient -> brandColors.onGradientContainer
    }
    val shape = RoundedCornerShape(MaterialTheme.cornerRadius.medium)
    val contentColor = when (style) {
        OnSurface -> when (tone) {
            Positive -> semanticColors.onPositiveContainer
            Info -> semanticColors.onNeutralContainer
            Warning -> semanticColors.onNegativeContainer
        }
        OnGradient -> brandColors.onGradientContent
    }
    val border = when (style) {
        OnSurface -> null
        OnGradient -> BorderStroke(
            width = MaterialTheme.sizes.hairline,
            color = brandColors.onGradientBorder,
        )
    }

    Surface(
        modifier = modifier.blurBackdrop(shape = shape, enabled = blursBackdrop),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = border,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.small,
                vertical = MaterialTheme.spacing.xsmall,
            ),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                // Decorative: the banner text carries the whole message.
                contentDescription = null,
                modifier = Modifier.size(BANNER_ICON_SIZE),
            )
            Text(text = text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@ShowkaseComposable(name = "Info", group = "Banners")
@Composable
fun FlashcardsInfoBannerShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsInfoBanner(
                text = "Your performance updates the card scores used to plan future sessions.",
                icon = Icons.Default.Lightbulb,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.spacing.small),
            )
        }
    }
}

@ShowkaseComposable(name = "Info — warning", group = "Banners")
@Composable
fun FlashcardsInfoBannerWarningShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsInfoBanner(
                text = "This card is private to you — only you can see and study it.",
                icon = Icons.Default.Lock,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.spacing.small),
                tone = Warning,
            )
        }
    }
}

@ShowkaseComposable(name = "Info — on gradient", group = "Banners")
@Preview
@Composable
fun FlashcardsInfoBannerOnGradientShowcase() {
    FlashcardsTheme {
        Box(
            modifier = Modifier
                .background(MaterialTheme.brandColors.topBarGradient)
                .padding(MaterialTheme.spacing.small),
        ) {
            FlashcardsInfoBanner(
                text = "Both modes can go hands-off — great for a walk, commute or other daily activities.",
                icon = Icons.Default.Mic,
                modifier = Modifier.fillMaxWidth(),
                style = OnGradient,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsInfoBannerPreview() {
    FlashcardsTheme {
        Surface {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
            ) {
                FlashcardsInfoBanner(
                    text = "Every mastered card earns XP — climb Levels and track progress per topic.",
                    icon = Icons.Default.WorkspacePremium,
                    modifier = Modifier.fillMaxWidth(),
                    tone = Positive,
                )
                FlashcardsInfoBanner(
                    text = "Your performance updates the card scores used to plan future sessions.",
                    icon = Icons.Default.Lightbulb,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlashcardsInfoBanner(
                    text = "Private flashcards are reviewed before being added to the global pool.",
                    icon = Icons.Default.Lock,
                    modifier = Modifier.fillMaxWidth(),
                    tone = Warning,
                )
            }
        }
    }
}
