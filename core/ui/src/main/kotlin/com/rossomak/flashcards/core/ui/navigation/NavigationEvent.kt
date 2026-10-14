package com.rossomak.flashcards.core.ui.navigation

/**
 * Marker for one-time navigation side effects.
 *
 * Navigation is modeled as a transient event delivered through a [kotlinx.coroutines.channels.Channel],
 * never merged into persistent UI state. Every per-screen destination sealed interface implements this
 * marker and is collected once in the UI via [observeAsEvents]. See
 * docs/adr/0019-navigation-as-one-time-events.md.
 */
interface NavigationEvent
