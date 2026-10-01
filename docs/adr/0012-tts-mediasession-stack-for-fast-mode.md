# System TTS in a Media3 player for voice playback

## Decision

Voice playback (Fast mode read-aloud, and the question read-out of Rated voice answering) uses Android's system `TextToSpeech` engine wrapped in a Media3 `SimpleBasePlayer` (`TtsPlayer`), hosted by a Media3 `MediaSessionService` (`StudySessionVoiceService`). Media3 provides the media notification, lock-screen and Bluetooth transport controls, and media-button handling. Fast sessions keep Media3's default notification and foreground behavior; a Rated voice-answering session overrides it to hold the `microphone` foreground-service type (see [ADR-0027](0027-bluetooth-mic-capture-le-audio-first-sco-fallback-bt-strict-screen-off.md), decision 5).

## Context

Fast mode requires: spoken question→pause→answer auto-advance, background/screen-off survival, persistent notification with prev/play-pause/next actions, and lock-screen controls identical to music players.

## Key rationale

- TTS is utterance-based, not stream-based. `SimpleBasePlayer` lets `TextToSpeech` act as a `Player` directly, with no ExoPlayer, no custom `MediaSource` and no synthesis to a file, so Media3's session, notification and controller support come almost for free.
- The legacy `androidx.media` stack (`MediaSessionCompat` + `MediaButtonReceiver` + `NotificationCompat.MediaStyle`) would need all of that wired by hand.
- The player presents one part of one card when told to (`presentQuestion`, `presentAnswer`), reads it while playing, and reports when it was read in full (`PlaybackEvent.QuestionFinished`, `PlaybackEvent.AnswerFinished`). `UtteranceProgressListener` callbacks and a generation-counter guard tell a natural completion from an interrupted utterance, so a paused or replaced read never reports finished.
- The player never starts another part or card by itself. The card position and the question, pause, answer, pause, next card loop belong to the study session coordinator; the two pauses are coordinator timers ([ADR-0054](0054-study-session-rules-in-domain-coordinators.md), timer rule).

## Consequences

- App must declare `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, and `POST_NOTIFICATIONS` permissions.
- `MediaSessionService` only registers its session, and so only shows the notification and goes foreground, once a `MediaController` connects. `DefaultStudySessionVoiceGateway` binds to the service for commands and state, and connects a `MediaController` only to activate that system surface.
- The player never acts on a system transport command or an audio-focus change itself, and starts and stops only on the coordinator's orders. `TtsPlayer`'s `handle*` overrides report a transport command as a `PlaybackEvent.ExternalCommand`, and the study session coordinator applies it exactly like the matching in-app command ([ADR-0054](0054-study-session-rules-in-domain-coordinators.md)). A controller's stop only pauses. A coordinator drops a command that is not in the set it last gave the player, so a command racing the update of that set does nothing.
- While a call rings or runs, that set is empty ([ADR-0054](0054-study-session-rules-in-domain-coordinators.md), audio interruptions): `toPlayerCommands()` then offers no play, pause, stop or seek, the notification and the headset have nothing to send, and the service stays foreground with its session. Media3 hides an unavailable button; it cannot grey it.
- In a Fast session, next at a question (or in the pause after it) presents that card's answer, and next at an answer (or in the pause after it) moves to the next card, whether it comes from the app, the notification or a headset. At the last card's answer, next does nothing: the session ends only once the player has read that answer in full and the pause after it has run. A card becomes Studied when the player reports its answer revealed (`PlaybackEvent.AnswerRevealed`), screen on or off, so a quick skip never loses it.
- After a part finishes, the player stays playing and idle, so the notification and the headset keep showing playing through the coordinator's pause. A play after a pause taken during one of those pauses goes on to the next step (the answer, or the next card) rather than finishing the pause; a play after a pause mid-part reads that part again from its start.
- The service holds a partial wake lock (`PlaybackWakeLock`) exactly while the player plays, including those idle pauses, and releases it on pause, hold, stop and service destroy. `ExoPlayer` does the same for background playback (`setWakeMode`), but a custom `SimpleBasePlayer` gets nothing like it, and the coordinator's pause timers are coroutine delays in the app's process, which do not advance while the CPU sleeps.
- TTS language is fixed to `Locale.US` (app is English-only).
- Speech rate (0.5×–2×) and voice apply from the next utterance; changing them never reads anything again. They change only through the voice settings dialog, which pauses the session first.
- Audio focus is requested manually (`AudioFocusRequest`), since `SimpleBasePlayer` does not handle it. The request is made when the player starts reading, held while paused (abandoning it would let a defensively paused app take the slot) and made again after a permanent loss, which drops it from the system's stack. The request asks to be paused when ducked, so the platform always reports the duck callback. The player reacts to no focus change itself: it neither pauses on a loss nor resumes on a gain. `AudioEnvironmentMonitor` reports each change as a signal, and the study session coordinator decides by its severity, and resumes through an ordinary play ([ADR-0054](0054-study-session-rules-in-domain-coordinators.md), audio interruptions).
- Backtick characters are stripped from spoken text at session load time (`forSpeech()` in `DefaultStudySessionVoiceGateway`) to prevent TTS from reading "backtick" aloud; Flashcard model text is unaffected.
