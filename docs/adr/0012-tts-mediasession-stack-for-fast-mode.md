# System TTS in a Media3 player for voice playback

## Decision

Voice playback (Fast mode read-aloud, and the question read-out of Rated voice answering) uses Android's system `TextToSpeech` engine wrapped in a Media3 `SimpleBasePlayer` (`TtsPlayer`), hosted by a Media3 `MediaSessionService` (`StudySessionVoiceService`). Media3 provides the media notification, lock-screen and Bluetooth transport controls, and media-button handling. Fast sessions keep Media3's default notification and foreground behavior; a Rated voice-answering session overrides it to hold the `microphone` foreground-service type (see [ADR-0027](0027-bluetooth-mic-capture-le-audio-first-sco-fallback-bt-strict-screen-off.md), decision 5).

## Context

Fast mode requires: spoken question→pause→answer auto-advance, background/screen-off survival, persistent notification with prev/play-pause/next actions, and lock-screen controls identical to music players.

## Key rationale

- TTS is utterance-based, not stream-based. `SimpleBasePlayer` lets `TextToSpeech` act as a `Player` directly, with no ExoPlayer, no custom `MediaSource` and no synthesis to a file, so Media3's session, notification and controller support come almost for free.
- The legacy `androidx.media` stack (`MediaSessionCompat` + `MediaButtonReceiver` + `NotificationCompat.MediaStyle`) would need all of that wired by hand.
- The player drives playback via `UtteranceProgressListener` callbacks and a generation-counter guard to distinguish natural completion from interrupted utterances.

## Consequences

- App must declare `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, and `POST_NOTIFICATIONS` permissions.
- `MediaSessionService` only registers its session, and so only shows the notification and goes foreground, once a `MediaController` connects. `StudySessionVoiceGateway` binds to the service for commands and state, and connects a `MediaController` only to activate that system surface.
- The player never acts on a system transport command itself. `TtsPlayer`'s `handle*` overrides report it as a `PlaybackEvent.ExternalCommand`, and the study session coordinator applies it exactly like the matching in-app command ([ADR-0054](0054-study-session-rules-in-domain-coordinators.md)). A controller's stop only pauses.
- TTS language is fixed to `Locale.US` (app is English-only).
- Speech rate is configurable (0.5×–2×) but restarts the current utterance on change (TTS cannot resume mid-word).
- Audio focus is managed manually (`AudioFocusRequest`), since `SimpleBasePlayer` does not handle it: `AUDIOFOCUS_LOSS_TRANSIENT` pauses with auto-resume; `AUDIOFOCUS_LOSS` pauses without auto-resume, and since the system drops the request on a permanent loss, the next play requests focus again. A user play or pause cancels any pending auto-resume.
- Backtick characters are stripped from spoken text at session load time (`forSpeech()` in `StudySessionVoiceGateway`) to prevent TTS from reading "backtick" aloud; Flashcard model text is unaffected.
