# Settings

The Settings tab and the persisted study preferences that it and other screens edit. The Settings tab sets defaults only. The settings a concrete session runs with are chosen on the Preview Study Session Screen ([preview-study-session.md](preview-study-session.md)).

## Screen

The top app bar collapses as the list scrolls, and it can also be dragged directly. It carries the same faint network graph as the Account header ([account.md](account.md)). It carries one action: the User's avatar, showing their profile photo or, without one, their initials. Tapping it opens the Account screen ([account.md](account.md)). Sign-out, Account Deletion and Bug Reports live there, not here.

Below the bar are two grouped lists, **Study sessions** and **Voice**. Every row has the same shape: an icon, a title, its current value and a chevron. Tapping a row opens a focused dialog for that one value. There are no other controls on the screen.

**Study sessions**

| Row | What it sets | Dialog |
|---|---|---|
| Daily Goal | Minutes of study per day | Stepper |
| Session length | How many Flashcards a session draws | Stepper |
| Attempts per card | The Rated Attempts limit | Stepper |
| Partial re-queueing | Whether a Partial Rating re-inserts the Flashcard or ends it on the spot ([ADR-0044](../adr/0044-three-valued-terminal-state.md)) | Two option cards |
| Default study mode | Rated or Fast | Two option cards |
| Sort Order | Default, Easiest first or Hardest first ([ADR-0038](../adr/0038-one-sort-order-and-flashcard-selection-seam.md)) | Radio list |
| Quick Session topic count | The range a Quick Session samples its Subcategory count from ([ADR-0040](../adr/0040-quick-session-subcategory-sampling.md)) | Range slider |

**Voice**

| Row | What it sets | Dialog |
|---|---|---|
| Voice answering | Whether a Rated session starts with Voice Answering on | Two option cards |
| Read aloud | Whether a Fast session starts with read-aloud on | Two option cards |
| Voice | The speaking voice and its speed | Voice picker and speed slider |

Both delivery rows stay editable whatever the default study mode is. Each is the default for its own mode, and the mode itself is still chosen per session on Preview.

Each stepper and slider is bounded by the limits of its value.

## Dialogs and saving

A dialog edits a draft. Confirming saves the draft as the stored value straight away, and dismissing discards it. There is no "keep as default" checkbox on this screen, because every edit here is a default.

The dialog closes on confirm whether or not the save succeeds. A failed save shows a snackbar instead of leaving the dialog open, so a second confirm can never repeat the write. The rows always show the stored value, never the draft, so after a failed save a row still shows the old value.

## Voice dialog

The voice picker lists the device's English voices from a fixed set of countries, a few of the best per country. Each voice is named by its country and a number. While the list loads, the picker shows the saved voice's name. With no saved voice, the first voice in the list is picked. If the list cannot load, the picker offers no voices but the speed can still be changed; the next opening tries again.

Each change of voice or speed plays a short sample with the new values. Confirming or dismissing the dialog stops the sample.

The Voice row shows the voice name and the speed. It shows the speed alone when no voice is saved or the saved voice has no name yet. When a saved voice has no name, the screen looks it up in the device's voice list and saves the name. A voice that is no longer installed stays unnamed.

## Where else defaults are set

The same stored values are edited from other screens. A default saved anywhere is what every other screen shows next.

- **Preview Study Session Screen**: every study preference except the Daily Goal, through each dialog's "keep as default" checkbox ([preview-study-session.md](preview-study-session.md)). Preview reads the defaults once when it opens, so a default saved during one Preview never changes settings already adjusted there.
- **Subcategory Details**: Sort Order, through its Sort dialog's "keep as default" checkbox ([browse.md](browse.md)).
- **A spoken session**: the voice and speed, through the in-session voice dialog's "keep as default" checkbox ([study-session.md](study-session.md)).
- **Onboarding**: the default study mode and the Daily Goal ([onboarding-flow.md](onboarding-flow.md)).

## Storage

Every preference lives only on the device, never in Firestore. The Daily Goal reaches the server only inside each submitted session ([ADR-0048](../adr/0048-streak-and-daily-goal-ride-the-session-payload.md)). Preferences belong to the device, not to the User: they survive sign-out and Account Deletion, and the next User to sign in on the device starts with them. A stored value the app can no longer read falls back to its default.

The one-time privacy notice the Preview screen shows before the first voice-answering Start is remembered on the device as a separate flag, not as a setting with a row.

## Related

- [preview-study-session.md](preview-study-session.md): the per-session counterparts of these rows and the "keep as default" checkbox.
- [flashcard-selection.md](flashcard-selection.md): how Length, Sort Order and the topic count range shape a draw.
- [rated-mode.md](rated-mode.md): Attempts and Partial re-queueing.
- [fast-mode.md](fast-mode.md): read-aloud.
- [voice-answering.md](voice-answering.md): Voice Answering.
- [session-submission.md](session-submission.md): how the Daily Goal is judged.
- [account.md](account.md): the Account screen the avatar opens.
