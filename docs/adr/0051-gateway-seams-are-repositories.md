# Platform-API wrapper seams are named and layered as Repositories, not a separate Gateway tier

## Decision

A new interface that wraps a device/OS/platform API (e.g. `ShortcutManagerCompat`, `TextToSpeech`) for
domain/UseCase consumption is named `XyzRepository` and lives in the same architectural tier as a
data-backed Repository (e.g. `FlashcardRepository`, `AuthRepository`) — **not** a separate "Gateway"
concept. Concretely:

- Interface: `core/domain/src/main/kotlin/com/rossomak/flashcards/core/domain/repository/XyzRepository.kt`.
- Implementation: `core/data/src/main/kotlin/.../DefaultXyzRepository.kt`.
- UseCases depend on it directly, alongside any other Repository they need — no intermediate
  pass-through Repository wrapping it, and no separate "Gateway" naming/tier for platform-API seams
  going forward.

This does not rename existing seams. `VoicePreviewGateway`
(`core/domain/.../repository/VoicePreviewGateway.kt:3`, impl `DefaultVoicePreviewGateway` in
`core/data`) and `VoiceGateway` (`feature/study/.../voice/VoiceGateway.kt:27`) keep their current
names — retroactive renames are out of scope here. `VoiceGateway` is also injected directly into
`RatedStudySessionViewModel` (`RatedStudySessionViewModel.kt:88`), bypassing a UseCase entirely; that
is a separate, already-known problem (a ViewModel should not hold a Repository/Gateway-tier
dependency directly) and is tracked as its own future cleanup, not fixed by this ADR.

## Context

The launcher-shortcuts feature (see `docs/temp/specs/launcher-shortcuts-spec.md` and
`.scratch/launcher-shortcuts/issues/`) needed a new seam wrapping `ShortcutManagerCompat`. The initial
draft named it `AppShortcutsGateway`, following `VoicePreviewGateway`'s naming. A grilling session
surfaced that this codebase has no consistent rule for when something is a "Gateway" vs a
"Repository" — investigation found:

- `VoicePreviewGateway`'s interface already lives in the `core/domain/.../repository/` **package**,
  despite the "Gateway" name — the package placement doesn't distinguish it from a Repository at all.
- Every existing Repository implementation (`DefaultFlashcardRepository`, `DefaultAuthRepository`,
  `DefaultVoiceOptionsRepository`, etc. — 13 total) talks to a DataSource, never to a Gateway. No
  Repository in the codebase wraps a Gateway.
- The only place a "Gateway" is injected directly into a ViewModel (`VoiceGateway` into
  `RatedStudySessionViewModel`) is an already-acknowledged architecture mistake, not a pattern to
  replicate.

So "Gateway" as a distinct tier had no actual enforced layering rule behind it in this codebase — it
was a name inherited from Android's own `ShortcutManagerCompat`/similar API vocabulary, not a
deliberate architectural distinction from Repository.

## Alternatives considered

**UseCase depends on both a Gateway and a Repository as separate collaborator kinds** — rejected.
Requires a rule for which external-system wrappers are "Gateway-tier" (UseCase-visible directly) vs
which are "Repository-tier," and no such rule exists or was ever needed before this feature.

**Wrap the new `ShortcutManagerCompat` seam in a thin pass-through `ShortcutRepository`** that itself
calls a separately-named `AppShortcutsGateway` — rejected. A delegation class with no logic of its own
is exactly the premature layering this project's own conventions ask to avoid.

## Consequences

- New platform-API wrapper seams (this feature's shortcut seam, and any future one) are named and
  boxed as Repositories from the start: `AppShortcutsRepository` (interface,
  `core/domain/.../repository/`) / `DefaultAppShortcutsRepository` (impl, `core/data`).
- UseCases remain the only orchestration layer allowed to depend on more than one Repository at once;
  ViewModels depend on UseCases, not Repositories, for anything beyond what's already an established
  exception (`VoiceGateway`, tracked separately for future correction).
- "Gateway" is not retroactively purged from the codebase by this ADR — `VoicePreviewGateway` and
  `VoiceGateway` keep their names until touched for unrelated reasons.
