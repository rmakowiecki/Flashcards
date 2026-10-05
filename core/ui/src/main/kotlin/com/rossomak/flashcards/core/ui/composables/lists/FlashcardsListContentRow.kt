package com.rossomak.flashcards.core.ui.composables.lists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.rossomak.flashcards.core.ui.composables.FlashcardsIconTile
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.spacing

/** Opacity applied to the whole row when `enabled = false`, per the design's disabled rows. */
private const val DISABLED_ALPHA = 0.6f

/**
 * The frame every list row shares, with nothing inside it: the row background, the minimum height,
 * the standard padding, vertical centering with a small gap between children, and the click
 * semantics. The caller decides all of the content, so a row that needs a one-off look (the
 * Account screen's error-colored Delete account) composes its own children here instead of adding
 * a flag to [FlashcardsListRow].
 *
 * The whole row is one merged, clickable node with the given [role] — unless [onClick] is `null`,
 * in which case the row carries no click semantics at all. [onLongClick] is opt-in: leaving it
 * `null` keeps the cheaper [Modifier.clickable] path, passing it switches to
 * [Modifier.combinedClickable], which already fires the long-press haptic itself. Pairing
 * [onLongClick] with a `null` [onClick] is nonsensical and unsupported. When [enabled] is `false`,
 * the whole row dims rather than only disabling the click.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FlashcardsListContentRow(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    role: Role = Role.Button,
    content: @Composable RowScope.() -> Unit,
) {
    val clickModifier = when {
        onClick == null -> Modifier
        onLongClick != null -> Modifier.combinedClickable(
            enabled = enabled,
            role = role,
            onLongClick = onLongClick,
            onClick = onClick,
        )
        else -> Modifier.clickable(enabled = enabled, role = role, onClick = onClick)
    }
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .heightIn(min = MaterialTheme.sizes.listRowMinHeight)
            .then(clickModifier)
            .padding(
                horizontal = MaterialTheme.spacing.normal,
                vertical = MaterialTheme.spacing.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        content = content,
    )
}

@PreviewLightDark
@Composable
private fun FlashcardsListContentRowPreview() {
    FlashcardsTheme {
        Surface {
            FlashcardsListContentRow(onClick = {}) {
                FlashcardsIconTile(
                    icon = Icons.Default.DeleteForever,
                    contentDescription = null,
                    contentColor = MaterialTheme.colorScheme.error,
                )
                Text(text = "Delete account", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
