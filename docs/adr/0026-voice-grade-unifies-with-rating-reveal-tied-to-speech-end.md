# Voice-graded answers unify with the manual Rating pipeline; on-screen reveal mirrors the spoken timeline, not the question TTS

## Decision

Within the Rating/Attempt/Terminal-State pipeline (ADR-0044 fixes Terminal State resolution, ADR-0046 the queue placement, ADR-0016 the persisted per-card record), a Voice Answering grade writes a **Rating** exactly like a manual self-rating tap — same Attempt/Terminal-State/Mastery/XP effects, using the grade-band mapping already fixed by ADR-0031 (Failed < 40, Partial 40-79, Correct ≥ 80). This resolves ADR-0031's deferred follow-up #1: voice-graded and manually-rated Attempts are the same kind of Attempt, not two parallel tracks.

Answer-reveal in voice-on Rated is tied to the **end of the user's spoken answer** (the `LISTENING`/`SPEECH_DETECTED` → `GRADING` transition), not to the end of question-TTS. The user only sees the answer text once their own answer is already locked in. It stays revealed through the grade notice, and through a grading-failure notice, since that round also reached grading; a silence timeout never reveals it.

Once `SPEAKING_NOTICE` begins with a grade, the bottom sheet shows the Rating and the grader's rationale alongside the spoken notice. A notice without a grade — the no-answer/silence skip (8s timeout, ADR-0031) or a grading failure — shows only a progress disc, and its cause is told by a snackbar and the spoken notice ([ADR-0053](0053-voice-answer-sheet-states-and-live-input-level.md)). Both cases share `VoiceAnswerPhase.SPEAKING_NOTICE`, distinguished by `VoiceAnswerState.lastGrade == null` (no grade) vs non-null (graded), with `VoiceAnswerState.error` separating a grading failure from a silence skip; no new phase enum value.

## Context

ADR-0031 deliberately stopped short of wiring the voice grade into any persistence, since no Rating/Attempt/Terminal-State system exists in this codebase yet for *any* input method, manual or voice. This ADR is the target-design counterpart, written while designing the full Rated-mode mockups (rating buttons, Attempt counter, Mastered progress header) that also don't exist yet — it fixes the shape voice grading unifies into once that system is built, so the two efforts aren't designed independently and then found to disagree.

Separately, cross-referencing the shipped `feat/voice-answering` code against the mockups surfaced two gaps: `isAnswerRevealed` auto-derives from Fast mode's TTS phase (`StudySessionViewModel.kt:130`) but has no equivalent branch for Rated's `VoiceAnswerPhase`, so the answer never auto-reveals in voice-on Rated today. And `SPEAKING_NOTICE` is reused for both the graded-feedback notice and the silence-timeout skip notice with identical UI — audio-only differentiation. The graded result earns its own on-screen treatment; the skip is a short status message, carried by a snackbar alongside its spoken notice.

## Alternatives considered

**Keep voice grade permanently outside Terminal-State/Mastery** (ADR-0031's original deferred stance, kept as a permanent property rather than a temporary gap) — rejected. Once a Rating/Attempt/Mastery system exists at all, having one input method silently not count is worse than not building voice answering yet — it would make the feature's grade feel cosmetic, undermining its stated purpose (hands-free rating).

**Auto-reveal on question-TTS-end**, mirroring Fast mode exactly — rejected. Fast mode has no rating step, so early reveal costs nothing there. Rated's grading premise is "graded on what you actually said" — revealing the reference answer while the user is still mid-utterance risks them adjusting their spoken answer having glimpsed it, defeating the point of capturing an unprompted response.

**A dedicated phase enum value for the skip case** (e.g. `SPEAKING_SKIP_NOTICE` distinct from `SPEAKING_NOTICE`) — rejected. `VoiceAnswerState.lastGrade == null` already distinguishes the two cases losslessly; adding a parallel phase value duplicates that signal in the state machine for no gain.

## Consequences

- `StudySessionViewModel` needs an additional `isAnswerRevealed` branch for `state.studyMode == RATED && isVoiceAnswerEnabled`, revealing when `voiceAnswerPhase` reaches `GRADING` — mirroring the existing `voice.isActive` branch (line 130) but keyed off `VoiceAnswerPhase` instead of Fast mode's `VoicePhase`.
- The bottom sheet's content during `SPEAKING_NOTICE` follows the sheet mode model in [ADR-0053](0053-voice-answer-sheet-states-and-live-input-level.md): the Rating and rationale for a grade, the progress disc for a notice without one.
- This ADR does not itself build the Rating/Attempt/Terminal-State system (ADR-0044, ADR-0046, ADR-0016) — it only fixes voice answering's shape within it, so the two aren't built to disagree. A silence timeout consumes no Attempt and records no outcome.
- `docs/design` mockup coverage for the voice-on Rated branch needs these states: question+waiting, listening (idle), listening (speech detected), grading (answer revealed), feedback (graded), a notice without a grade, consent dialog, and exit dialog over a voice-active background.
