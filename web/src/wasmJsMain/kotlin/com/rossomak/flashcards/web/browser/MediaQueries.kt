package com.rossomak.flashcards.web.browser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.browser.window
import org.w3c.dom.events.Event

/** Whether the browser's CSS media [query] matches, updated live when it changes (a system theme switch, say). */
@Composable
fun rememberMediaQuery(query: String): State<Boolean> {
    val mediaQueryList = remember(query) { window.matchMedia(query) }
    val matches = remember(mediaQueryList) { mutableStateOf(mediaQueryList.matches) }
    DisposableEffect(mediaQueryList) {
        val onChange: (Event) -> Unit = { matches.value = mediaQueryList.matches }
        mediaQueryList.addEventListener(CHANGE_EVENT, onChange)
        onDispose { mediaQueryList.removeEventListener(CHANGE_EVENT, onChange) }
    }
    return matches
}

/** The browser asks for a dark color scheme. */
@Composable
fun rememberPrefersDarkColorScheme(): State<Boolean> = rememberMediaQuery("(prefers-color-scheme: dark)")

/** The visitor asked the system for reduced motion. */
@Composable
fun rememberPrefersReducedMotion(): State<Boolean> = rememberMediaQuery("(prefers-reduced-motion: reduce)")

private const val CHANGE_EVENT = "change"
