package com.rossomak.flashcards.core.domain.model

/** A transport command from outside the app (a headset, the notification, the lock screen). */
sealed interface TransportCommand {
    data object Play : TransportCommand
    data object Pause : TransportCommand
    data object Next : TransportCommand

    /** Restart the current card, or go to the previous one right after it started. */
    data object Previous : TransportCommand

    /** Go to the previous card, whatever the time on the current one. */
    data object PreviousCard : TransportCommand

    /** Go to the card at [index] of the player's list. */
    data class JumpTo(val index: Int) : TransportCommand

    data object Stop : TransportCommand
}
