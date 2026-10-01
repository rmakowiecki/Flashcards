# Study session rules are pure domain reducers, run by a stateful domain coordinator per mode

> Status: accepted

## Decision

Every rule of a Study Session lives in `core:domain`, in the `core.domain.session` package, split in
two kinds of class per Study Mode:

- **A pure reducer.** `RatedSessionReducer` and `FastSessionReducer` take a state and one input
  (a Rating, a finished question, a captured answer, a grade, a timer that elapsed, a transport
  command, a player state change) and return the next state plus an ordered list of effects. They
  never read a clock, never touch a gateway and never launch anything. Their only constructor
  parameters are the `Random` that draws re-insertion gaps
  ([ADR-0046](0046-failed-and-partial-re-insertion-placement.md)) and a `DomainLogger` that records
  diagnostics such as audio interruption decisions; logging never affects the state or the effects.
  Konsist enforces this for every `*Reducer` class in `core:domain`.
- **A stateful coordinator.** `RatedStudySessionCoordinator` and `FastStudySessionCoordinator` are
  plain `@Inject` classes, one per ViewModel. The ViewModel starts one with `viewModelScope` and
  stops it from `onCleared()`; the coordinator never creates a long-lived scope of its own, and its
  `stop()` is synchronous. It loads the session's cards, holds the reducer state, runs each effect,
  owns every timer, and feeds what the effects report back into the reducer as inputs. It talks to
  gateways and repositories directly (`StudyVoicePlaybackGateway`, `VoiceCaptureGateway`,
  `VoiceAnswerGradingRepository`, `PermissionGateway`), and to a use case only when that use case
  carries logic of its own (`GetSessionStartDataUseCase`), never to a pass-through use case. It
  publishes a snapshot (`StateFlow`) and one-shot events (an unconflated `Flow`). The snapshot is
  plain data: every derived rule (the current card, whether voice answering is active) lives on the
  reducer state, and the coordinator copies its result into the snapshot.

The ViewModel keeps presentation only: dialogs, mapping the snapshot to its `ScreenState`, mapping
events to snackbar messages and navigation, and the microphone bar levels.

Two rules decide where state lives:

- **Boundary rule.** If anything other than the screen can change a piece of state (a headset, the
  media notification, audio focus, an engine failure, a timer), that state lives in the
  coordinator. The ViewModel may ask for a change but never owns or mirrors that state. External
  transport commands therefore reach the coordinator as `PlaybackEvent.ExternalCommand`; the player
  never acts on them itself.
- **Timer rule.** Every timer that changes round or session state lives in the coordinator: the
  silence timeout, the transcript dwell, the notice tail, the rewind threshold, and Fast
  read-aloud's pause between a question and its answer and pause between an answer and the next
  card, the time a short sound may last before it interrupts, and the tail the capture gate stays
  closed after it. The voice implementation keeps only platform mechanics: the text-to-speech and
  capture engines, the route handshake, the capture and playback wake locks, the audio environment
  monitor (audio-focus requests, and the translation of what the platform reports into signals),
  string resolution, and the notice watchdog that guarantees every spoken notice reports that it
  finished.
  The Fast coordinator also owns the presented card: the player is told which part of which card to
  present and never moves on by itself.

**Audio interruptions follow the same split.** `AudioInterruptionGateway` reports what other apps and
the device do to the audio around a voice session as signals: focus changes, the live audio mode, a
microphone taken by another app and a headset disconnect. The platform adapter only translates; it
never pauses or resumes. Both reducers hold an `InterruptionEpisode` in their state, pure state that
classifies each signal by severity and decides nothing about playback:

- a *blip* (a notification sound, a navigation prompt) keeps the session going. In a Rated
  listening window it only closes the capture gate; when it outlasts 1.5 s over speech, it becomes
  an interruption;
- an *interruption* (the Assistant, an alarm, a ringing call, a microphone taken by another app)
  pauses the session through the pause flags a user pause already uses, so its per-phase behavior is
  the one a user pause has, and resumes it through an ordinary play when it ends within 60 s;
- a *call* (picked up, whatever its length) and a *takeover* (another media app) pause the same way
  and never resume on their own;
- a headset disconnect is a user pause, and never resumes on the device speaker by itself.

While a call rings or runs, no transport command is on offer anywhere. Both reducer states return an
empty `availableTransportCommands` set, so the in-app buttons are disabled, the media notification
shows none, and Media3 refuses what a headset or the notification still sends. The notification
hides its buttons rather than greying them: Media3 has no disabled state for a command. A command
that still reaches a coordinator is dropped before the reducer, silently, because the notification
cannot show a refusal. The reducers keep their own check as a backstop: a play that races the block
starts nothing and answers with a message. The reducer also emits that message once, when the audio
mode first turns call-blocking, so the user learns why the controls went dark; the ViewModel maps it
like any other session event.

A session that opens during a call is blocked from the start: the platform adapter reads the live
audio mode when it starts and takes it as the baseline later changes are compared with, so the end
of the ring is seen as well. A call known only by its audio mode starts no interruption, because the
session never held focus to lose; if the player plays anyway, the reducer pauses the session as for
a user pause, so only the user's play resumes it, also after the call ends. The pause is triggered by
the player's report and not by the mode alone, because during a session the mode arrives just before
the focus loss that holds the session, and pausing on the mode would end the auto-resume of a ring
that is declined. A part that starts inside that gap does pause the session, so a declined ring then
leaves it paused until the user plays: the narrow price of never playing over a ring.

A blocked session cannot play, so nothing would ask for focus and see the call end. The adapter
therefore queues a focus request when the session opens during a call; the system grants it once the
call lets go of focus, and every focus change makes the adapter read the mode again. Below Android 12
(API 31) the platform has no audio-mode callback and the app does not poll: there the adapter also
reads the mode when any app's playback starts or stops (API 26 and later), which is how the end of a
ringtone is seen. Below API 26 a call that ends without a focus change stays unseen until the next
signal, and the close button leaves the session. A ring that no audio mode reports (a messaging app's
own ringtone, or a silent ring) cannot be blocked.

Signals carry no time; the coordinator stamps each with its time source when it arrives, and the
60 s window is checked from those stamps at the end of the interruption, so it needs no timer.

One invariant follows: **a session never changes its delivery mode after it starts.** A Rated
voice-answering session never becomes a manual one, and a Fast read-aloud session never becomes
tap-through. When a text-to-speech engine cannot start, the coordinator pauses the session with
`SessionPauseReason.VoiceEngineUnavailable`; a resume restarts the whole voice stack at the
presented card.

## Context

The Rated ViewModel had grown to about 900 lines. It held the queue and its rules, the silence and
grading-failure counters, a deferred screen sync, the session clock, the rewind threshold and three
kinds of test seam. The voice round ran in a separate controller inside the voice service, with its
own timers and its own copy of the notice choice, fed by flags the ViewModel pushed ahead of each
round. The advance after a voice answer was split between the two and held its order only through
dispatcher timing. Headset commands reached the player directly and bypassed every rule, so a
headset "next" in a Rated session moved the player off the card the queue was still grading.
Nothing enforced where a new rule should go.

## Alternatives considered

- **Rules inside use cases.** Rejected: a session is long-lived state that many inputs change in
  turn. A use case is one business action with no state between calls, so the state would still
  have to live somewhere else, and every input would need its own use case threading it through.
- **Top-level rule functions with no entry point**, as the queue rules were before. Rejected: they
  test well one at a time, but nothing owns the order they run in, which is exactly where the
  split advance broke.
- **Rules in the ViewModel.** Rejected: the ViewModel only sees what the screen does, while half
  of a voice session's inputs come from outside the screen. Keeping the rules there is what left the
  headset path unguarded.

## Consequences

- A new session rule goes into a reducer as an input or an effect, and a new timer goes into the
  coordinator. A ViewModel change never alters session behavior.
- Reducers are tested as tables (input in, state and effects out); coordinators are tested in
  virtual time on the `core:domain` test fixtures; ViewModel tests run on the real coordinator.
- ADR-0051's layering gains a second orchestration kind: a coordinator may depend on several
  gateways and repositories at once, as a use case may.
- The voice player keeps no session state: no pending advance, no rewind threshold, no reaction to
  transport commands. Two text-to-speech engines stay separate
  ([ADR-0031](0031-voice-answering-shared-tts-engine-silence-timeout-grade-bands.md)).
- `core:domain` logs through its own `DomainLogger` port, implemented on `AppLog` in `core:data`. Audio
  interruptions log a handful of lines per episode with a stable `Audio interruption:` prefix.
- The time source the coordinators share counts while the device sleeps (`SystemClock.elapsedRealtimeNanos()`),
  because a locked phone in a pocket sleeps between an interruption and its end. Tests use the test
  scheduler's time source.
