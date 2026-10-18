package com.rossomak.flashcards.feature.onboarding.component

import androidx.compose.ui.graphics.Color

/**
 * Content colours for onboarding steps. Fixed rather than themed: every step is drawn on
 * [com.rossomak.flashcards.core.designsystem.theme.BrandColors.screenGradient], which is itself identical in
 * light and dark, so its content must be too.
 */
internal object OnboardingContentColors {
    val primary: Color = Color.White
    val secondary: Color = Color.White.copy(alpha = 0.78f)
    val eyebrow: Color = Color.White.copy(alpha = 0.70f)
}
