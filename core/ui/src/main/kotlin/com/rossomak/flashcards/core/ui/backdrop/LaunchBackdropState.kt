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

/**
 * Where a [LaunchBackdropState] starts: the graph's [spec], whether the gradient is still to slide in,
 * and the transform the graph is drawn with before it settles to unscaled and unturned. The graph
 * always starts invisible.
 */
@Immutable
data class LaunchBackdropStart(
    val spec: MorpheusGraphSpec,
    val isGradientHidden: Boolean = false,
    val graphScale: Float = 1f,
    val graphRotationDegrees: Float = 0f,
)

/**
 * The one backdrop the launch screens (Splash, Onboarding and Login) share: the brand gradient over its
 * base color, and a network graph over the gradient. Owned by [LaunchBackdropHost] behind the nav host,
 * so moving between launch screens never swaps it: the graph's clock keeps running and each screen
 * morphs it from whatever is on screen.
 *
 * Besides the graph's spec, it carries what a spec cannot express: how far the gradient has slid in,
 * and the graph's opacity, scale and rotation, which Splash animates for its bloom. Scale and rotation
 * pivot on the center and transform the drawn graph, strokes and nodes included.
 */
@Stable
class LaunchBackdropState internal constructor(start: LaunchBackdropStart, graphAlpha: Float = 0f) {
    val graph: MorpheusGraphMorphState = MorpheusGraphMorphState(start.spec)

    /** 0 shows only the base color; 1 the gradient, slid fully into place. */
    val gradientReveal: Animatable<Float, *> = Animatable(if (start.isGradientHidden) 0f else 1f)
    val graphAlpha: Animatable<Float, *> = Animatable(graphAlpha)
    val graphScale: Animatable<Float, *> = Animatable(start.graphScale)
    val graphRotationDegrees: Animatable<Float, *> = Animatable(start.graphRotationDegrees)

    /**
     * Brings the graph to [spec] for a screen that has just arrived. When a graph is already shown, as
     * when another launch screen hands over, it morphs there from the screen as it is over
     * [HandoffAnimationSpec] and returns once it arrives. Otherwise it shows [spec] at once, still
     * invisible, for [fadeIn] to reveal.
     */
    suspend fun arriveAt(spec: MorpheusGraphSpec) {
        if (graphAlpha.targetValue > 0f) graph.animateTo(spec, HandoffAnimationSpec) else graph.snapTo(spec)
    }

    /** Fades the graph in over [animationSpec], unless it is already shown. */
    suspend fun fadeIn(animationSpec: AnimationSpec<Float>) {
        if (graphAlpha.targetValue < 1f) graphAlpha.animateTo(1f, animationSpec)
    }
}

/** The morph from one launch screen's graph to the next one's. */
private val HandoffAnimationSpec: AnimationSpec<Float> =
    tween(BrandMorpheusGraphSpecs.settleMorphDuration.inWholeMilliseconds.toInt(), easing = FastOutSlowInEasing)

/** The [LaunchBackdropHost]'s state while it shows one, otherwise `null`. */
private val LocalLaunchBackdrop = staticCompositionLocalOf<LaunchBackdropState?> { null }

/**
 * Draws the launch backdrop behind [content] while [isShown], and provides its state to the launch
 * screens in [content]. Each time it is shown it starts afresh from [start]. While shown it is also the
 * [blurBackdropScope] for [content], so launch screens blur the graph behind them.
 */
@Composable
fun LaunchBackdropHost(isShown: Boolean, start: LaunchBackdropStart, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = remember(isShown) { if (isShown) LaunchBackdropState(start) else null }
    Box(modifier = modifier.fillMaxSize().then(if (state != null) Modifier.blurBackdropScope() else Modifier)) {
        if (state != null) LaunchBackdropLayer(state)
        CompositionLocalProvider(LocalLaunchBackdrop provides state, content = content)
    }
}

/**
 * The [LaunchBackdropHost]'s state, or, with no host (previews, the screen showcase), one of the
 * screen's own made from [start], for [LaunchBackdropBox] to draw. The host starts from its own
 * [LaunchBackdropStart], so [start] only matters without one. Under inspection the screen's own one is
 * shown settled, since a preview never runs its animations.
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

/**
 * The root of a launch screen. Over the host's backdrop it is a plain full-size box; for a screen's own
 * [state] it draws the backdrop itself, under its own [blurBackdropScope].
 */
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
