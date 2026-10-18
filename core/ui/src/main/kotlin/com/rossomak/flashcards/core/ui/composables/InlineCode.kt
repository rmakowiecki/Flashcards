package com.rossomak.flashcards.core.ui.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.rossomak.flashcards.core.designsystem.theme.InlineCodeSpanStyle

/** Pads the code background on each side; a full monospace space makes the span look bulky. */
private const val THIN_SPACE = ' '

@Composable
fun String.withInlineCode(): AnnotatedString {
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val codeColor = MaterialTheme.colorScheme.onSurfaceVariant
    return remember(this, codeBackground, codeColor) {
        buildAnnotatedString {
            val source = this@withInlineCode
            var cursor = 0
            while (cursor < source.length) {
                val open = source.indexOf('`', cursor)
                if (open == -1) {
                    append(source.substring(cursor))
                    break
                }
                append(source.substring(cursor, open))
                val close = source.indexOf('`', open + 1)
                if (close == -1) {
                    append(source.substring(open))
                    break
                }
                val codeText = source.substring(open + 1, close)
                pushStyle(InlineCodeSpanStyle.merge(SpanStyle(background = codeBackground, color = codeColor)))
                append("$THIN_SPACE$codeText$THIN_SPACE")
                pop()
                cursor = close + 1
            }
        }
    }
}
