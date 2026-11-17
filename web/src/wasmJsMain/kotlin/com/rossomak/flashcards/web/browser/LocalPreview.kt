package com.rossomak.flashcards.web.browser

import kotlinx.browser.window

/** Whether the site is served from this machine, by `wasmJsBrowserDevelopmentRun`, rather than from Firebase Hosting. */
fun isLocalPreview(): Boolean = window.location.hostname.let { hostname -> hostname == "localhost" || hostname == "127.0.0.1" }
