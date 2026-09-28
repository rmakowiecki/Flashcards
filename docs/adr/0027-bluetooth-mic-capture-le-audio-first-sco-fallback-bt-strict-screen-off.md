# Bluetooth mic capture: LE-Audio-first, SCO fallback, BT-strict routing; Rated full-voice runs screen-off

## Decision

Voice answering captures from the **Bluetooth earphone microphone** when a mic-capable BT device is connected, and Rated full-voice sessions run **screen-off** as a first-class hands-free use case. Concretely:

1. **BT-strict routing.** If a connected BT device is *mic-capable*, capture must bind to its mic. "Mic-capable" = a connected communication **input** device of type `TYPE_BLUETOOTH_SCO` or `TYPE_BLE_HEADSET`. A2DP-only devices (output, no mic) are not mic-capable; with those, the phone mic is the only option and is allowed (logged). When a BT mic is present, capture **never** silently falls back to the phone mic — a pocketed phone mic produces muffled, useless grading audio, which is worse than failing.

2. **LE-Audio-first, SCO fallback.** LE Audio (`TYPE_BLE_HEADSET`, Android 13+) carries mic + hi-fi output simultaneously → used session-long, no per-turn toggling, no quality penalty. Classic Bluetooth → SCO/HFP session-long (mono, 8/16kHz, tinny TTS), the only reliable BT Classic mic path. Both are first-class; LE Audio is not merely a second path bolted onto SCO.

3. **SCO-ready handshake.** Replace the current fire-and-forget order (AudioRecord created *before* routing, no wait for the async SCO link): (a) detect BT mic device; (b) request route — `setCommunicationDevice()` API 31+, `startBluetoothSco()` below; (c) wait for ready — `ACTION_SCO_AUDIO_STATE_UPDATED` → `CONNECTED` pre-31, poll `communicationDevice` on 31+, ~3s timeout + 1 retry; (d) then `createAudioRecord()` + `setPreferredDevice(scoInput)`; (e) `startRecording()`, then verify `routedDevice` is the BT device. Timeout or route mismatch → strict-pause, not phone-mic fallback.

4. **Dynamic mid-session switching.** `AudioManager.AudioDeviceCallback` + SCO-state receiver observe connect/disconnect. Switches happen **at utterance boundaries only** (never corrupt an in-flight grading clip; discard the partial on abrupt drop). BT-appears → auto-adopt; BT-drops → strict-pause + auto-reacquire on reconnect.

5. **Screen-off, one dual-typed FGS.** Rated full-voice runs with the screen off. Capture and TTS playback share one foreground service typed `mediaPlayback|microphone` (the existing `MediaSessionService` gains the `microphone` type + `FOREGROUND_SERVICE_MICROPHONE` permission, required on Android 14+ / target SDK 36). Declaring the type is not enough: Media3 always starts the foreground service as `mediaPlayback` only and leaves the foreground on every pause, and without an active `microphone` type a background app records silence. So for a Rated voice-answering session the service posts Media3's notification itself, with both types, from the moment the session loads (the app is visible then) until the session stops. Android 14+ blocks adding the `microphone` type from the background, so the type set never shrinks mid-session, not even while voice answering is paused, and the notification cannot be swiped away for the whole session. Fast sessions keep Media3's default. Media-button (bud-tap) controls drive pause/resume/skip/repeat; the turn loop auto-advances after grading-feedback TTS + ~1s.

6. **Permissions.** Routing is by device *type* via `AudioManager` (`MODIFY_AUDIO_SETTINGS`, already held). **No `BLUETOOTH_CONNECT`** — no named-device access, no new runtime prompt; UI/logs use a generic "headset mic" label.

7. **Audio source is empirical, not assumed.** `preferredAudioSource()` stays a single swappable line pending the on-device A/B test (see the design doc's debug-screen raw-capture block). The `VOICE_RECOGNITION` → `MIC` swap is not treated as settled.

8. **v1 happy-path alerts.** Rare route events (BT drop / reconnect / phone-fallback) are detected and **logged only** — no audible cue yet. The engine still exposes the current capture route as state (debug screen + future audible notices). Turn-level earcons (your-turn / captured) and the existing spoken grade-fail notice stay.

## Context

`VoiceCaptureEngine` already attempts BT SCO routing, so "the device mic is hardcoded" is false — but the implementation fails silently to the phone mic: `createAudioRecord()` runs before `startBluetoothScoRoutingIfNeeded()` (wrong order), nothing waits for the async SCO link, the `AudioSource.MIC` source is a weak pairing for BT routing (MIC is not a communication source, and no `setPreferredDevice` binds the input), and only `TYPE_BLUETOOTH_SCO` is matched (LE Audio buds are missed). The net effect defeats the feature's core "in-pocket" premise.

This ADR supersedes the design doc's original SCO-only, `VOICE_RECOGNITION`, "LE Audio is just a second code path" stance (`premium-voice-grading-pipeline.md` §"Audio capture API and earphone routing", now rewritten). It also narrows the earlier "no background-only voice" position: that constraint applies to *always-on/system-wide* listening, which remains rejected — session-scoped screen-off capture inside a foreground service is the sanctioned path and is what "in-pocket, hands-free" actually requires.

The audio-source uncertainty is deliberate: commit d54cb34 ("use MIC audio source instead of VOICE_RECOGNITION") is confounded — it bundled the missing `Log.e` that fixed silent loop-death, and its doc-comment describes an UNPROCESSED-preferring branch that was never implemented (the code returns `MIC` unconditionally). Combined with the separately-fixed Silero v5 context-prefix bug, there is no clean evidence that `VOICE_RECOGNITION`'s OEM DSP caused the near-silence.

## Alternatives considered

- **Best-effort fallback to phone mic** (current behavior): try BT, silently use the phone mic on any failure. Rejected — silently records a pocketed phone mic, killing the in-pocket use case with no signal to the user.
- **SCO only, defer LE Audio** as a second code path: rejected — silently fails on modern LE-Audio-only buds, exactly the new-hardware in-pocket users. Matching `TYPE_BLE_HEADSET` is ~one extra type check.
- **`VOICE_COMMUNICATION` universally** for reliable SCO routing: parked, not adopted — it re-enables the OEM AEC/AGC/NS that *may* have gutted the phone-mic signal. Resolve by the A/B test, not by assumption.
- **Adding `BLUETOOTH_CONNECT`** for named devices + sturdier detection: rejected for v1 — type-based `AudioManager` routing needs no such permission; a generic "headset mic" label is enough, and a denied prompt would need its own fallback path.
- **Per-utterance SCO toggling** (hi-fi TTS between turns): rejected for Classic SCO — re-arms the 0.5–2s handshake race every turn. LE Audio makes it moot (simultaneous mic + hi-fi), so it is unnecessary where it would help and harmful where it wouldn't.
- **Separate mic foreground service** distinct from playback: rejected — two services in lockstep (start/stop ordering, dual notifications, duplicated wake-lock) for no gain; capture and TTS already interleave on one session.
- **Audible cues for rare route events** in v1: deferred — detect + log now, add earcon/TTS later; keeps v1 to the happy path.

## Consequences

- `VoiceCaptureEngine` gains: route detection over `{TYPE_BLUETOOTH_SCO, TYPE_BLE_HEADSET}`, the ordered SCO-ready handshake with timeout/retry + `routedDevice` verification, `AudioDeviceCallback`/SCO-state observation with utterance-boundary switching, and a `CaptureRoute` state flow (phone / bluetooth / waiting / none). Strict-pause replaces silent phone-mic fallback.
- The existing `MediaSessionService` becomes dual-typed (`mediaPlayback|microphone`); manifest adds `FOREGROUND_SERVICE_MICROPHONE`. Wake lock and notification stay single/shared. A refused `microphone` type surfaces as a capture failure, which pauses the session; a session loaded while the app is already in the background always ends in this pause.
- Holding the `microphone` type relies on Media3 `@UnstableApi`, checked against Media3 1.10.1. `StudySessionVoiceService` overrides `onUpdateNotification` and builds the notification with `DefaultMediaNotificationProvider.createNotification`. Media3's own action factory, `DefaultActionFactory`, is package-private, so `VoiceSessionNotificationActionFactory` mirrors it on `PlaybackPendingIntentBuilder` and `CustomCommandPendingIntentBuilder`. Re-check the override and the mirrored factory on every Media3 upgrade.
- Like Media3's own foreground path, the service is started, not only bound, but with a plain `startService`, not `startForegroundService`: if the `startForeground` call that follows were refused, a pending foreground-service start would crash the app.
- `preferredAudioSource()` remains a one-line swap; the debug-screen raw-capture block grows a per-source RMS A/B test (Realme 9 Pro + a BT headset) to fix the source empirically.
- Not built here: this ADR is design-only (no implementation this change). Audible route-event notices, voice commands (vs bud-tap controls), and per-utterance LE-Audio quality optimization are explicit later phases.
