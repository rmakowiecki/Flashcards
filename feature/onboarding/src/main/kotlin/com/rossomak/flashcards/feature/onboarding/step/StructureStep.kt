package com.rossomak.flashcards.feature.onboarding.step

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.rossomak.flashcards.core.designsystem.theme.cornerRadius
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.core.ui.composables.FlashcardsIconTile
import com.rossomak.flashcards.feature.onboarding.R
import com.rossomak.flashcards.feature.onboarding.component.OnboardingHierarchyConnector
import com.rossomak.flashcards.feature.onboarding.component.OnboardingStepColumn
import com.rossomak.flashcards.feature.onboarding.component.OnboardingStepHeader

/**
 * Teaches the Category → Topic → Flashcard hierarchy with three stacked example cards.
 *
 * "Topic" is the presentation-layer name for what the code calls a Subcategory (see CONTEXT.md);
 * the label here is correct as designed, not a terminology slip.
 */
@Composable
internal fun StructureStep(modifier: Modifier = Modifier) {
    OnboardingStepColumn(modifier = modifier) {
        OnboardingStepHeader(
            eyebrow = stringResource(R.string.structure_eyebrow_label),
            headline = stringResource(R.string.structure_headline_title),
            message = stringResource(R.string.structure_intro_message),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))
        StructureExampleCard(
            kindLabel = stringResource(R.string.structure_category_label),
            title = stringResource(R.string.structure_category_example_title),
            message = stringResource(R.string.structure_category_example_message),
            icon = Icons.Default.Android,
        )
        OnboardingHierarchyConnector()
        StructureExampleCard(
            kindLabel = stringResource(R.string.structure_topic_label),
            title = stringResource(R.string.structure_topic_example_title),
            message = stringResource(R.string.structure_topic_example_message),
            icon = Icons.Default.Folder,
        )
        OnboardingHierarchyConnector()
        StructureExampleCard(
            kindLabel = stringResource(R.string.structure_flashcard_label),
            title = stringResource(R.string.structure_flashcard_example_title),
            message = stringResource(R.string.structure_flashcard_example_message),
            icon = Icons.Default.Style,
        )
    }
}

@Composable
private fun StructureExampleCard(
    kindLabel: String,
    title: String,
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MaterialTheme.cornerRadius.card),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.small),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlashcardsIconTile(icon = icon, contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.none)) {
                Text(
                    text = kindLabel.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
