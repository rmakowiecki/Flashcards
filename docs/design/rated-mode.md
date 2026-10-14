# Rated Mode

Rated Study Sessions: how the user answers, how each answer becomes a Rating, how the queue re-inserts weaker cards, and how a Flashcard reaches its Terminal State. These queue rules apply to a manual session and a Voice Answering session alike. What both modes share on screen, and how a session loads, ends and is left, is in [study-session.md](study-session.md).

## Answering

A Rated session has one of two deliveries, fixed when it starts on the Preview Study Session Screen.

- **Manual**: the bottom panel offers Show answer. Once the answer shows, the panel offers the three Ratings, Failed, Partial and Correct, and a Rating moves straight on to the next card, with no separate Next. Manual has no skip: the user moves on only by rating.
- **Voice Answering**: the question is read aloud, the app listens, and the spoken answer is transcribed and graded. The grade becomes the card's Rating exactly as a manual tap would ([ADR-0026](../adr/0026-voice-grade-unifies-with-rating-reveal-tied-to-speech-end.md)). The card stays on screen while its grading feedback is spoken, and the queue moves only once that feedback has finished. The round itself, its transport controls and its pauses are in [voice-answering.md](voice-answering.md).

Every completed Attempt carries exactly one Rating, and each Rating fills the next of the card's Attempt slots. Moving to another card hides the answer again.

## The queue

The session's queue starts as the Flashcards Preview drew, in Preview's order. The card at its head is the one on screen. After a Rating the head either leaves the queue with a Terminal State, or goes back into the queue further ahead.

- **Correct** on any Attempt ends the card as **Mastered**.
- **Failed** or **Partial** re-inserts the card at a random distance ahead, with at least two other cards before it returns. Failed returns sooner than Partial, so weaker recall is asked again first. When fewer cards remain than the drawn distance, the card goes to the end of the queue. The ranges and the reasoning are in [ADR-0046](../adr/0046-failed-and-partial-re-insertion-placement.md).
- **Partial re-queueing turned off**: a Partial Rating ends the card as **Partial** on the spot instead of re-inserting it. The setting is chosen on Preview, with its default in Settings ([settings.md](settings.md)).
- **Attempts limit reached**: a card that has used every Attempt it is allowed ends on its best Rating rather than being re-inserted. The limit is chosen the same way as the setting above.

The session completes when every Flashcard has reached a Terminal State and the queue is empty.

## Terminal State

A Flashcard's Terminal State is the **best Rating it ever achieved** in the session, not its last one: Correct makes it Mastered, otherwise any Partial makes it Partial, otherwise it is Failed. Reaching the Attempts limit resolves the accumulated Ratings and never forces Failed, so a card rated Failed, then Partial, then Failed ends Partial ([ADR-0044](../adr/0044-three-valued-terminal-state.md)). What each Terminal State does to Card Progress, Persistent Mastery and XP is in [session-submission.md](session-submission.md) and [xp-leveling-system.md](xp-leveling-system.md).

## Moves that are not Ratings

Only a Rating uses an Attempt. These moves take none and record nothing:

- **Next**, offered with Voice Answering only, at the question: the card is re-inserted unanswered, using the Failed distance.
- **Silence**: when no answer is heard in time, the card is re-inserted the same way.
- **Grading failure**: when an answer cannot be graded, the card is re-inserted the same way.
- **Previous**, with Voice Answering only: the current question starts again in place.
- **Microphone or voice engine failure**: the session pauses on the current card without re-inserting it, and the card's question starts again when the user resumes.

A card nobody answers never reaches a Terminal State, since only a Rating can resolve it, so it keeps returning until it is rated or the user leaves the session. Repeated silences or grading failures in a row pause voice answering, so a session left unattended does not loop on its own.

## Related

- [study-session.md](study-session.md): the screen both modes share, loading, leaving a session and termination.
- [voice-answering.md](voice-answering.md): the Voice Answering round.
- [preview-study-session.md](preview-study-session.md): where the delivery, Attempts limit and Partial re-queueing are chosen.
- [settings.md](settings.md): their saved defaults.
- [fast-mode.md](fast-mode.md): the other Study Mode.
- [session-submission.md](session-submission.md): what a Terminal State writes.
