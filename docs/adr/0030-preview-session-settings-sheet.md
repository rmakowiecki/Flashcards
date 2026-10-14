# Preview Study Session Screen presents session settings as a summary-row bottom sheet

The Preview Study Session Screen keeps its adjustable session settings in a **no-scrim bottom
sheet** whose contents are one **summary row per adjustable setting**, instead of laying every
control out flat. Each row shows its current value and opens a focused **dialog**; the whole row is
the tap target. This is the design record for that rework. Current design:
[docs/design/preview-study-session.md](../design/preview-study-session.md).

The screen had to expose mode, voice answering, TTS voice, session length, tag + difficulty
filters, and sort order before a session starts — too many controls to display flat. The
summary-row model keeps the surface compact and reads the current configuration at a
glance, while deferring each control to its own popup.

## Consequences

- **The sheet starts hidden.** A settings button beside Start opens and closes it, and it is
  dismissible by swipe and back, the dismissible shape of
  [ADR-0043](0043-bottom-sheet-on-m3-standard-sheet.md). The hero's Study Mode and delivery badges
  stay visible with the sheet closed, so a new user still sees that both are choices.
- **Voice answering is selectable on the Preview screen**, amending
  [ADR-0025](0025-voice-answering-rated-mode-only.md). It is chosen only here: there is no
  in-session toggle, and a session's delivery never changes once it starts. The Rated interaction
  model itself is unchanged (manual self-rate, or full voice answering — no intermediate TTS-manual
  mode). The microphone handshake (a one-time privacy notice, then the system request) runs on
  Preview's Start tap.
- **Rows follow the Study Mode.** Rated shows Voice answering, Attempts and Partial re-queueing;
  Fast shows Read aloud instead. The Voice row (TTS voice and speed) appears only when something
  will speak: Fast with read-aloud on, or Rated with voice answering on. A Quick session adds a
  topic count range row. Length, Filters and Sort are always shown.
- **Filters reuse the merged tags + difficulty filter popup** from
  [ADR-0022](0022-subcategory-details-filter-sort-toolbar.md) (tags OR-within, AND-combined
  with a difficulty range). Tags are per-subcategory, so the tag facet appears for
  single-subcategory sessions only; multi-subcategory (Quick / Custom) sessions filter
  by difficulty only.
- **Persistence is "keep as default" per popup** (every setting row's dialog) — checked persists a
  global default, unchecked is session-scoped. The **Filters popup is exempt** (tags + difficulty
  always session-scoped), since a per-subcategory tag set can't carry to a different Subcategory.
- **Every popup is a dialog, on this screen and on the Settings screen alike** — the same
  `core:ui` dialog primitives, shell included, keyed on action count rather than on which
  screen opened them. A setting's popup shape therefore never depends on where it was
  opened from, and the merged Filters popup is shared whole with Subcategory Details.
