@file:OptIn(ExperimentalComposeUiApi::class)

package com.rossomak.flashcards.core.ui.backdrop

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.modifier.modifierLocalConsumer
import androidx.compose.ui.modifier.modifierLocalOf
import androidx.compose.ui.modifier.modifierLocalProvider
import androidx.compose.ui.unit.Dp
import com.rossomak.flashcards.core.common.logw
import com.rossomak.flashcards.core.ui.theme.AppSizes
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.util.concurrent.atomic.AtomicBoolean

// The only file allowed to import `dev.chrisbanes.haze` (Konsist enforces it), so the library stays swappable.

private val ModifierLocalBackdropState = modifierLocalOf<HazeState?> { null }

/** Blur radius of every [blurBackdrop]; only the debug screen overrides the theme token. */
val LocalBackdropBlurRadius = staticCompositionLocalOf<Dp> { AppSizes.backdropBlurRadius }

/** Switches every [blurBackdrop] off when `false`; only the debug screen sets it. */
val LocalBackdropBlurEnabled = staticCompositionLocalOf { true }

private val missingSourceWarned = AtomicBoolean(false)

/**
 * Opens a scope for the [blurBackdropSource]s and [blurBackdrop]s below it; put it on the common
 * ancestor of a background and its components. It reaches subcomposed slots but not dialogs or
 * popups, and the nearest scope wins.
 */
fun Modifier.blurBackdropScope(): Modifier = composed {
    val hazeState = rememberHazeState()
    modifierLocalProvider(ModifierLocalBackdropState) { hazeState }
}

/**
 * Marks a background that [blurBackdrop] components in the same scope blur. A source records its
 * children too and a component cannot blur itself, so it must be a sibling layer behind the
 * content, placed before the drawing modifiers it captures:
 * `Modifier.fillMaxSize().blurBackdropSource().background(brush)`.
 */
fun Modifier.blurBackdropSource(): Modifier = composed {
    var hazeState by remember { mutableStateOf<HazeState?>(null) }
    modifierLocalConsumer { hazeState = ModifierLocalBackdropState.current }
        .then(hazeState?.let { Modifier.hazeSource(it) } ?: Modifier)
}

/**
 * Blurs the nearest [blurBackdropSource] behind this node, clipped to [shape]; the component's own
 * container is drawn over it. When [enabled] is `false`, below API 31, with blur switched off, or
 * outside a [blurBackdropScope] (logged once), it returns the receiver unchanged and attaches no
 * Haze node.
 */
fun Modifier.blurBackdrop(
    shape: Shape,
    enabled: Boolean = true,
): Modifier = if (!enabled) {
    this
} else {
    composed {
        var hazeState by remember { mutableStateOf<HazeState?>(null) }
        val radius = LocalBackdropBlurRadius.current
        val isBlurEnabled = LocalBackdropBlurEnabled.current
        val style = remember(radius) {
            HazeBlurStyle {
                blurEnabled(true)
                blurRadius(radius)
                noiseFactor(0f)
            }
        }
        WarnOnMissingSource(sourceFound = { hazeState != null })

        val resolvedState = hazeState
        modifierLocalConsumer { hazeState = ModifierLocalBackdropState.current }
            .then(
                when {
                    resolvedState == null || !isBlurEnabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> Modifier
                    else -> Modifier.clip(shape).hazeBlur(input = HazeInput.Sources(resolvedState), style = style)
                },
            )
    }
}

@Composable
private fun WarnOnMissingSource(sourceFound: () -> Boolean) {
    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (!sourceFound() && missingSourceWarned.compareAndSet(false, true)) {
            logw { "blurBackdrop found no ancestor blurBackdropScope; drawing no blur" }
        }
    }
}
