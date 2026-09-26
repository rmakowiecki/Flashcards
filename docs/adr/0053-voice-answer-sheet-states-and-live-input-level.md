# Voice answer sheet states and live input level

## Decision

**One sheet mode at a time.** In a Rated Study Session with Voice Answering, the bottom sheet shows exactly one
`RatedVoiceSheetMode`, derived by `voiceSheetModeOf` from the voice-answering phase, transcript, grade, pause state
and a short-notice flag:

| Mode | When | Shows |
|---|---|---|
| `Transport` | question being read, no round running, or paused | transport row + voice-settings cog |
| `Listening` | `Listening`, `SpeechDetected` | live microphone indicator, centered |
| `Pending` | `Grading` without a transcript; any short notice | progress disc, centered |
| `GradingWithTranscript` | `Grading` with the sanitized transcript | disc left, "Grading", "Your answer" + transcript |
| `Graded` | `SpeakingNotice` with a grade | Rating circle left, Rating name, "Your answer rating" + rationale |

A short notice (silence skip, silence pause, grading failure, capture failure) always maps to `Pending`: a status
message, not content, so it has no controls. `VoiceAnswerState.isShortNoticeSpeaking` marks it. The flag survives
`VoiceAnswerController.stop()`, because a pause stops voice answering while its own notice is still speaking. It
clears when that utterance finishes, on `release()`, or after a 5 s safety timeout. While it is set, `Pending` wins
over a pause; afterwards the sheet shows the next card's `Transport`, or the ordinary paused `Transport` with no
special wording.

The voice-settings cog exists only in `Transport`. Changing voice settings mid-round would create states the
session does not model.

**Three composables, one persistent slot.** The microphone indicator (`FlashcardsVoiceCaptureIndicator`), the
progress disc (`FlashcardsVoiceProgressDisc`) and the read-only Rating circle (`FlashcardsRatingButton` with
`onClick = null`) stay separate composables, all sized by `FlashcardsVoiceCaptureIndicatorDefaults.discSize`. The
only shared node is an invisible badge slot. Inside a `LookaheadScope`, its `animateBounds` moves it from the center
to the left when the transcript (or, without one, the grade) arrives. Every handoff is a crossfade: the indicator's
bars fold away as the progress disc fades in at the same center and size, and the disc crossfades into the Rating
circle, which carries the Rating color. There is no shared disc and no color animation. The sheet's own
`AnimatedContent` switches only between `Transport` and the voice-round group, so the slot survives the whole round.

**The user's spoken answer.** The sanitized transcript ([ADR-0028](0028-voice-answering-not-background-only-streamed-transcript-then-grade.md))
appears only in the sheet, only from its arrival until the grade, and never comes back after grading. Its sole job
is letting the user confirm their speech was recognized, so it stays on screen for at least one second:
`holdGradedUntilTranscriptShown` in `VoiceAnswerController` holds the grade, or a grading failure, until then. The
hold sits in the controller, not the UI, so the spoken notice and the visual change still start together. The
transcript is display-only: never spoken by TTS and never exposed to TalkBack, whether as a content description, a
live region or focusable text. The voice round exposes one polite live region announcing "Listening", then "Grading",
then the Rating name. The rationale is left to the spoken feedback.

The sheet has a fixed height ([ADR-0043](0043-bottom-sheet-on-m3-standard-sheet.md)). A long transcript or rationale
scrolls inside it and is never truncated.

**No percentage.** Neither the sheet nor the spoken notice shows the grade percentage. The notice speaks the Rating
name and the rationale. `VoiceAnswerGrade.gradePercent` stays, because it drives the grade bands
([ADR-0031](0031-voice-answering-shared-tts-engine-silence-timeout-grade-bands.md)).

**Grading failures stay snackbars.** `VoiceAnswerFailureReason.GradingFailed` is sealed: `NoConnection` or
`ServiceError`. `Throwable.toGradingFailureReason()` returns `NoConnection` when an `IOException` appears anywhere in
the cause chain, because the Firebase Functions SDK wraps a failed or timed-out request in its own exception with the
`IOException` as the cause. Everything else is `ServiceError`, including an HTTP 503 and an entitlement rejection,
since the server was reached. Each variant has its own snackbar and its own spoken notice; a hands-free user may
never see the snackbar. The card answer is revealed for a grading failure, which already passed through grading,
and stays revealed through its notice. It stays hidden for silence timeouts and capture failures.

**The indicator's contract.** The `:core:ui` indicator does no timing. It takes a static
`levels: ImmutableList<Float>` of 5 values in `0..1`, index 0 innermost, mirrored on both sides, and interpolates
linearly to each new snapshot over the producer's interval (70 ms). Only bar height and alpha vary. The disc pulses
independently of the level. Whether the bars show is decided only by the indicator's `isActive`, never by the level.

**The level pipeline.** `VoiceCaptureEngine.inputLevel` turns each 20 ms PCM frame into a `0..1` level: RMS in dBFS,
mapped from −50 dBFS to −10 dBFS, smoothed with a 30 ms attack and a 250 ms decay, zero below a noise floor. It
reacts to any sound, not only VAD speech, because it shows that the microphone hears the user. Gateways and domain
carry only this raw scalar as `rawVoiceLevel: Flow<Float>`. Shaping happens in `:core:ui`: `VoiceLevelWaveShaper`
takes the window maximum each interval, shifts it outward, decays to rest when the level drops to zero, and suspends
its timer while at rest. It is cold. `stateInVoiceBarsLevels` turns it into each ViewModel's `voiceBarsLevels`,
which lives outside screen state, so a new snapshot (about 14 per second) recomposes only the indicator. The
onboarding voice test uses the same indicator, following the playback level while the take plays back.

**Privacy.** The level is computed from raw PCM on the device only. It is never logged, stored or uploaded.

## Context

The voice-answering sheet used to stack a phase label, the transcript, the grade text and the transport row, all at
once. Screen-on use ([ADR-0028](0028-voice-answering-not-background-only-streamed-transcript-then-grade.md)) made
that stack the main thing a user looks at during a round. It also gave no sign that the microphone was hearing
anything.

Two answers appear in a voice round and must not be confused. The Flashcard's answer is revealed on the card when
capture ends ([ADR-0026](0026-voice-grade-unifies-with-rating-reveal-tied-to-speech-end.md)). The user's spoken
answer is theirs, not part of the Flashcard, so it belongs in the sheet.

Grading can finish within a few hundred milliseconds of the transcript, which would flash it unreadably. A UI-only
delay would let the spoken grade start before the visual one.

## Alternatives considered

**Show the transcript inside the Flashcard card** — rejected. The user's answer is not part of the Flashcard, and
the card already shows the revealed answer.

**A draggable sheet, or the stacked layout** — rejected. A draggable sheet adds states the round does not need, and
the stack is what this replaces.

**In-sheet failure states** — rejected. A failure is a short status message; the snackbar and the spoken notice
already carry it, and the sheet stays on the progress disc until the notice ends.

**One morphing badge composable** — rejected. Three plain composables in one slot, handed off by crossfade, give the
same effect with each composable usable on its own.

**An FFT spectrum** — rejected. At 5 bars per side, per-band detail is barely visible, and it needs a hand-rolled
FFT for no user-facing gain over one loudness value.

**A simulated or VAD-gated animation** — rejected. The indicator's job is to prove the microphone hears the user,
so it must follow the real signal, including sound the VAD would not call speech.

## Consequences

- Every voice-round phase maps to a real sheet mode; there is no fallback layout.
- `VoiceAnswerController` has no unit tests (it depends on the capture engine and TTS), so its timing logic lives in
  pure, separately tested pieces: `holdGradedUntilTranscriptShown`, `toGradingFailureReason` and
  `VoiceLevelWaveShaper`.
- The short-notice flag has a 5 s timeout, so notices must stay short.
- The same indicator and pipeline serve the Rated sheet, the onboarding voice test and the Voice debug screen.
