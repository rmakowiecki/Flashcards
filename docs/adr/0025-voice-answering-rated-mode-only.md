# Voice answering (mic-listen-and-grade) applies to Rated Study Sessions only

## Decision

Voice answering applies **exclusively to Rated Study Sessions**. Fast Study Sessions never get voice answering, under any configuration. There is no in-between Rated submode either — e.g. "TTS reads the question, user still self-rates manually" — voice answering is Rated's all-or-nothing second submode alongside plain manual Rated.

It is chosen once, up front, as a row on the Preview Study Session Screen ([ADR-0030](0030-preview-session-settings-sheet.md)), and stays fixed for the whole session.

Engine-sharing, silence-timeout, and grade-band mechanics are documented in [ADR-0031](0031-voice-answering-shared-tts-engine-silence-timeout-grade-bands.md).

## Context

The original implementation gated `VoiceAnswerController`'s activation on `StudySessionViewModel.isVoiceActive`, which only ever became `true` via `onVoiceAutoStart()` — itself only triggered `when route.studyMode == StudyMode.FAST`. Net effect: voice answering was reachable only in Fast mode and unreachable in Rated mode — the opposite of the intended use case (phone-in-pocket hands-free *rating*, which only makes sense where a rating step exists at all — Fast mode has none, per ADR-0016: "Fast mode has no Rating step... There is no Correct/Failed outcome per card").

Neither the design doc (`docs/design/premium-voice-grading-pipeline.md`) nor any prior ADR specified Fast vs. Rated scoping — this was an implementation gap, not a documented decision being reversed.

## Alternatives considered

**Keep voice answering attached to Fast mode, just fix the underlying VAD/TTS overlap bug** — rejected. Fast mode structurally has no rating step and always auto-advances (question → pause → answer → next card, ADR-0012); there's no point in that flow for "wait for a spoken answer, then judge it" to occupy. The Fast-mode attachment wasn't a smaller-scope version of the right design, it was the wrong session type entirely.

**Build a "TTS-reading-only, voice-answering off" middle Rated mode** (question read aloud, user still manually reveals + self-rates) — rejected. It needs the Failed/Partial/Correct self-rating buttons, which don't exist anywhere in this codebase; building them just for this middle mode means half-building the deferred rating system for a submode nobody asked for. Tracked as a follow-up, not built.

## Consequences

- Rated gains exactly two submodes (voice-answering, manual); Fast is untouched by this decision.
- Follow-up, still not built: the "TTS-reading-only, voice-answering off" middle Rated mode, contingent on manual self-rating buttons existing at all.
