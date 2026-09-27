# Voice-answering Rated shares Fast's TTS engine (stop-after-question), uses an 8s silence timeout, and grades on fixed percent bands

## Decision

Enabling voice answering in a Rated session auto-enables TTS question-reading, running through the **same** `VoiceGateway`/`TtsPlayer` engine Fast mode already uses (same bottom sheet: speed slider, voice picker, play/pause) — not a separate `TextToSpeech` instance. Unlike Fast mode, this engine must **stop after reading the question** and never auto-progress to reading the answer. Once the question finishes, `VoiceAnswerController` starts listening — never before, and never while any TTS (question or grade-feedback notice) is speaking.

When voice answering is on, the grade **replaces** the manual Failed/Partial/Correct self-rating entirely — no rating buttons are shown.

Two tunable constants:
- **8 seconds** of continuous silence after the question finishes → declare no-answer, speak an audible skip notice, advance without a grade for that card.
- **1000ms** fixed delay after the grade-feedback notice finishes speaking → advance to the next card.

Grade-percent bands, used both for feedback tone and the Failed/Partial/Correct mapping: **Failed < 40, Partial 40-79, Correct ≥ 80**. On Failed/Partial, spoken feedback must state what was missed and include the full acceptable answer (the only place the user hears the real answer in this mode). On Correct, feedback is a short affirmation only. The spoken notice is the Rating name followed by that feedback; the percentage itself drives only the band and is never spoken or shown ([ADR-0053](0053-voice-answer-sheet-states-and-live-input-level.md)).

## Context

Split out of ADR-0025, which was trimmed to cover only the Rated-vs-Fast scoping decision. This ADR holds the mechanics of *how* voice answering behaves once it's active in a Rated session.

A bug surfaced during the original implementation: `VoiceAnswerController.start()` called `voiceCaptureEngine.startListening()` unconditionally with no coordination with any TTS playback state, so a phone playing its own TTS through a loudspeaker (no earphones) could have that audio picked up by the VAD as a spoken answer. Tying question-reading and answer-listening to one coordinated engine (this ADR's decision) closes that gap — the fix isn't a special case bolted onto the old wiring, it falls out of building the two capabilities on the same state machine from the start.

## Alternatives considered

**A separate lightweight `TextToSpeech` instance for question-reading**, distinct from `VoiceGateway`/`TtsPlayer` (mirroring how `VoiceAnswerController` already handles its own grade/failure notices) — rejected once the shared-bottom-sheet requirement (speed slider, voice picker, play/pause, identical to Fast mode's) was set: that UI is hard-wired to `VoiceGateway`'s state (`isVoiceActive`/`isVoicePlaying` gate the whole `BottomSheetScaffold` in `StudySessionScreen.kt`), so a second TTS path would need a second bottom sheet. The grade/failure notice TTS stays a separate lightweight instance — it's a fire-and-forget announcement with no transport controls, genuinely different from question-reading.

**No silence timeout (listen indefinitely) or a re-prompt-before-skipping variant** — rejected for now in favor of a flat 8s timeout → audible skip. Indefinite listening risks a session hanging on one card with no recovery path in a mode that deliberately has no button fallback. Re-prompting once before skipping is a reasonable future refinement (see Consequences) but adds retry-count state this rework doesn't need yet.

**Grade as supplementary feedback only, manual rating buttons stay** — rejected. Phone-in-pocket use makes tapping a rating button impossible; if the feature's purpose is hands-free rating, the grade has to be the rating, not commentary alongside a UI action the user can't physically perform in this mode.

## Consequences

- `VoiceAnswerController`'s phase state machine needs states/handling for: waiting-for-question-TTS-to-finish before listening starts, the 8s silence timeout, and pausing/ignoring capture during the grade-feedback notice.
- `VoiceGateway`/`TtsPlayer` needs a Rated-mode playback shape (stop after question, no auto-progress to answer) distinct from Fast mode's continuous auto-advance.
- `StudySessionScreen.kt`'s `BottomSheetScaffold` content, gated only on `state.isVoiceActive`, needs to branch on Fast vs. Rated-voice-answering-on (different controls, different `sheetPeekHeight` semantics) — it can no longer assume "voice active" means "Fast mode."
- `functions/src/lib/grading.ts`'s Gemini prompt needs updating to: instruct inclusion of the full acceptable answer in feedback when the grade is Failed/Partial, and keep Correct feedback to a short affirmation.
- Grade-to-band mapping needs to live somewhere shared enough that both feedback-content logic and the rating-write logic (ADR-0026) reuse it without drift.
- Explicit follow-ups, not built:
  1. Wiring the auto-grade into actual Terminal-state/mastery/XP persistence — designed by ADR-0044 (Terminal State), ADR-0016 (card progress) and ADR-0014 (the single commit at the Summary screen); resolved in shape by ADR-0026. Not yet implemented. A silence timeout consumes no Attempt and records no outcome; three consecutive timeouts auto-pause the session.
  2. Silence-timeout handling upgraded from flat skip to "re-prompt the question once before skipping."
  3. Surfacing the 8s silence timeout as a user-configurable preference in Settings.
