package com.rossomak.flashcards.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

/** Mounts the website onto the page body; index.html loads the script that calls this. */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(requireNotNull(document.body) { "index.html has no body to mount the site on" }) {
        WebApp()
    }
}
