# Curation Requests

How a User flags a global Flashcard for a specific content fix from inside a Study Session, and what the app sends. Each report is a **Curation Request** made of one or more **Curation Actions**. This is separate from a **Bug Report**, which describes a problem with the app rather than with one Flashcard.

## Entry point

Every Rated and Fast session header carries a flag icon at the end of its top bar, opposite the close button. It is shown only while a Flashcard is on screen and is hidden otherwise. Tapping it opens the Report a problem dialog for the Flashcard currently shown. The entry sits in the header because the session's bottom sheet is already crowded with transport and voice controls ([ADR-0017](../adr/0017-curation-report-system.md)).

Opening the dialog does not pause a spoken session. Like every session dialog, it holds the session at its auto-advance point, and the session moves on shortly after the dialog closes (see [study-session.md](study-session.md)).

## The dialog

The dialog lists every Curation Action as a checkable row with an icon and a label, in a fixed order: the two difficulty actions first, then content problems in rising severity, with deleting the card last. Every Curation Action has a row: the dialog checks its rows against the full set of actions and fails fast if one is missing, so a new action cannot be added without a way to report it.

- **The draft always starts empty.** It never shows what the User reported before, so a checked row only ever means "report this now" and an unchecked row never means "withdraw".
- **The difficulty pair is exclusive.** Checking Raise the difficulty clears Lower the difficulty and the reverse. The other actions toggle independently and may be combined.
- **Submit** is enabled once at least one row is checked. **Cancel** discards the draft and writes nothing.

## Sending

Submit sends the checked actions as one write that adds them to the User's Curation Request for that Flashcard. The write is additive: it never removes an action reported earlier, except that a difficulty action replaces its opposite so the stored pair stays exclusive too.

- **While sending**, the dialog stays open with its rows and Submit disabled, so the checked set the User sees is the one being sent.
- **On success** the dialog closes.
- **On failure** the dialog stays open with the draft intact and Submit enabled again, and a snackbar says the report failed.
- **Dismissed while sending**, the dialog closes at once and releases the held session. A later failure still shows the snackbar.
- **Offline**, a write the server has not acknowledged after a short wait counts as sent and the dialog closes. Firestore keeps the write queued and delivers it when the device reconnects.

**Resubmitting is skipped when nothing is new.** The app remembers, for as long as the process lives, which actions each Flashcard was last known to carry. A card's first submission reads its stored request once to learn this, and a failed read fails the submission. A submission whose actions are all already on record makes no write. Only writes the server has acknowledged update this memory, so a write still queued offline is sent again if the User resubmits.

## What a report does not do

- **No suppression.** A reported Flashcard keeps appearing in Study Sessions.
- **No withdrawal.** Because the draft never shows earlier reports, the app has no way to remove a reported action. Withdrawal is done by admin tooling on the stored requests.
- **No management screen.** Curation Requests are read only by admin sync scripts and are never shown back to the User.
- **Per card only.** There is no bulk report from a list of Flashcards.

## Related

- [ADR-0017](../adr/0017-curation-report-system.md): the report system and its rejected alternatives.
- [study-session.md](study-session.md): the session header and the dialog hold.
- [firestore-schema.md](firestore-schema.md): how a Curation Request is stored.
- `CONTEXT.md`: Curation Request, Curation Action, Bug Report.
