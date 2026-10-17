// Token-grouping file: the two selection containers are a matched pair and are read together.
@file:Suppress("MatchingDeclarationName")

package com.rossomak.flashcards.core.ui.composables.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rossomak.flashcards.core.designsystem.theme.spacing

/**
 * Container for a set of mutually exclusive options ([FlashcardsRadioRow], [FlashcardsOptionCard]).
 *
 * Its job is the accessibility semantics an individual row cannot provide: `selectableGroup()`
 * makes TalkBack announce "1 of 3" style position within the group and treats the rows as one
 * radio group rather than three unrelated controls.
 *
 * Use inside a dialog's content slot. The L2 dialog types know nothing about selection — they see
 * an opaque content lambda — so this pairing is the concrete dialog's responsibility.
 */
@Composable
fun FlashcardsSingleSelectGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
        content = content,
    )
}

/**
 * Container for independently toggled options ([FlashcardsCheckableRow]).
 *
 * Deliberately *not* a `selectableGroup()` — that modifier declares mutual exclusivity, which is
 * wrong here. Each row is its own checkable control and announces its own checked state.
 *
 * Owns the stack's geometry so the rows can stay plain: [dialogContentBleed] widens the whole
 * stack past the dialog's prose margin, and the gap between rows is zero because
 * [FlashcardsCheckableRow] carries its padding inside its clickable bounds — a gap here would only
 * shrink each row's press target and the indication drawn on it.
 */
@Composable
fun FlashcardsMultiSelectGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .dialogContentBleed(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.none),
        content = content,
    )
}
