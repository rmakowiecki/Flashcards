package com.rossomak.flashcards.web.browser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.window
import org.w3c.dom.events.Event

/** Calls [onPointerDown] for every press anywhere in the window, before Compose sees it, so a tap elsewhere can dismiss something. */
@Composable
fun WindowPointerDownHandler(onPointerDown: () -> Unit) {
    val currentOnPointerDown by rememberUpdatedState(onPointerDown)
    DisposableEffect(Unit) {
        val listener: (Event) -> Unit = { currentOnPointerDown() }
        window.addEventListener(POINTER_DOWN_EVENT, listener, true)
        onDispose { window.removeEventListener(POINTER_DOWN_EVENT, listener, true) }
    }
}

private const val POINTER_DOWN_EVENT = "pointerdown"
