package com.rossomak.flashcards.feature.onboarding.step

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.rossomak.flashcards.core.designsystem.theme.cornerRadius
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.ui.composables.banners.FlashcardsInfoBanner
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardsOptionCard
import com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardsSingleSelectGroup
import com.rossomak.flashcards.core.ui.composables.icon
import com.rossomak.flashcards.feature.onboarding.R
import com.rossomak.flashcards.feature.onboarding.component.OnboardingStepColumn
import com.rossomak.flashcards.feature.onboarding.component.OnboardingStepHeader

/**
 * Explains Rated vs Fast and captures the user's default Study Mode in the same screen.
 *
 * Picking a mode here sets a persisted default only — it does not start anything. The Preview Study
 * Session Screen remains the only place a concrete session's mode is chosen (ADR-0030), which is
 * why this step's CTA is the flow's ordinary Continue and not a session-start button.
 *
 * Both options sit on one white card, using [FlashcardsOptionCard] — the same icon/title/description
 * row a dialog's single-select list uses — rather than two separate bordered cards.
 */
@Composable
internal fun SessionModesStep(
    selectedStudyMode: StudyMode,
    onStudyModeSelect: (StudyMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingStepColumn(modifier = modifier) {
        OnboardingStepHeader(
            eyebrow = stringResource(R.string.session_modes_eyebrow_label),
            headline = stringResource(R.string.session_modes_headline_title),
            message = stringResource(R.string.session_modes_intro_message),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(MaterialTheme.cornerRadius.card),
            color = MaterialTheme.colorScheme.surface,
        ) {
            FlashcardsSingleSelectGroup(
                modifier = Modifier.padding(MaterialTheme.spacing.small),
            ) {
                FlashcardsOptionCard(
                    icon = StudyMode.Rated.icon,
                    title = stringResource(R.string.session_modes_rated_title),
                    description = stringResource(R.string.session_modes_rated_message),
                    selected = selectedStudyMode == StudyMode.Rated,
                    onSelect = { onStudyModeSelect(StudyMode.Rated) },
                )
                FlashcardsOptionCard(
                    icon = StudyMode.Fast.icon,
                    title = stringResource(R.string.session_modes_fast_title),
                    description = stringResource(R.string.session_modes_fast_message),
                    selected = selectedStudyMode == StudyMode.Fast,
                    onSelect = { onStudyModeSelect(StudyMode.Fast) },
                )
            }
        }
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.normal))
        FlashcardsInfoBanner(
            text = stringResource(R.string.session_modes_hands_free_banner_message),
            icon = Icons.Default.Mic,
            modifier = Modifier.fillMaxWidth(),
            style = FlashcardsComponentStyle.OnGradient,
        )
    }
}
