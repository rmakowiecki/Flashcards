# Voice-answering Rated shares Fast's TTS engine (stop-after-question), uses an 8s silence timeout, and grades on fixed percent bands

## Decision

Enabling voice answering in a Rated session auto-enables TTS question-reading, running through the **same** `TtsPlayer` engine (behind `StudyVoicePlaybackGateway`) Fast mode already uses (same bottom sheet: speed slider, voice picker, play/pause) — not a separate `TextToSpeech` instance. Unlike Fast mode, this engine must **stop after reading the question** and never auto-progress to reading the answer. Once the question finishes, `RatedStudySessionCoordinator` opens the listening window — never before, and never while any TTS (question or grade-feedback notice) is speaking.

When voice answering is on, the grade **replaces** the manual Failed/Partial/Correct self-rating entirely — no rating buttons are shown.

Two tunable constants:
- **8 seconds** of continuous silence after the question finishes → declare no-answer, speak an audible skip notice, advance without a grade for that card.
- **1000ms** fixed delay after the grade-feedback notice finishes speaking → advance to the next card.

Grade-percent bands, used both for feedback tone and the Failed/Partial/Correct mapping: **Failed < 30, Partial 30-74, Correct ≥ 75**. The grader judges ideas rather than wording and does not penalize a small missing detail heavily.

Feedback is as short as it can be while still useful, and never repeats the whole expected answer:
- **Correct**: empty, or the one minor point missed in a few words.
- **Partial**: only what was missing or wrong.
- **Failed with a real attempt**: what was wrong, plus the key correct idea in one clause.
- **Non-answer** ("I don't know", off-topic): grade 0 and a condensed version of the expected answer, key points only. This is where the user hears the real answer in this mode.

The grader returns the feedback twice: once for the screen, where a couple of short inline code spans are allowed, and once worded for speech, with code said in words and no markup. The server strips every other markdown mark from both. The spoken notice is the Rating's spoken name ("Correct", "Partially correct", "Failed") followed by the spoken feedback, or the name alone when the feedback is empty; the sheet shows the shorter "Correct", "Partial", "Failed". The percentage itself drives only the band and is never spoken or shown ([ADR-0053](0053-voice-answer-sheet-states-and-live-input-level.md)).

## Context

Split out of ADR-0025, which was trimmed to cover only the Rated-vs-Fast scoping decision. This ADR holds the mechanics of *how* voice answering behaves once it's active in a Rated session.

A bug surfaced during the original implementation: the voice-answering controller's `start()` called `voiceCaptureEngine.startListening()` unconditionally with no coordination with any TTS playback state, so a phone playing its own TTS through a loudspeaker (no earphones) could have that audio picked up by the VAD as a spoken answer. Tying question-reading and answer-listening to one coordinated engine (this ADR's decision) closes that gap — the fix isn't a special case bolted onto the old wiring, it falls out of building the two capabilities on the same state machine from the start.

## Alternatives considered

**A separate lightweight `TextToSpeech` instance for question-reading**, distinct from `TtsPlayer` (mirroring how the grade and failure notices get their own engine, owned by `NoticeSpeaker`) — rejected once the shared-bottom-sheet requirement (speed slider, voice picker, play/pause, identical to Fast mode's) was set: that UI is hard-wired to the voice player's state (`isVoiceActive`/`isVoicePlaying` gate the whole `BottomSheetScaffold` in `StudySessionScreen.kt`), so a second TTS path would need a second bottom sheet. The grade/failure notice TTS stays a separate lightweight instance, owned by `NoticeSpeaker` — it's a fire-and-forget announcement with no transport controls, genuinely different from question-reading, and its callbacks never touch the player's utterance state machine.

**No silence timeout (listen indefinitely) or a re-prompt-before-skipping variant** — rejected for now in favor of a flat 8s timeout → audible skip. Indefinite listening risks a session hanging on one card with no recovery path in a mode that deliberately has no button fallback. Re-prompting once before skipping is a reasonable future refinement (see Consequences) but adds retry-count state this rework doesn't need yet.

**Feedback that ends with the full expected answer, verbatim, on every Failed or Partial grade** — the original rule, replaced. The answer is already revealed on the card while grading runs, so repeating it filled the fixed-height sheet with a copy of what was on screen above it, and read aloud it buried what the user had actually missed. Only a non-answer, where there is nothing to correct, gets the answer back, condensed.

**One feedback string for screen and speech** — rejected. Code is exactly where the two differ: `fun empty() = null` is precise on screen and garbled aloud. Cards already keep a separate spoken form for the same reason.

**Grade as supplementary feedback only, manual rating buttons stay** — rejected. Phone-in-pocket use makes tapping a rating button impossible; if the feature's purpose is hands-free rating, the grade has to be the rating, not commentary alongside a UI action the user can't physically perform in this mode.

## Consequences

- The voice round (`RatedSessionReducer`'s round, run by `RatedStudySessionCoordinator`) needs states/handling for: waiting-for-question-TTS-to-finish before listening starts, the 8s silence timeout, and pausing/ignoring capture during the grade-feedback notice.
- `TtsPlayer` stops after every part it reads and reports `PlaybackEvent.QuestionFinished` (or `AnswerFinished`); it never moves on by itself. The Rated coordinator opens the listening window on `QuestionFinished` and never asks for the answer to be read, while the Fast coordinator runs its own question, pause, answer, pause loop on the same reports ([ADR-0012](0012-tts-mediasession-stack-for-fast-mode.md)).
- `StudySessionScreen.kt`'s `BottomSheetScaffold` content, gated only on `state.isVoiceActive`, needs to branch on Fast vs. Rated-voice-answering-on (different controls, different `sheetPeekHeight` semantics) — it can no longer assume "voice active" means "Fast mode."
- `functions/src/lib/grading.ts`'s Gemini prompt carries the feedback rules and word limits above, and the client sends the card's spoken answer, when it has one, so the spoken feedback reuses its wording. When one form comes back empty the server fills it from the other; when both are empty it accepts the grade with no rationale and logs a warning if the grade was not Correct, rather than losing the Attempt.
- The bands are defined twice: in `VoiceAnswerGrade` on the client, which maps the grade to a Rating, and as the server's `PARTIAL_GRADE_THRESHOLD` and `CORRECT_GRADE_THRESHOLD`, which the grading prompt quotes to pick the feedback case. A change to one must change the other.
- Grade-to-band mapping needs to live somewhere shared enough that both feedback-content logic and the rating-write logic (ADR-0026) reuse it without drift.
- Explicit follow-ups, not built:
  1. Wiring the auto-grade into actual Terminal-state/mastery/XP persistence — designed by ADR-0044 (Terminal State), ADR-0016 (card progress) and ADR-0014 (the single commit at the Summary screen); resolved in shape by ADR-0026. Not yet implemented. A silence timeout consumes no Attempt and records no outcome; three consecutive timeouts auto-pause the session.
  2. Silence-timeout handling upgraded from flat skip to "re-prompt the question once before skipping."
  3. Surfacing the 8s silence timeout as a user-configurable preference in Settings.
