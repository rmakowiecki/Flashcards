# Report a problem: single collection, action map per card, universal in-session entry point

## Decision

Any user can flag a Flashcard for a content fix via a flag `IconButton` in the study session top
app bar (Rated and Fast alike), at the end of the bar opposite the close button, shown only while a
card is displayed — the session's bottom sheet is already crowded with the mic toggle, cog and transport controls.
Tapping it opens a **"Report a problem"** dialog listing all 7 Curation Actions the user can toggle
on for the current card, plus Cancel/Submit.

The draft always starts **empty** — it never seeds from the card's stored report — so a checked row
means only "report this now," never "already reported." Cancel discards the draft; Submit upserts
the checked actions to `users/{uid}/curationRequests/{cardId}` as a map of action key →
`{ flaggedAt: Timestamp }` in one write. Multiple actions can be active on a single card
simultaneously. Reported Flashcards are **not suppressed** — they continue appearing in Study
Sessions normally.

Curation Requests are consumed by admin sync scripts. There is no user-facing management screen,
and because the draft never seeds, there is **no in-app way to withdraw a report** — withdrawal, if
ever needed, is an admin/sync-tooling concern operating on Firestore directly.

## Context

The global Flashcard pool is large and AI-generated. Content issues (wrong difficulty, missing code
formatting, factual errors) are discovered while studying but have no in-session repair path. A
report mechanism bridges the gap between discovering an issue mid-session and queueing a precise fix
for the sync script.

## Alternatives considered

**Two-action model (Retire / Rework), with a dedicated management screen for viewing and withdrawing
past reports** — rejected. A coarse two-value signal loses the specificity needed for automated
fixes, and a management screen is unneeded scope while there's no user-facing use for reviewing past
reports.

**Separate collections for casual user reports vs. structured fix directives** — rejected. One
taxonomy, one admin pipeline; splitting them doubles the sync-script surface for no signal-quality
gain.

**Bulk report action from a card list (multi-select multiple cards, apply one action to all)** —
rejected. A single bulk action can't express "this subset needs X, that subset needs Y" across seven
independent per-card reasons; reporting stays a per-card, in-session action only.

**Speed-dial mini-FABs instead of a list dialog** — rejected. 7 actions exceed the practical limit
for speed-dial (typically ≤5), and labels are essential to distinguish similar-sounding actions. A
dialog with icon + label per action is cleaner.

**Seeding the draft from the card's stored report state** — rejected, whether fetched eagerly per
card or lazily batched on first flag-icon tap. Pre-checking rows from prior state makes an unchecked
box ambiguous between "not a problem" and "already reported, now un-reporting it" — and even a lazy
batched fetch costs a read for a feature most sessions never touch. The draft starts empty every
time; Submit is additive only.

**Delta-based difficulty change (`+1`/`-1`)** — rejected. A card may be severely mismatched. Storing
direction only (`DifficultyTooEasy` / `DifficultyTooHard`) lets the AI agent assign an appropriate
new value rather than nudging incrementally.

**Pausing a spoken session while the dialog is open** (Fast read-aloud and voice-answering sessions)
— rejected in favour of the shared dialog hold ([ADR-0013](0013-extended-context-dialog-voice-interaction.md)):
the current card is read to its end, the session holds at its auto-advance point while the dialog is
open, and it moves on shortly after the dialog closes. Reporting a card never needs a manual resume.

**Deleting the document on empty vs. never deleting** — the document is deleted once every action on
it is removed, so empty report docs never linger and pollute the sync script's query results. The app
itself has no path that removes a single action (see Decision), so in practice this only fires from
admin/sync tooling operating on Firestore directly, not through a `CurationRepository` method.

## Consequences

- `users/{uid}/curationRequests/{cardId}` → `{ subcategoryId: String, actions: { "<CurationAction>": { flaggedAt: Timestamp } } }`
- `CurationAction` values: `DifficultyTooEasy`, `DifficultyTooHard`, `WrongTags`, `NeedsCodeExample`, `BacktickRedo`, `FullRedo`, `Delete`. Presented to users as: "Raise the difficulty," "Lower the difficulty," "Wrong tags," "Needs a code example," "Wrong code formatting," "Needs a full rewrite," "Delete this card." Every value gets a row in the dialog, enforced by a `check()` against `CurationAction.entries` so a new action can't be added without a row.
- `DifficultyTooEasy` and `DifficultyTooHard` are mutually exclusive — enforced in the draft: checking one clears the other via `CurationAction.difficultyOpposite()`, so contradictory data never reaches Firestore.
- No report state is fetched to seed the dialog. Writes happen only on Submit: the checked Curation Actions are upserted to Firestore in one write via `SubmitCurationReportUseCase` → `CurationRepository.upsertCurationActions`; Cancel makes no write; a snackbar shows on write failure.
- Resubmitting a set of actions already on record is a no-op: `DefaultCurationRepository` caches each card's last known flagged actions and skips the Firestore write when the requested set is already a subset of it.
- `CurationRepository` is the sole interface for this concern — separate from `FlashcardRepository`.
- No management screen: Curation Requests are invisible to users anywhere outside the report dialog itself, and cannot be withdrawn from within the app.
- No bulk report action anywhere in the app (e.g. Subcategory Details has no multiselect report toolbar).
