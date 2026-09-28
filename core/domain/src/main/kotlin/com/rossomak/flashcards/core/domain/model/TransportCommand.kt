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

/**
 * A [TransportCommand] without its arguments: what a transport surface can offer at a given
 * moment. Play and pause are separate, so a state can offer pause alone.
 */
enum class TransportCommandType { Play, Pause, Next, Previous, PreviousCard, JumpTo, Stop }

val TransportCommand.type: TransportCommandType
    get() = when (this) {
        TransportCommand.Play -> TransportCommandType.Play
        TransportCommand.Pause -> TransportCommandType.Pause
        TransportCommand.Next -> TransportCommandType.Next
        TransportCommand.Previous -> TransportCommandType.Previous
        TransportCommand.PreviousCard -> TransportCommandType.PreviousCard
        is TransportCommand.JumpTo -> TransportCommandType.JumpTo
        TransportCommand.Stop -> TransportCommandType.Stop
    }
