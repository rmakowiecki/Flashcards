package com.rossomak.flashcards.core.ui.composables.buttons

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle.OnSurface
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.cornerRadius
import com.rossomak.flashcards.core.ui.theme.spacing

/**
 * The commit button of an irreversible action, such as deleting the account: shaped like
 * [FlashcardsFilledButton], but filled with the error color instead of the CTA gradient.
 */
@Composable
fun FlashcardsDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: FlashcardsComponentSize = FlashcardsComponentSize.Normal,
    enabled: Boolean = true,
) {
    val metrics = size.metrics()
    Button(
        onClick = onClick,
        modifier = modifier.height(metrics.height),
        enabled = enabled,
        shape = RoundedCornerShape(MaterialTheme.cornerRadius.full),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
            disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = DISABLED_GRADIENT_ALPHA),
            disabledContentColor = disabledButtonContentColorFor(OnSurface),
        ),
        contentPadding = PaddingValues(horizontal = metrics.horizontalPadding, vertical = MaterialTheme.spacing.none),
    ) {
        FlashcardsButtonContent(text = text, icon = null, iconPosition = FlashcardsButtonIconPosition.Leading, metrics = metrics)
    }
}

@ShowkaseComposable(name = "Destructive", group = "Buttons")
@Composable
fun FlashcardsDestructiveButtonShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsDestructiveButton(text = "Delete", onClick = {})
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsDestructiveButtonPreview() {
    FlashcardsDestructiveButtonShowcase()
}
