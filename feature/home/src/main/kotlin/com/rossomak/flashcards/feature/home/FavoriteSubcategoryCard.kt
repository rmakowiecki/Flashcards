package com.rossomak.flashcards.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.model.SubcategoryProgressState
import com.rossomak.flashcards.core.domain.model.SubcategoryProgressState.Resolved
import com.rossomak.flashcards.core.ui.composables.FlashcardsVectorIconTile
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledIconButton
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize
import com.rossomak.flashcards.core.ui.composables.progress.FlashcardsLinearProgressBar
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.core.ui.theme.toCategoryColorOrNull
import kotlin.math.roundToInt

private const val PERCENT_SCALE = 100

/**
 * A Favorite Subcategory: its name, its parent Category's name and icon, how much of it the User has
 * Studied, and a play button.
 *
 * The body is one button; the play button is a separate node with its own description. An
 * [SubcategoryProgressState.Unresolved] [progress] omits the label and bar, but the bottom row keeps
 * the play button's height, so the card does not resize when the progress arrives.
 */
@Composable
internal fun FavoriteSubcategoryCard(
    subcategory: Subcategory,
    parentCategory: Category,
    progress: SubcategoryProgressState,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val parentColor = remember(parentCategory.color) { parentCategory.color.toCategoryColorOrNull() }
        ?: MaterialTheme.colorScheme.primary
    val playButtonSize = MaterialTheme.sizes.buttonHeightSmall

    FavoriteCardContainer(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(MaterialTheme.spacing.normal),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(modifier = Modifier.weight(1f, fill = false)) {
                FlashcardsVectorIconTile(
                    iconSvg = parentCategory.iconSvg,
                    color = parentCategory.color,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(MaterialTheme.spacing.small))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subcategory.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = parentCategory.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = parentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = playButtonSize),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (progress is Resolved) {
                    StudiedProgress(
                        studiedFraction = progress.studiedFraction(subcategory.cardCount),
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.width(playButtonSize + MaterialTheme.spacing.small))
            }
        }
        FlashcardsFilledIconButton(
            icon = Icons.Filled.PlayArrow,
            contentDescription = stringResource(R.string.favorites_subcategory_play_cd, subcategory.name),
            onClick = onPlayClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(MaterialTheme.spacing.normal),
            size = FlashcardsComponentSize.Small,
        )
    }
}

@Composable
private fun StudiedProgress(studiedFraction: Float, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxsmall)) {
        Text(
            text = stringResource(
                R.string.favorites_subcategory_progress_label,
                (studiedFraction * PERCENT_SCALE).roundToInt(),
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        FlashcardsLinearProgressBar(
            progress = studiedFraction,
            modifier = Modifier.fillMaxWidth(),
            size = FlashcardsComponentSize.Small,
        )
    }
}

@PreviewLightDark
@Composable
private fun FavoriteSubcategoryCardPreview() {
    val category = Category(
        id = "android",
        name = "Android",
        order = 0,
        subcategoryCount = 14,
        iconSvg = null,
        color = "#2B6AA5",
        featuredSubcategoryNames = emptyList(),
    )
    FlashcardsTheme {
        FavoriteSubcategoryCard(
            subcategory = Subcategory(
                id = "compose",
                name = "Compose",
                categoryId = category.id,
                categoryName = category.name,
                order = 0,
                cardCount = 30,
            ),
            parentCategory = category,
            progress = Resolved(studiedCount = 25, masteredCount = 10),
            onClick = {},
            onPlayClick = {},
        )
    }
}
