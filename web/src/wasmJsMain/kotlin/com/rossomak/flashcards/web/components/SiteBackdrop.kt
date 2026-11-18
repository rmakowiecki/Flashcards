package com.rossomak.flashcards.web.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.rossomak.flashcards.core.designsystem.theme.brandColors
import com.rossomak.morpheus.ExperimentalMorpheusGraphApi
import com.rossomak.morpheus.LocalMorpheusGraphFill
import com.rossomak.morpheus.MorpheusGraphMorphState
import com.rossomak.morpheus.morpheusGraphBackground

/** The gradient and graph behind every page, as one [backdrop] layer for frosted components. */
@OptIn(ExperimentalMorpheusGraphApi::class)
@Composable
fun SiteBackdrop(modifier: Modifier = Modifier, backdrop: BlurBackdrop, graphState: MorpheusGraphMorphState, graphAlpha: () -> Float, showViolations: Boolean) {
    Box(modifier = modifier.fillMaxSize().blurBackdropSource(backdrop).background(MaterialTheme.brandColors.screenGradient)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = graphAlpha() }
                .morpheusGraphBackground(state = graphState, fill = LocalMorpheusGraphFill.current, stats = null, showViolations = showViolations),
        )
    }
}
