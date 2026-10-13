package com.rossomak.networkgraph.tuner

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.rossomak.networkgraph.NetworkGraphFill

/**
 * One surface the [NetworkGraphPlayground] shows a graph on: the [fill] its graphs inherit, what is painted
 * under it ([barBackground] behind the app bar strip, [heroBackground] behind the tall hero and the
 * deck pages) and the [contentColor] of the text and icons drawn over it. The consumer supplies these,
 * so the playground carries its brand without knowing it.
 */
@Immutable
public class NetworkGraphPlaygroundStyle(
    public val name: String,
    public val fill: NetworkGraphFill,
    public val barBackground: Brush,
    public val heroBackground: Brush,
    public val contentColor: Color,
)
