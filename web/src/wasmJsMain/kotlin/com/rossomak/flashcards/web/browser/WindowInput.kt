package com.rossomak.flashcards.web.browser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.window
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.WheelEvent

/**
 * Routes each vertical window `wheel` event where [target] says, by pointer x, before Compose sees it. Pinch zoom
 * stays with the browser. [onBrowserWheel] hears each one left to the browser, which scrolls by itself.
 */
@Composable
fun WindowWheelHandler(onBrowserWheel: () -> Unit = {}, target: (clientX: Double) -> WheelTarget) {
    val currentOnBrowserWheel by rememberUpdatedState(onBrowserWheel)
    val currentTarget by rememberUpdatedState(target)
    DisposableEffect(Unit) {
        val listener: (Event) -> Unit = { event ->
            val wheel = event as WheelEvent
            // Stops Compose's listener, which would prevent the default.
            if (!wheel.ctrlKey && wheel.deltaY != 0.0 && currentTarget(wheel.clientX.toDouble()) == WheelTarget.Browser) {
                currentOnBrowserWheel()
                wheel.stopImmediatePropagation()
            }
        }
        window.addEventListener(WHEEL_EVENT, listener, true)
        onDispose { window.removeEventListener(WHEEL_EVENT, listener, true) }
    }
}

/** Passes each unmodified `keydown` to [onKey]. A key it returns true for is kept from the browser. */
@Composable
fun WindowKeyHandler(onKey: (String) -> Boolean) {
    val currentOnKey by rememberUpdatedState(onKey)
    DisposableEffect(Unit) {
        val listener: (Event) -> Unit = { event ->
            val key = event as KeyboardEvent
            if (!key.ctrlKey && !key.altKey && !key.metaKey && currentOnKey(key.key)) {
                key.preventDefault()
                key.stopImmediatePropagation()
            }
        }
        window.addEventListener(KEY_DOWN_EVENT, listener, true)
        onDispose { window.removeEventListener(KEY_DOWN_EVENT, listener, true) }
    }
}

private const val WHEEL_EVENT = "wheel"
private const val KEY_DOWN_EVENT = "keydown"
