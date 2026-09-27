package com.rossomak.flashcards.core.ui.composables.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme

/**
 * An indeterminate progress spinner on the same disc as [FlashcardsVoiceCaptureIndicator], for
 * waiting on the result of a spoken answer. Same size and color as the capture indicator's disc and
 * as a Rating circle, so any of them can replace another in place. Does not pulse.
 */
@Composable
fun FlashcardsVoiceProgressDisc(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(FlashcardsVoiceCaptureIndicatorDefaults.discSize)
            .background(color = FlashcardsVoiceCaptureIndicatorDefaults.discContainerColor, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(ProgressSize),
            color = FlashcardsVoiceCaptureIndicatorDefaults.discContentColor,
            strokeWidth = ProgressStrokeWidth,
        )
    }
}

private val ProgressSize = 24.dp
private val ProgressStrokeWidth = 3.dp

@ShowkaseComposable(name = "Voice progress disc", group = "Feedback")
@Composable
fun FlashcardsVoiceProgressDiscShowcase() {
    VoiceProgressDiscPreview()
}

@PreviewLightDark
@Composable
private fun FlashcardsVoiceProgressDiscPreview() {
    VoiceProgressDiscPreview()
}

@Composable
private fun VoiceProgressDiscPreview() {
    FlashcardsTheme {
        Surface {
            FlashcardsVoiceProgressDisc()
        }
    }
}
