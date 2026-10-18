package com.rossomak.flashcards.feature.study.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.gallatinapps.syntaxmp.tokenizer.SyntaxTokenizer
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.core.domain.model.CodeBlock
import com.rossomak.flashcards.core.ui.composables.SyntaxCodeBlock
import com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardsSingleActionDialog
import com.rossomak.flashcards.core.ui.composables.withInlineCode
import com.rossomak.flashcards.feature.study.R

/**
 * Read-only extra context the user opted into — definitions, explanations, a code sample.
 *
 * There is nothing to set, so its single acknowledging action ("Got it") commits nothing;
 * [onDismiss] and the button do the same thing. Never a fork.
 */
@Composable
fun ExtendedContextDialog(
    extendedContext: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    codeBlocks: List<CodeBlock> = emptyList(),
    engine: SyntaxTokenizer = remember { SyntaxTokenizer() },
) {
    FlashcardsSingleActionDialog(
        title = stringResource(R.string.extended_context_dialog_title),
        onConfirm = onDismiss,
        onDismiss = onDismiss,
        modifier = modifier,
        actionLabel = stringResource(R.string.extended_context_acknowledge_button),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            Text(text = extendedContext.withInlineCode(), style = MaterialTheme.typography.bodyMedium)
            codeBlocks.forEach { codeBlock ->
                SyntaxCodeBlock(code = codeBlock.code, language = codeBlock.language, engine = engine)
            }
        }
    }
}

@Preview
@Composable
private fun ExtendedContextDialogPreview() {
    ExtendedContextDialog(
        extendedContext = "For a custom data type, pair state with a Saver so only the fields " +
            "you need get persisted:",
        onDismiss = {},
        codeBlocks = listOf(
            CodeBlock(
                language = "kotlin",
                code = "@Composable\nfun Counter() {\n    var count by rememberSaveable { mutableIntStateOf(0) }\n}",
            ),
        ),
    )
}
