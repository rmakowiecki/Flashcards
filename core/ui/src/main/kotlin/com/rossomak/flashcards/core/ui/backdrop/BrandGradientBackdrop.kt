package com.rossomak.flashcards.core.ui.backdrop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rossomak.flashcards.core.ui.theme.brandColors

/** The brand gradient as a [blurBackdropSource]; the first child of a `Box` carrying [blurBackdropScope]. */
@Composable
fun BoxScope.BrandGradientBackdrop(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .matchParentSize()
            .blurBackdropSource()
            .background(MaterialTheme.brandColors.screenGradient),
    )
}
