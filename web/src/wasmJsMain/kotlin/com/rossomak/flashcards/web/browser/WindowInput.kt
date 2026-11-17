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
 * Hands every vertical `wheel` event on the window to [target], with the pointer's distance from the
 * window's start edge in CSS pixels, before Compose sees it, while in composition, and sends the event
 * where [target] says. Pinch zoom (a wheel event with Ctrl held) is left to the browser.
 */
@Composable
fun WindowWheelHandler(target: (clientX: Double) -> WheelTarget) {
    val currentTarget by rememberUpdatedState(target)
    DisposableEffect(Unit) {
        val listener: (Event) -> Unit = { event ->
            val wheel = event as WheelEvent
            // Captured on the window, so Compose's own wheel listener, which prevents the default, never runs.
            if (!wheel.ctrlKey && wheel.deltaY != 0.0 && currentTarget(wheel.clientX.toDouble()) == WheelTarget.Browser) {
                wheel.stopImmediatePropagation()
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

private const val WHEEL_EVENT = "wheel"
private const val KEY_DOWN_EVENT = "keydown"
