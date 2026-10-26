package com.rossomak.flashcards.core.ui.backdrop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import com.rossomak.flashcards.core.designsystem.theme.brandColors
import com.rossomak.morpheus.MorpheusGraphMorphState
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.morpheusGraphBackground

/** A [LaunchBackdropState]'s starting pose. The graph always starts invisible. */
@Immutable
data class LaunchBackdropStart(
    val spec: MorpheusGraphSpec,
    val isGradientHidden: Boolean = false,
    val graphScale: Float = 1f,
    val graphRotationDegrees: Float = 0f,
)

/**
 * The gradient and graph Splash, Onboarding and Login share, so the graph morphs between them instead of
 * being swapped. Carries the transform a Morpheus spec can't express. See ADR-0073.
 */
@Stable
class LaunchBackdropState internal constructor(start: LaunchBackdropStart, graphAlpha: Float = 0f) {
    val graph: MorpheusGraphMorphState = MorpheusGraphMorphState(start.spec)

    /** 0 shows only the base color; 1 the gradient, slid fully in. */
    val gradientReveal: Animatable<Float, *> = Animatable(if (start.isGradientHidden) 0f else 1f)
    val graphAlpha: Animatable<Float, *> = Animatable(graphAlpha)
    val graphScale: Animatable<Float, *> = Animatable(start.graphScale)
    val graphRotationDegrees: Animatable<Float, *> = Animatable(start.graphRotationDegrees)

    /** Morphs to [spec] when a graph is shown, returning once it arrives; otherwise snaps to it, still invisible, for [fadeIn]. */
    suspend fun arriveAt(spec: MorpheusGraphSpec) {
        if (graphAlpha.targetValue > 0f) graph.animateTo(spec, HandoffAnimationSpec) else graph.snapTo(spec)
    }

    /** Does nothing when the graph is already shown, as after a handover. */
    suspend fun fadeIn(animationSpec: AnimationSpec<Float>) {
        if (graphAlpha.targetValue < 1f) graphAlpha.animateTo(1f, animationSpec)
    }
}

private val HandoffAnimationSpec: AnimationSpec<Float> =
    tween(BrandMorpheusGraphSpecs.settleMorphDuration.inWholeMilliseconds.toInt(), easing = FastOutSlowInEasing)

private val LocalLaunchBackdrop = staticCompositionLocalOf<LaunchBackdropState?> { null }

/** Draws the launch backdrop behind [content] while [isShown], starting afresh from [start] each time. */
@Composable
fun LaunchBackdropHost(isShown: Boolean, start: LaunchBackdropStart, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = remember(isShown) { if (isShown) LaunchBackdropState(start) else null }
    Box(modifier = modifier.fillMaxSize().then(if (state != null) Modifier.blurBackdropScope() else Modifier)) {
        if (state != null) LaunchBackdropLayer(state)
        CompositionLocalProvider(LocalLaunchBackdrop provides state, content = content)
    }
}

/**
 * The host's state, ignoring [start], or without a host (previews, the showcase) the screen's own from
 * [start], shown settled under inspection since previews run no animations.
 */
@Composable
fun rememberLaunchBackdropState(start: LaunchBackdropStart): LaunchBackdropState {
    LocalLaunchBackdrop.current?.let { hostState -> return hostState }
    val isInspecting = LocalInspectionMode.current
    return remember {
        if (isInspecting) {
            LaunchBackdropState(LaunchBackdropStart(start.spec), graphAlpha = 1f)
        } else {
            LaunchBackdropState(start)
        }
    }
}

/** A launch screen's root: draws the backdrop itself only when [state] is not the host's. */
@Composable
fun LaunchBackdropBox(
    state: LaunchBackdropState,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    if (LocalLaunchBackdrop.current === state) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = contentAlignment, content = content)
    } else {
        Box(modifier = modifier.fillMaxSize().blurBackdropScope(), contentAlignment = contentAlignment) {
            LaunchBackdropLayer(state)
            content()
        }
    }
}

@Composable
private fun BoxScope.LaunchBackdropLayer(state: LaunchBackdropState) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .blurBackdropSource()
            .background(MaterialTheme.brandColors.screenGradientBase),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val reveal = state.gradientReveal.value
                    alpha = reveal
                    translationY = (1f - reveal) * size.height
                }
                .background(MaterialTheme.brandColors.screenGradient),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    alpha = state.graphAlpha.value
                    scaleX = state.graphScale.value
                    scaleY = state.graphScale.value
                    rotationZ = state.graphRotationDegrees.value
                }
                .morpheusGraphBackground(state.graph, BrandMorpheusGraphSpecs.fill),
        )
    }
}
