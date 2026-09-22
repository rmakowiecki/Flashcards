package com.rossomak.flashcards.feature.study.chrome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.ui.composables.FlashcardsMetadataBadge
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsGradientTopBar
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle.OnGradient
import com.rossomak.flashcards.core.ui.composables.progress.FlashcardsLinearProgressBar
import com.rossomak.flashcards.core.ui.theme.brandColors
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.study.R

/**
 * The header shared by every Study Session screen
 * ([ADR-0045](../../../../../../../../docs/adr/0045-separate-fast-and-rated-session-screens.md)):
 * [FlashcardsGradientTopBar] (a close action opening the exit-confirmation dialog, and a flag
 * action shown only while a card is on screen that opens Report a problem for that card) plus,
 * whenever [progressFraction] is non-null, a [progressLabel]/[completedCount]/[totalCount] row and a
 * [FlashcardsLinearProgressBar] beneath it, followed by [reportableCard]'s tags (when any) as an
 * on-gradient badge row.
 *
 * Takes the current card and already-formatted label text as plain values rather than the screen
 * state that owns them — the two Study Modes carry two different state types
 * (see [StudySessionBody]'s own doc) and this header must not force either into a shared
 * supertype just to be fed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudySessionHeader(
    modifier: Modifier = Modifier,
    title: String,
    reportableCard: Flashcard?,
    progressLabel: String?,
    completedCount: Int?,
    totalCount: Int?,
    progressFraction: Float?,
    onClose: () -> Unit,
    onReportProblem: (card: Flashcard) -> Unit,
) {
    Column(modifier = modifier) {
        FlashcardsGradientTopBar(
            title = title,
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.exit_session_dialog_title),
                    )
                }
            },
            actions = {
                if (reportableCard != null) {
                    IconButton(onClick = { onReportProblem(reportableCard) }) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = stringResource(R.string.report_problem_dialog_title),
                        )
                    }
                }
            },
        )
        if (progressLabel != null && completedCount != null && totalCount != null && progressFraction != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.spacing.normal)
                    .padding(bottom = MaterialTheme.spacing.small),
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    // alignByBaseline, not Alignment.Bottom: labelMedium and titleLarge have
                    // different descent, so box-bottom alignment leaves their glyphs visibly
                    // offset — see the LEVEL/level-number pair in FlashcardsLevelCard.
                    Text(
                        text = progressLabel,
                        modifier = Modifier.alignByBaseline(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.brandColors.onGradientContent,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = completedCount.toString(),
                        modifier = Modifier.alignByBaseline(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.brandColors.onGradientContent,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.study_session_progress_total_label, totalCount),
                        modifier = Modifier.alignByBaseline(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.brandColors.onGradientContent,
                    )
                }
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.xxsmall))
                FlashcardsLinearProgressBar(
                    progress = progressFraction,
                    style = OnGradient,
                )
            }
        }
        val cardTags = reportableCard?.tags
        if (!cardTags.isNullOrEmpty()) {
            FlashcardTagRow(
                tags = cardTags,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.spacing.normal)
                    .padding(bottom = MaterialTheme.spacing.small),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlashcardTagRow(tags: List<String>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxsmall),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxsmall),
    ) {
        tags.forEach { tag -> FlashcardsMetadataBadge(label = tag, style = OnGradient) }
    }
}
