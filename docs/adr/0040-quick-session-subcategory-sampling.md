# Quick Session subcategory sampling, eager fetch, and the Composite/Custom split

## Decision

### Quick Session samples a bounded random subset of Subcategories, via its own use case

A Quick Session no longer pulls every Subcategory in the Category into the Preview Study Session
Screen. It samples a random count of Subcategories — bounded by a new user preference,
`StudySessionPreferences.subcategoryCountRange` (default `3..5`, editable 1–5) — then randomly picks
that many Subcategories to draw cards from. Re-randomize re-rolls the sample itself, not just the
card draw: the whole point of a "topics: 5" line next to a Re-randomize button is that pressing it
changes the topics, not just their order.

This sampling lives in a new, separate use case, `SampleQuickSessionSubcategoriesUseCase` — pure,
synchronous, no repository dependency — rather than being folded into
`SelectSessionFlashcardsUseCase`. Quick Session is the one scenario where the *set of Subcategories
itself* is allowed to change between one resolution and the next; a single-Subcategory session and a
Custom session (manual multi-select, not yet built) both hand `SelectSessionFlashcardsUseCase` a
fixed, already-decided list, exactly as today. Splitting the two keeps `SelectSessionFlashcardsUseCase`'s
existing interface, tests, and every non-Quick caller completely untouched.

`StudySessionConfig` carries `subcategoryCountRange` unconditionally — the same idiom already used
for `ratedAttempts` in Fast-mode sessions, where it's carried but ignored. It only does anything when
`route.isQuickSession`; a Custom session applies no count and no cap at all, since a user who
manually selects Subcategories has already made the scope decision on purpose.

### No lazy plan construction — the fetch stays eager, the repository cache absorbs the rest

Deferring the real Firestore fetch until "Start session" (fetching only the sampled subset, not
every candidate) was considered and rejected as too much complexity for the value bought.
`SelectSessionFlashcardsUseCase` keeps fetching as soon as a plan needs building — on load and on
every Re-randomize — leaning on the repository's cache-generation seam (ADR-0038) to absorb repeat
reads of Subcategories a Re-randomize sequence happens to re-sample. Bounding the sample size to
begin with (previous decision) already removes most of the read-explosion problem laziness would
have solved; the remainder wasn't judged worth the added moving parts.

### "Composite" and "Custom" are not the same word

**Composite** is the umbrella structural term: any Study Session spanning more than one Subcategory,
regardless of how it was built. **Custom** is specifically the manual-multi-select entry point (`CONTEXT.md`:
"a session where the user manually chooses every Subcategory that enters the session"). A Custom
session is always Composite; a Composite session is not always Custom — a Quick session is Composite
too. "Composite Session" is retired as the *name of the manual entry point* (that's Custom now); the
structural adjective survives unchanged everywhere else it already appeared (composite Recent,
"a session spanning multiple Subcategories is a composite session").

## Context

Quick Session's original MVP behavior handed the Preview Study Session Screen every Subcategory in
the Category unconditionally — `CategoryDetailsScreen.onStartSession` passes
`state.subcategories.map { it.id }` in full. For a Category the size of Android (30+ Subcategories)
this produces a Preview screen too cluttered to read and an N-way Firestore fan-out (one read per
Subcategory, per ADR-0007's shard-per-Subcategory shape) on every load and every Re-randomize, the
overwhelming majority of which the user never sees a card from.

Fixing the read cost by deferring the fetch (only download what Re-randomize actually settles on)
was the first instinct, but it collided with a second problem: the Preview header's card-count
estimate (`Subcategory.cardCount`, a denormalized total) doesn't account for an active Difficulty
filter, so a lazy plan couldn't show an accurate count without fetching anyway — at which point
laziness buys latency-hiding on the *first* load at best, not the read-count reduction it was meant
for. Bounding the sample size solves the actual read-count problem directly and makes the deferred-fetch
question moot for now.

The Composite/Custom question surfaced when the Figma renamed the existing "Start Composite Session"
entry point to "Custom session" — a naming collision that, if resolved by blanket find-and-replace,
would have silently mislabeled a Quick session's Recent card (today "composite Recent," meaning
nothing more than "more than one Subcategory") as a "Custom Recent," implying a manual selection that
never happened.

## Alternatives considered

**Folding subcategory sampling into `SelectSessionFlashcardsUseCase`** (a new
`subcategoryCountRange: IntRange?` field, sampling internally before fetch) — rejected. It reduces
the total seam count by one, which is normally worth doing, but it means every non-Quick call site
(single-Subcategory, future Custom) carries a field that means nothing to it, and it blurs a use case
whose one job today is "resolve a fixed config into cards" into also deciding which Subcategories
participate. Keeping sampling as its own use case, called only from the Quick path, keeps that
boundary exactly where the domain boundary already is.

**Lazy plan construction** (defer the fetch to "Start session") — rejected; see Decision above. Also
rejected: fetching per-Subcategory difficulty-filtered counts as a cheaper substitute for the full
fetch, to keep the header accurate without full laziness — this trades one Firestore read per
candidate Subcategory (to get an accurate filtered count) for the one Firestore read per *sampled*
Subcategory the eager approach already does, buying nothing.

**Collapsing Composite into Custom** (rename every "composite" occurrence to "Custom") — rejected;
see Decision above. **Introducing a third word for the structural sense** (e.g. "multi-subcategory")
instead of keeping "Composite" — considered, rejected as an unforced coinage: "composite" already
carried this exact meaning everywhere in the existing glossary and docs, and several sites already
used "multi-subcategory" and "composite" side by side without meaning to distinguish them.

**A non-uniform or performance-weighted subcategory sample** — rejected for this pass. Uniform random
selection matches the spirit of the existing uniform `shuffled()` card draw; a weighted scheme (favor
Subcategories the user studies less, say) is a future refinement, not a blocker for solving the
clutter/read-count problem.

## Consequences

- `SampleQuickSessionSubcategoriesUseCase` is new and pure. It takes a `kotlin.random.Random`
  defaulting to `Random.Default`; production passes nothing and tests pass a fixed one. There is no
  session seed.
- **The sampled Subcategory ids are screen state, not a function re-derived on every selection.**
  Sampling runs on load and on Re-randomize only. This is what keeps the sample stable while the user
  adjusts filters, length or sort — re-sampling on every selection would let a difficulty-slider nudge
  silently change which Subcategories the session draws from. This applies to `subcategoryCountRange`
  too: it is a Preview screen setting, edited before the session starts, never mid-session. Changing it
  does not itself re-sample — the retained ids can momentarily sit outside the new range — the new
  range only takes effect on the next Re-randomize or the next time the screen loads, the same rule
  every other Preview setting follows.
- `StudySessionConfig`, `StudySessionPreferences`, and `StudySessionPreference` each gain a
  `subcategoryCountRange` field/variant. `PreviewDialog` and `SettingsDialog` each gain a matching
  case, reusing `SessionLengthDialog`'s scaffold with a `RangeSlider` swapped in for the stepper.
- `PreviewStudySessionViewModel` gains a resolve-then-select step, run on load and on Re-randomize,
  that only touches `subcategoryIds`/`subcategoryNames` when `route.isQuickSession`; every other
  session type's resolution is byte-for-byte what it is today.
- `CONTEXT.md`'s Study Session and Study Creation entries now state the Composite/Custom relationship
  explicitly, rather than leaving it to be inferred from two similar-sounding words. `docs/adr/0007`, `docs/adr/0030`, `docs/design/preview-study-session.md`, and
  `docs/user-flow/study.md` were swept for the same distinction; anywhere "composite" was structural
  (composite Recent, "a composite session") was left alone.
- The Preview screen's Filters dialog drops its Tag section outright for any multi-Subcategory
  session (not an empty-chips fallback), and the filter call passes `tagIds = emptySet()` explicitly
  for multi-Subcategory rather than relying on it staying empty by omission — a separate, smaller
  decision bundled into the same implementation pass.
- A 0-card resolution (Difficulty filter narrowed too far, on any session) gets an explicit empty
  state instead of a silently disabled Start button — `FlashcardsEmptyState` gains an `OnGradient`
  style for this. It is a UI-composition decision with no real alternative that was seriously
  considered.
- Until Custom's own entry point is built (Category Details multi-select, tracked separately), this
  ADR's `subcategoryCountRange` exclusion for Custom is unverified by any actual code path — it is a
  constraint on that future work, not yet an enforced one.
