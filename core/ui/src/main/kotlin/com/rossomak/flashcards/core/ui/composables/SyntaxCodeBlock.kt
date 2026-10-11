package com.rossomak.flashcards.core.ui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.gallatinapps.syntaxmp.compose.SyntaxTheme
import com.gallatinapps.syntaxmp.compose.rememberSyntaxAnnotatedString
import com.gallatinapps.syntaxmp.tokenizer.SyntaxTokenizer
import com.rossomak.flashcards.core.ui.theme.CodeBlockColors
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.code
import com.rossomak.flashcards.core.ui.theme.cornerRadius
import com.rossomak.flashcards.core.ui.theme.spacing

@Composable
fun SyntaxCodeBlock(
    code: String,
    language: String,
    engine: SyntaxTokenizer,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = rememberSyntaxAnnotatedString(
            code = code,
            languageLabel = language,
            engine = engine,
            theme = SyntaxTheme.DefaultDark,
        ),
        style = MaterialTheme.typography.code,
        color = { CodeBlockColors.foreground },
        modifier = modifier
            .fillMaxWidth()
            .background(
                // Fixed dark surface: this block always renders SyntaxTheme.DefaultDark,
                // independent of the app light/dark theme (see CodeBlockColors).
                color = CodeBlockColors.background,
                shape = RoundedCornerShape(MaterialTheme.cornerRadius.small),
            )
            .padding(MaterialTheme.spacing.small),
    )
}

private const val PREVIEW_CODE = """fun greet(name: String): String {
    return "Hello, ${'$'}name!"
}"""

@ShowkaseComposable(name = "Syntax code block", group = "Code")
@Composable
fun SyntaxCodeBlockShowcase() {
    FlashcardsTheme {
        Surface {
            SyntaxCodeBlock(
                code = PREVIEW_CODE,
                language = "kotlin",
                engine = remember { SyntaxTokenizer() },
                modifier = Modifier.padding(MaterialTheme.spacing.normal),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun SyntaxCodeBlockPreview() {
    FlashcardsTheme {
        Surface {
            SyntaxCodeBlock(
                code = PREVIEW_CODE,
                language = "kotlin",
                engine = remember { SyntaxTokenizer() },
                modifier = Modifier.padding(MaterialTheme.spacing.normal),
            )
        }
    }
}
