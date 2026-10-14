# Study Session

What Rated and Fast Study Sessions share: the session screen's header, card and dialogs, how a session loads, how the user leaves it, and how it terminates. What each mode does with the bottom panel is in [rated-mode.md](rated-mode.md) and [fast-mode.md](fast-mode.md).

A session is scoped to one Category and one or more Subcategories. Its Flashcards, their order and its Study Mode are settled on the Preview Study Session Screen before it begins ([preview-study-session.md](preview-study-session.md), [flashcard-selection.md](flashcard-selection.md)). Fast and Rated are two separate screens and routes, so Study Mode cannot change mid-session; they share the header, the card and the dialogs but not an interaction model ([ADR-0045](../adr/0045-separate-fast-and-rated-session-screens.md)).

## Loading

The session receives the drawn Flashcards from Preview and loads them for its Subcategories, keeping Preview's order. A spinner shows meanwhile. If the Flashcards cannot be loaded, or none of the drawn ones is found, the session returns to Preview, which keeps its draw and settings.

At the same time the session reads the User's **Card Progress** for every Subcategory in scope, so it knows which Flashcards are new to the User and, in a Rated session, which were already Mastered. A failed progress read never stops the session: it runs as if the User had no prior progress. The server decides both facts again from its own stored Card Progress when the session is delivered ([session-submission.md](session-submission.md)).

## Screen

The screen stays on while a session runs.

**Header.** A close button, a title naming the Category and the current Flashcard's own Subcategory (in a session with several Subcategories this changes from card to card), and a flag icon that opens Report a problem for the current Flashcard ([curation-requests.md](curation-requests.md)). Below them a progress counter with a bar, then the current Flashcard's Tags. In a Rated session the counter shows how many Flashcards have reached a Terminal State out of the session's distinct Flashcards; re-insertion grows the Rated queue, so the total counts Flashcards, not queue entries. In a Fast session it shows the current card's position in the deck.

**Card.** The current Flashcard sits in a single card showing the question with its Difficulty, and in a Rated session the card's Attempt slots: one per Attempt allowed, filled with each past Rating, the current Attempt marked. Revealing the answer expands the card downward to add the answer below the question, and the question's label loses its emphasis. Code blocks in either part are syntax highlighted. Content too long for the screen scrolls inside the card while the bottom panel stays pinned. When the Flashcard has Extended Context, the revealed answer offers a **Learn more** link that opens it in a dialog.

**Bottom panel.** A fixed panel below the card whose content depends on the Study Mode and its delivery.

## Dialogs

Opening a dialog never pauses reading aloud. It asks the session to **hold at its auto-advance point**: the current card is read to its end, and where the session would move on it stops instead. When the dialog closes, a held session keeps the old card on screen for a moment, then moves on ([ADR-0013](../adr/0013-extended-context-dialog-voice-interaction.md)).

The voice settings dialog, offered in the spoken variants only, is the exception: it pauses playback while it is open, plays a sample on each edit, and applies its voice and speed to the rest of this session, with a "keep as default" checkbox that also saves them as the User's default.

## Spoken sessions

A Fast session with read-aloud and a Rated session with Voice Answering both speak. This section covers what the two share. What each one reads and when it moves on is in [fast-mode.md](fast-mode.md) and [voice-answering.md](voice-answering.md).

**Speech and background play.** Speech comes from the device's text-to-speech engine, in English, with the voice and speed chosen on Preview or in the voice settings dialog. A spoken session keeps playing with the screen off or the app in the background. One foreground playback service hosts it, adding microphone use for Voice Answering, and keeps the device awake exactly while the session plays, including the silent pauses between parts ([ADR-0012](../adr/0012-tts-mediasession-stack-for-fast-mode.md)). No notification permission is requested, because a media playback notification is exempt from it.

**System controls.** The session appears to Android as one media session, built on Media3, with each Flashcard as one item in its list. That single media session drives every control outside the app:

- the media notification;
- the lock-screen media card;
- headset and Bluetooth buttons;
- other media controllers, such as a watch or a car.

The notification and the lock-screen card show the current Flashcard's Subcategory as the title. The subtitle shows progress: the card's position in a Fast session, or the Flashcards that have reached a Terminal State out of the session's distinct Flashcards in a Rated one. The notification stays ongoing while the session runs, so it cannot be swiped away, and tapping it opens the app.

The player never acts on a command itself. A command from any of these surfaces is handed to the session, which applies it exactly as it applies the matching in-app button. The session decides which commands are on offer at each moment. It gives that same set to the in-app transport row and to the media session, so the app and the system controls never disagree. System surfaces hide a button that is not on offer rather than greying it out, and a command that arrives outside the set anyway is dropped. A controller's stop, including dismissing the notification where the system allows it, only pauses.

The commands are play, pause, next, previous, going back one item, and jumping to an item. Going back one item is the notification's previous, and jumping to an item comes from a controller that lists the session's cards. A Rated session never offers those two, because its queue decides the order. What next and previous do in each mode is in [fast-mode.md](fast-mode.md) and [rated-mode.md](rated-mode.md).

**Audio interruptions.** The player takes audio focus but never pauses or resumes on a focus change itself. The session classifies what other apps and the device do by severity ([ADR-0054](../adr/0054-study-session-rules-in-domain-coordinators.md)):

- A short sound, such as a notification or a navigation prompt, lets the session carry on. While Voice Answering listens, the microphone ignores it. One that lasts long over speech counts as an interruption.
- The Assistant, an alarm, a ringing call or another app taking the microphone pauses the session as a user pause would. If it ends within a short window, the session resumes on its own.
- An answered call, or another media app taking over, pauses the session and leaves it paused.
- A disconnected headset pauses the session, which never resumes on the device speaker by itself.

While a call rings or runs, no control is offered anywhere, so nothing can start playing, and the User is told once as the call starts. A session that opens during a call starts blocked. A pause or play from the User during an interruption takes over, and the session no longer resumes on its own.

**Voice engine failure.** If the text-to-speech engine becomes unavailable, the session pauses and tells the User, with a link to the system's text-to-speech settings. Its delivery never changes: a spoken session never falls back to tapping through or self-rating. Play restarts the voice at the current card's question.

## Leaving a session

The close button and system back both open an exit confirmation; there is no in-session finish button. Confirming ends the session early.

## Session termination

Both Study Modes terminate the same way: the session seals its Flashcard Results, stamps its duration, hands the **Session Result** to the Session Summary screen, and writes nothing itself. What happens to the result next, including how duration is counted, is in [session-submission.md](session-submission.md).

- **Natural end**: Rated, when every Flashcard has reached a Terminal State. Fast, when the session moves on from the last card's answer. Both open the Session Summary screen.
- **Early exit** (confirmed exit dialog): the result carries everything so far and is marked abandoned. The rule differs by mode:
  - **Rated**: a Flashcard the User never completed an Attempt on is left out. A Flashcard that already completed at least one Attempt is resolved using its best Rating so far, the same rule a natural end uses, rather than discarded: it is already **Studied**, so dropping it at exit would contradict that definition.
  - **Fast**: there are no Attempts or Ratings to resolve. Every Flashcard whose answer was shown is recorded as Seen; a Flashcard whose answer was never shown is left out.

  Both open the Session Summary screen.
- **Nothing studied**: a session that ends, by either route, with no Studied Flashcards records nothing and returns to the Preview Study Session Screen.
- **Microphone revoked**: if a Voice Answering session finds the microphone permission gone when it starts or resumes from a pause, it stops voice, tells the User, and after a short pause ends as an early exit.
- **App ended**: nothing in progress is saved. If the User closes the app, the session is lost and nothing is recorded. If the system ends the app in the background, returning rebuilds the session with the same Flashcards from the first one; nothing studied before is kept or recorded.

## Related

- [rated-mode.md](rated-mode.md): ratings, Attempts, re-insertion and Terminal States.
- [fast-mode.md](fast-mode.md): tap-through and read-aloud.
- [voice-answering.md](voice-answering.md): Voice Answering.
- [curation-requests.md](curation-requests.md): the flag icon's dialog.
- [preview-study-session.md](preview-study-session.md): where a session is set up and where it returns.
- [session-summary.md](session-summary.md): where a finished session goes.
- [session-submission.md](session-submission.md): what happens to the Session Result.
