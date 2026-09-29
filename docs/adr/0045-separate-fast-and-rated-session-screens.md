# Fast and Rated Study Sessions are separate screens

## Decision

Fast and Rated Study Sessions are two routes, two ViewModels and two screens:

- `FastStudySessionRoute` → `FastStudySessionScreen` → `FastStudySessionViewModel`
- `RatedStudySessionRoute` → `RatedStudySessionScreen` → `RatedStudySessionViewModel`

Each route carries only the fields its mode uses. `attemptsPerCard` and `voiceAnsweringEnabled`
appear on the Rated route only; `readAloudEnabled` on the Fast route only. No field is carried and
ignored.

The Preview Study Session screen picks the destination when the user starts a session. Study Mode
cannot change mid-session, so a session's route is fixed for its lifetime.

Shared between them, extracted rather than duplicated:

- the top app bar, its exit affordance and its flag affordance
- the exit-confirmation, report-a-problem and extended-context dialogs
- `StudyVoicePlaybackGateway` / `TtsPlayer` and the voice settings dialog
- the Session Summary screen, which both terminate into

## Context

One screen served both modes, branching internally. The entanglement was small — two mode guards in
the ViewModel around voice answering, and a handful of layout branches — and the advance logic was
mode-agnostic, so the shared screen was defensible while both modes did roughly the same thing.

They are about to stop doing roughly the same thing. Rated gains an Attempt counter per card, a
best-Rating-so-far ledger ([ADR-0044](0044-three-valued-terminal-state.md)), Terminal State
resolution, queue re-insertion ([ADR-0046](0046-failed-and-partial-re-insertion-placement.md)),
Mastery Defense marking, and a per-card XP accounting pass. Fast gains none of them. It gains one
boolean set — which cards reached their answer ([ADR-0016](0016-card-progress-model.md)).

The domain model had already drawn this line. `CONTEXT.md` says "Does not apply to Fast Study
Sessions" five times, for Attempt, Rating, Terminal State, Mastery and Voice Answering. The two
modes share a card deck, a top bar and a set of dialogs; they do not share an interaction model.

The shared screen had also already produced the bug this predicts. With voice inactive, the sheet
rendered the Failed / Partial / Correct rating buttons unconditionally, so a Fast session showed
Rating controls for a mode defined app-wide as having no Ratings. Harmless in isolation — the
handler only advanced the card — but it is exactly the failure a mode-conditional screen invites,
and it survived review.

## Alternatives considered

**Keep one screen and branch internally** — rejected. The branching grows with everything Rated is
about to gain, and each branch is a place where Fast can accidentally inherit Rated behaviour, as it
already did once.

**One route and one screen shell, two ViewModels behind a common interface** — rejected. The
interface has to be the union of both modes' events, so Fast's ViewModel implements Rating handlers
it must reject and Rated's implements transport controls it must reject. It relocates the branching
into the type system without removing it.

**One route carrying `studyMode`, two screens chosen inside the composable** — rejected. The route's
argument list stays the union of both modes' needs, which is the part that produces
carried-but-ignored fields and the ambiguity about which are meaningful.

## Consequences

- `StudySessionRoute` is replaced by two routes. "Study Again (Failed)" navigates directly to
  `RatedStudySessionRoute`, which is now unambiguous.
- Each ViewModel is testable against only its own mode's events; a Fast test cannot accidentally
  exercise Rating logic.
- The shared chrome moves into a common package under `feature:study`, and is the surface to check
  when a change should affect both modes.
- `StudySessionDestination` splits, and each mode's terminal event carries its own result shape into
  the shared Summary.
- Two `@Preview` sets to maintain instead of one, and any change to shared chrome must be checked
  against both screens.
