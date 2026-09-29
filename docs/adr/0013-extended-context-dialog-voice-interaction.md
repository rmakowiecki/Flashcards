# Extended context as dialog with voice-aware hold/advance logic

## Decision

Extended context is displayed in a **popup dialog** (not inline). Opening it, or any other study-session dialog except voice settings, never pauses playback: it asks the session to **hold at its auto-advance point**. Read-aloud keeps reading the current card; when it reaches the point where it would move on, it stops there instead. On dialog close, a held session keeps the old card on screen for a 500 ms linger, then moves on and plays the next card.

The hold is session state, so it lives in the study session coordinators ([ADR-0054](0054-study-session-rules-in-domain-coordinators.md)): the ViewModel only requests it (`holdAdvance`) and releases it (`releaseAdvance`) through the shared `DialogAdvanceHold` helper, which also owns the 500 ms linger. The reducers decide what the hold means against every other command. The player knows nothing about dialogs or holds; it only receives `pause`, `play` and part-presentation commands ([ADR-0012](0012-tts-mediasession-stack-for-fast-mode.md)).

## Context

Flashcards can have an `extendedContext` field with deeper explanation, code examples, and references. The original inline implementation forced the card into scroll mode and created UX problems when TTS was active:

- Re-entrancy ANR: calling `togglePlayPause()` synchronously inside a `StateFlow` collector triggered a re-entrant `publishState()` → `invalidateState()` → blocking Binder IPC on the main thread.
- Infinite toggle loop: after pausing, the player's between-card flag remained `true` in the emitted state, causing the observer to fire `togglePlayPause` again on every state update.
- Scroll mode: inline extended context pushed card content off-screen, creating a jarring layout shift during voice study.

An earlier version of this decision had the ViewModel watch the player's between-card silence and pause the player when it began. That became unnecessary once the Fast coordinator started timing the pauses between parts itself: the auto-advance point is now an event the reducer already handles, so the hold is one more branch there instead of a ViewModel observer racing the player.

## Alternatives considered

**Keep inline, fix re-entrancy only:** Addressed the ANR but left scroll-mode disruption and the awkward "resume replays the answer you just heard" flow.

**Pause on dialog open:** Simple, but cuts off the answer the user is listening to while they glance at the extended context.

**Auto-resume on dialog dismiss (always):** Clean, but breaks the case where the user paused on purpose before opening the dialog.

**Player holds advance internally:** The player publishes a waiting phase and waits for an external advance command. Rejected: it bleeds a UI concern into the player, which should only receive simple commands.

**ViewModel observes the player and pauses at the between-card silence:** The previous design. It worked only because the silence was inaudible, and it split one rule between a ViewModel observer and the player's own advance. Superseded by the coordinator owning the advance point.

## Interaction design (Fast read-aloud)

| Moment | Dialog closed | Dialog open |
|---|---|---|
| Question or answer reading | plays normally | plays normally — no interruption |
| Pause after the answer ends (auto-advance point) | moves on to the next card | session stops on the current card, player paused (held) |
| Dialog closed while held | — | 500 ms linger → moves on → next question plays |
| Dialog closed before the advance point | — | hold dropped; read-aloud continues normally |
| User pauses while held | — | becomes a user pause at the advance point; closing the dialog does not resume; the next play moves on |
| User plays, skips or goes back while held | — | resolves the hold as the command says; the late release is a no-op |
| Another dialog opens during the linger | — | pending release is dropped; the new dialog holds again |
| Exit confirmed from the exit dialog | — | hold is never released; the session ends |

Voice settings is the one dialog that pauses instead: it previews voices through the same engine, so it takes a temporary pause and ends it on close before releasing the hold.

Rated voice sessions use the same helper and the same rules; their auto-advance point is where the queue would move to the next card.

## Key rationale

**Why dialog instead of inline:** Eliminates scroll-mode layout shift. Dialog dismiss is a clear user signal ("I'm done reading") that maps naturally to "continue playback."

**Why hold at the advance point rather than pause:** The user keeps hearing the card they opened the dialog for; only moving away from it waits. A held player is paused, so the notification shows paused and the playback wake lock is released while the user reads.

**Why auto-advance on dismiss (not manual resume):** At dismiss time the user has heard the answer and read the extended context. Requiring a separate play tap adds friction with no benefit.

**Why 500 ms linger before advancing:** Gives the user a brief visual anchor on the current card before the next card appears, preventing a jarring instant cut.

**Why an explicit pause wins over the release:** An explicit user pause is an explicit user intent; closing a dialog must not override it.

**Why the coordinator owns the hold:** The hold competes with commands from the notification, a headset and audio focus, none of which pass through the ViewModel. Per ADR-0054's boundary rule, state that something other than the screen can change lives in the coordinator.
