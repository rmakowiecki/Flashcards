package com.rossomak.flashcards.core.ui.composables

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.spacing

/**
 * All-caps, muted overline label used as a header of list sections and as a count line under a top
 * bar. The caller passes the human-readable [text]; the component applies the uppercase treatment
 * and, unless [isHeading] is false, marks the node as an accessibility heading. Pass
 * `isHeading = false` for a count or status line that does not head a section.
 *
 * The label owns all of its own spacing: the page gutter on both sides, the space above it that
 * separates it from the previous section, and the space below it that ties it to its content.
 * Callers add no padding or arrangement spacing around it and place it in a container that adds no
 * horizontal padding of its own. The content below must use the same [com.rossomak.flashcards.core.ui.theme.Spacing.normal]
 * gutter, so the label text lines up with the list's left edge.
 */
@Composable
fun FlashcardsOverlineLabel(
    text: String,
    modifier: Modifier = Modifier,
    isHeading: Boolean = true,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .then(if (isHeading) Modifier.semantics { heading() } else Modifier)
            .padding(
                start = MaterialTheme.spacing.normal,
                top = MaterialTheme.spacing.normal,
                end = MaterialTheme.spacing.normal,
                bottom = MaterialTheme.spacing.xsmall,
            ),
    )
}

@ShowkaseComposable(name = "Overline label", group = "Labels")
@Composable
fun FlashcardsOverlineLabelShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsOverlineLabel(text = "Study sessions")
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsOverlineLabelPreview() {
    FlashcardsTheme {
        Surface {
            FlashcardsOverlineLabel(text = "Study sessions")
        }
    }
}
