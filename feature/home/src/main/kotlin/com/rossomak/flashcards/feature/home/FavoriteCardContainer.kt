package com.rossomak.flashcards.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rossomak.flashcards.core.ui.theme.cornerRadius

internal val FavoriteCardWidth = 210.dp
internal val FavoriteCardHeight = 140.dp

/**
 * The flat card shell shared by both Favorite card kinds: no elevation, no border, the card corner
 * token, and a clip so a child drawn past the edge (the Category watermark) is cropped by the card.
 *
 * [content] is a [Box], so a card layers its body (the clickable surface) and its separate action
 * button as siblings: a button nested inside the clickable body would be merged into the body's
 * semantics node instead of announcing on its own.
 */
@Composable
internal fun FavoriteCardContainer(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Surface(
        modifier = modifier.size(width = FavoriteCardWidth, height = FavoriteCardHeight),
        shape = RoundedCornerShape(MaterialTheme.cornerRadius.card),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Box(content = content)
    }
}
