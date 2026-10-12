package com.rossomak.flashcards.feature.onboarding.step

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Grading
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.ui.composables.FlashcardsIconCircle
import com.rossomak.flashcards.core.ui.composables.FlashcardsMetadataBadge
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.onboarding.R
import com.rossomak.flashcards.feature.onboarding.component.OnboardingContentColors
import com.rossomak.flashcards.feature.onboarding.component.OnboardingStepColumn

/**
 * The outro and the flow's only exit.
 *
 * The badges recap the three decisions the flow actually captures, in the order they were made.
 * They read straight from state, so a run reached by Skip shows the same defaults the app is about
 * to save — no separate "skipped" layout, and nothing on screen that the app will not honour.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AllSetStep(
    userName: String?,
    defaultStudyMode: StudyMode,
    dailyGoalMinutes: Int,
    favoriteCount: Int,
    modifier: Modifier = Modifier,
) {
    OnboardingStepColumn(modifier = modifier) {
        FlashcardsIconCircle(
            icon = Icons.Default.Celebration,
            contentDescription = null,
            style = FlashcardsComponentStyle.OnGradient,
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
        Text(
            text = if (userName != null) {
                stringResource(R.string.all_set_headline_title, userName)
            } else {
                stringResource(R.string.all_set_headline_anonymous_title)
            },
            style = MaterialTheme.typography.headlineSmallEmphasized,
            color = OnboardingContentColors.primary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.xsmall))
        Text(
            text = stringResource(R.string.all_set_intro_message),
            style = MaterialTheme.typography.bodyMedium,
            color = OnboardingContentColors.secondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(
                space = MaterialTheme.spacing.xsmall,
                alignment = Alignment.CenterHorizontally,
            ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
        ) {
            FlashcardsMetadataBadge(
                label = stringResource(R.string.all_set_mode_badge_label, defaultStudyMode.displayName()),
                icon = when (defaultStudyMode) {
                    StudyMode.Rated -> Icons.Default.Grading
                    StudyMode.Fast -> Icons.Default.Bolt
                },
                style = FlashcardsComponentStyle.OnGradient,
            )
            FlashcardsMetadataBadge(
                label = stringResource(R.string.all_set_daily_goal_badge_label, dailyGoalMinutes),
                icon = Icons.Default.Schedule,
                style = FlashcardsComponentStyle.OnGradient,
            )
            if (favoriteCount > 0) {
                FlashcardsMetadataBadge(
                    label = pluralStringResource(
                        R.plurals.all_set_favorites_badge_label,
                        favoriteCount,
                        favoriteCount,
                    ),
                    icon = Icons.Default.FavoriteBorder,
                    style = FlashcardsComponentStyle.OnGradient,
                )
            }
        }
    }
}

@Composable
private fun StudyMode.displayName(): String = when (this) {
    StudyMode.Rated -> stringResource(R.string.session_modes_rated_title)
    StudyMode.Fast -> stringResource(R.string.session_modes_fast_title)
}
