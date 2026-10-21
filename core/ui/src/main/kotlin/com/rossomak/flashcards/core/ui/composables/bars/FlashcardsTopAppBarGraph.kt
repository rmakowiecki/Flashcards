package com.rossomak.flashcards.core.ui.composables.bars

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rossomak.flashcards.core.ui.backdrop.BrandMorpheusGraphSpecs
import com.rossomak.morpheus.MorpheusGraphFill
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.morpheusGraphBackground

/** A network graph backdrop, clipped as the bar collapses. */
@Composable
fun FlashcardsTopAppBarGraph(
    spec: MorpheusGraphSpec,
    fill: MorpheusGraphFill,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().morpheusGraphBackground(spec, fill))
}

@Composable
fun FlashcardsTopAppBarOnSurfaceGraph(modifier: Modifier = Modifier) {
    FlashcardsTopAppBarGraph(spec = BrandMorpheusGraphSpecs.topAppBarOnSurface, fill = BrandMorpheusGraphSpecs.onSurfaceFill, modifier = modifier)
}

@Composable
fun FlashcardsTopAppBarBrandGraph(modifier: Modifier = Modifier) {
    FlashcardsTopAppBarGraph(spec = BrandMorpheusGraphSpecs.topAppBar, fill = BrandMorpheusGraphSpecs.fill, modifier = modifier)
}
