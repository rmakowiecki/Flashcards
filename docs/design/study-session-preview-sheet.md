# Study Session Preview — settings bottom sheet

Design record for reworking the **Preview Study Session Screen** so its bottom panel
summarizes every adjustable session setting as a tappable row, instead of laying the
controls out flat. Resolved in a grilling session; supersedes parts of ADR-0025 and the
current CONTEXT / SYSTEMDESIGN descriptions (see "Doc reconciliation").

## Problem

The preview screen must let the user adjust, before starting: study mode, voice
answering, TTS voice, session length, tag + difficulty filters, and sort order — too many
controls to display flat. Rework the panel into a summary-row list; each row shows the
current value and opens a focused edit popup.

## Container & layout

- Screen stays **full-screen** with the purple hero. Top bar: X (closes the whole
  preview) + `<Category> · <Topic>` title.
- The panel is a **persistent bottom sheet, no scrim**, holding the setting rows and the
  Start action. It rests at content height with all rows + Start visible; the hero sits
  above it, scaled smaller. Drag up expands if rows overflow; it is **not dismissible by
  drag** — only the X closes the preview.
- **Edit popups are modal bottom sheets *with* scrim** dimming the panel + hero. (The main
  panel is scrimless; the transient edit sheets are not.)

## Hero (read-only scope)

Keeps: the Category glyph circle, "Ready to start?", one-line scope sentence, and read-only chips —
estimated minutes + card count (+ Subcategory count for multi-subcategory sessions).

Removed from the hero: the Sort chip and the "Filtered by …" line — both are rows now.

## Setting rows

Every row opens an edit popup; the whole row is the tap target, the pencil is only an
affordance hint. Each row shows its current value.

| Row | Values | Visible when |
|-----|--------|--------------|
| Mode | Rated \| Fast | always |
| Voice answering | On \| Off | Rated only |
| Voice (TTS voice + speed) | popup | Fast **or** Rated + Voice-answering On (hidden for Rated-manual) |
| Length | card count | always |
| Filters | tags + difficulty | always (tags part single-subcategory only) |
| Sort | Default \| Easiest first \| Hardest first | always |

Actions at the sheet bottom: **Re-randomize** (multi-subcategory & Quick sessions only) beside
**Start session**.

## Study Mode model

Status quo retained — no intermediate "TTS reads question, rate by hand" mode.

- **Study Mode** (top level): `Rated | Fast`.
- A **Rated** session is either plain manual (read, reveal, self-rate) or full
  **Voice answering** (TTS reads the question, listens, auto-grades). Voice answering is a
  Rated-only toggle.
- **Voice answering is now chosen up front on the preview**, not only in-session. TTS
  still reads the question only (reveal tied to speech-end) — the Fast-mode "read Q and A,
  auto-advance" behavior is unchanged and distinct.

## Filters

One **merged filter popup**, shared with Subcategory Details (ADR-0022 shape): tag
multi-select (OR within tags) + difficulty `RangeSlider`, the two facets combined with
AND. Tags are derived per subcategory, so the tag section appears for **single-subcategory
sessions only**; multi-subcategory (Quick / Custom) sessions show **difficulty only**.

## Persistence

Every edit popup carries a **"keep as default"** checkbox — Mode, Voice answering, Length,
Sort, Voice, Quick Session topic count. Checking it persists that value as the user's
default for future sessions; unchecked, the change is session-scoped. Voice's "keep as
default" writes the existing global voice preference.

**Filters is the one exception**: no "keep as default" — tag and difficulty selections are
always session-scoped (a per-subcategory tag set can't carry to a different Subcategory).

## Popup form & sharing

Preview edit popups are **bottom sheets**; the Settings screen keeps its **AlertDialogs**.
The shared surface is the popup's inner content (voice picker + speed, sort options, filter
facets), not the outer shell — same body, different container per surface. The merged
Filters popup, being a sheet in both places, can be shared whole with Subcategory Details.

## Doc reconciliation (follow-ups)

- **ADR-0025** ("Voice Answering … never chosen on the Preview Study Session Screen") is
  **superseded** — voice answering is now a preview-time choice. Needs a superseding ADR.
- **CONTEXT.md** — update *Voice Answering* ("toggled after the session has already
  started (never chosen on the Preview Study Session Screen)") and *Preview Study Session
  Screen* to reflect the new summary-row model + voice-answering-on-preview.
- **SYSTEMDESIGN.md** — rewrite the *Preview Study Session Screen* section (row model,
  persistent no-scrim sheet); drop "Session parameters (card count override, difficulty,
  time limit)" from *Not in MVP* — length + difficulty are now first-class here.

## Design brief

See the copy-ready brief handed to Claude Design (screen rework instruction). It specifies
the container, hero, row list + visibility, edit-popup inventory, and the state matrix to
deliver (single-subcategory Rated manual / Rated+VA / Fast, multi-subcategory/Quick, one popup open).
