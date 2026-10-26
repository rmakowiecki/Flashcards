package com.rossomak.flashcards.debugtools.showcase

import com.rossomak.flashcards.core.ui.dialog.DialogEvent
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Confirm
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Dismiss
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.DraftChange
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Open

/** The dialog on screen after [event]. Confirm applies nothing. */
internal fun <D> showcaseDialogAfter(event: DialogEvent<D>): D? = when (event) {
    is Open -> event.dialog
    is DraftChange -> event.dialog
    Confirm, Dismiss -> null
}
