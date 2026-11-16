// Reading the legacy `wheelDeltaY` field, which the DOM bindings leave out, is Wasm–JS interop.
@file:OptIn(ExperimentalWasmJsInterop::class)

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
 * Takes every vertical `wheel` event on the window before Compose sees it, while in composition, and
 * hands it to [onWheel]. Pinch zoom (a wheel event with Ctrl held) is left to the browser.
 */
@Composable
fun WindowWheelHandler(onWheel: (WheelReading) -> Unit) {
    val currentOnWheel by rememberUpdatedState(onWheel)
    DisposableEffect(Unit) {
        val listener: (Event) -> Unit = { event ->
            val wheel = event as WheelEvent
            if (!wheel.ctrlKey && wheel.deltaY != 0.0) {
                // Captured on the window, so Compose's own wheel scrolling never runs. The window's wheel
                // listeners are passive, so there is no default to prevent; the page never scrolls natively.
                wheel.stopImmediatePropagation()
                currentOnWheel(WheelReading(deltaY = wheel.deltaY, deltaMode = wheel.deltaMode, wheelDeltaY = readWheelDeltaY(wheel)))
            }
        }
        window.addEventListener(WHEEL_EVENT, listener, true)
        onDispose { window.removeEventListener(WHEEL_EVENT, listener, true) }
    }
}

/**
 * Hands every `keydown` without a modifier (Ctrl, Alt, Meta) to [onKey] as its `KeyboardEvent.key`
 * while in composition; a key [onKey] returns true for is kept from the browser.
 */
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

// The parameter is read by name inside the JavaScript body, which detekt cannot see.
@Suppress("UnusedParameter")
private fun readWheelDeltaY(event: WheelEvent): Double = js("event.wheelDeltaY || 0")

private const val WHEEL_EVENT = "wheel"
private const val KEY_DOWN_EVENT = "keydown"
