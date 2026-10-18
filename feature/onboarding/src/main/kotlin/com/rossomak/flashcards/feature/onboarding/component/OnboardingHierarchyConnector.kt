package com.rossomak.flashcards.feature.onboarding.component

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.rossomak.flashcards.core.designsystem.theme.spacing

/**
 * Decorative chevron linking one stacked example card to the next. Shared by any step that breaks
 * an illustration into a sequence of cards ([com.rossomak.flashcards.feature.onboarding.step.StructureStep],
 * [com.rossomak.flashcards.feature.onboarding.step.MasteryStep]).
 */
@Composable
internal fun OnboardingHierarchyConnector(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Default.KeyboardArrowDown,
        contentDescription = null,
        tint = OnboardingContentColors.eyebrow,
        modifier = modifier
            .size(MaterialTheme.spacing.medium)
            .clearAndSetSemantics { },
    )
}
