// The History API takes a JS `state` value; passing `null` for it is Wasm–JS interop.
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.rossomak.flashcards.web.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.browser.window
import org.w3c.dom.events.Event

/**
 * Keeps the shown [WebRoute] and the browser's URL in step: [navigate] pushes a history entry, and
 * the browser's back and forward buttons (`popstate`) switch the page back.
 */
class BrowserNavigator internal constructor(initialRoute: WebRoute) {
    var route: WebRoute by mutableStateOf(initialRoute)
        private set

    fun navigate(target: WebRoute) {
        if (target == route) return
        window.history.pushState(null, "", target.path)
        route = target
    }

    internal fun onHistoryChange() {
        route = WebRoute.fromPath(window.location.pathname)
    }
}

/**
 * The site's navigator, starting on the page the URL names. An address that is not a page's
 * canonical path (`/privacy.html`, an unknown path) is rewritten in place to the page shown.
 */
@Composable
fun rememberBrowserNavigator(): BrowserNavigator {
    val navigator = remember {
        val initialRoute = WebRoute.fromPath(window.location.pathname)
        if (window.location.pathname != initialRoute.path) {
            window.history.replaceState(null, "", initialRoute.path)
        }
        BrowserNavigator(initialRoute)
    }
    DisposableEffect(navigator) {
        val onPopState: (Event) -> Unit = { navigator.onHistoryChange() }
        window.addEventListener(POP_STATE_EVENT, onPopState)
        onDispose { window.removeEventListener(POP_STATE_EVENT, onPopState) }
    }
    return navigator
}

private const val POP_STATE_EVENT = "popstate"
