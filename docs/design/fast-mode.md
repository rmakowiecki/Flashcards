# Fast Mode

Fast Study Sessions: going through the deck question, answer, next card, either by tapping through or hands-free with read-aloud. Fast has no Ratings, Attempts or Terminal States. Its only record per card is that the Flashcard became **Studied** once its answer was shown ([ADR-0016](../adr/0016-card-progress-model.md)). What both modes share, including the system controls and audio interruptions of a spoken session, is in [study-session.md](study-session.md).

Whether read-aloud is on is chosen on the Preview Study Session Screen and fixed for the session ([preview-study-session.md](preview-study-session.md)). The session walks the drawn Flashcards once, in Preview's order.

## Tap-through

Without read-aloud the session never speaks. The bottom panel offers Show answer, then Next once the answer shows. Next does nothing until the answer shows, so every card the User moves past is Studied. On the last card, Next ends the session. Tap-through has no Previous.

## Read-aloud

With read-aloud on, the session starts speaking as soon as its cards load and runs one loop per card: it reads the question, pauses, reads the answer, pauses again, then moves to the next card. The end of that second pause is the card's auto-advance point, where an open dialog holds the session ([study-session.md](study-session.md#dialogs)). After the last card's answer and its pause, the session ends.

A Flashcard can carry a spoken version of its question or answer, written to be read aloud. Read-aloud uses it when present and the written text otherwise. Code formatting marks are never read out.

A card becomes Studied the moment its answer is presented, whether it is read in full or skipped past at once.

The bottom panel holds the transport row (previous, play or pause, next) and a voice settings button. The same commands reach the session from the notification, the lock screen and a headset.

- **Next**: at a question, or in the pause after it, presents that card's answer. At an answer, or in the pause after it, moves to the next card. At the last card's answer it is not offered, because the session ends only once that answer has been read.
- **Previous**: restarts the current card from its question once the card has played for a moment, or on the first card. Pressed sooner, it goes back to the previous card. The notification's separate go-back-one control always goes back a card.
- **Jump**: a controller that lists the session's cards can jump to any of them. That card starts from its question.
- **Pause and play**: a pause taken during a part reads that part again from its start on play. A pause taken during one of the silent pauses goes on to the next step on play: the answer, or the next card.

Moving back never undoes Studied, and a card skipped by a jump without its answer showing is not Studied.

## Related

- [study-session.md](study-session.md): the shared screen, dialogs, spoken sessions and termination.
- [rated-mode.md](rated-mode.md): the other Study Mode.
- [preview-study-session.md](preview-study-session.md): where read-aloud, the voice and the speed are chosen.
- [settings.md](settings.md): their saved defaults.
- [session-submission.md](session-submission.md): what a Fast session's Studied cards write.
