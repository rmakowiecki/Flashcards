# Bluetooth mic routing (`AudioRouteManager`)

Technical reference for how `core:voice` routes capture to a Bluetooth mic, and the bugs hit getting
there. Policy rationale lives in [ADR-0027](../../docs/adr/0027-bluetooth-mic-capture-le-audio-first-sco-fallback-bt-strict-screen-off.md);
this doc is the implementation-level "how it actually works, and what broke."

---

## Division of responsibility

| Owns | Type | Scope |
|------|------|-------|
| **Routing** — which device to bind, when it's ready, mid-window switches | `AudioRouteManager` | Device watching per session (`startSession()` → `releaseSession()`); route activation per listening window (`activateRoute()` → `deactivateRoute()`) |
| **Capture** — the `AudioRecord`, the frame loop, VAD | `VoiceCaptureEngine` | Per-`startListening()` call; reads `AudioRouteManager.route.value` |

`VoiceCaptureEngine` never touches `AudioManager`/Bluetooth APIs directly — it binds
`AudioRecord.setPreferredDevice(route.device)` and reacts to `AudioRouteManager.routeChanges`. This
split means routing bugs and capture bugs are independently testable, but it also means **a caller
that skips `activateRoute()` gets a dead route** — see Bug 1.

The route is activated only while a listening window is open, because the Bluetooth stack drops
the headset's media buttons (AVRCP passthrough) while SCO is up; see ADR-0027 point 2. Without a
mic-capable Bluetooth device, activation touches neither the audio mode nor the communication
device.

## Route resolution

```text
CaptureRouteType: PHONE | BLUETOOTH_LE | BLUETOOTH_SCO | WAITING | NONE
```

`CaptureRoute.isCapturable` is true only for `PHONE` and the two Bluetooth types — `WAITING`
(BT mic present but link not ready) and `NONE` (no route active) are not. This is the
**BT-strict** rule from ADR-0027: a mic-capable BT device connected-but-not-ready must never
silently fall back to the phone mic.

Two resolution paths, picked by SDK version:

- **API 31+** (`resolveCommunicationRoute()`): scan `audioManager.availableCommunicationDevices` for
  `TYPE_BLE_HEADSET` (preferred) then `TYPE_BLUETOOTH_SCO` (fallback). `setCommunicationDevice()` +
  poll `communicationDevice` until the id matches, timeout 3s, 1 retry. No BT device present →
  `PHONE`, undoing a Bluetooth link only if this manager had set one up.
- **Pre-31** (`resolveLegacyScoRoute()`): Classic SCO only. `startBluetoothSco()`, await
  `ACTION_SCO_AUDIO_STATE_UPDATED` → `SCO_AUDIO_STATE_CONNECTED` (same 3s timeout).

Connect/disconnect is observed for the whole session via `AudioDeviceCallback` (all versions) and the
SCO-state `BroadcastReceiver` (pre-31 only). While a route is active they re-resolve and emit on
`routeChanges`; otherwise the next `activateRoute()` resolves afresh. `VoiceCaptureEngine`
only applies a route change at an utterance boundary — never mid-clip.

---

## Bugs found and fixed, in order

### 1. Debug screen never acquired a session route

**Symptom:** Voice Debug screen → Start Listening → immediate `"Bluetooth microphone unavailable"`,
even with Bluetooth off entirely.

**Cause:** `AudioRouteManager.route` defaults to `CaptureRoute(NONE)`, and `NONE.isCapturable` is
`false`. `VoiceDebugViewModel` called `VoiceCaptureEngine.startListening()` directly and never called
`AudioRouteManager.acquireSessionRoute()` (now `activateRoute()`) first — unlike `feature:study`'s `VoiceCaptureSession`,
which does. `runCaptureLoop()`'s very first check (`if (!route.isCapturable)`) failed before looking
at real BT state at all.

**Fix:** `VoiceDebugViewModel` now injects `AudioRouteManager`, calls `startSession()` in `init`,
activates the route for each recording and deactivates it after, and calls `releaseSession()` in
`onCleared()` — mirroring the production per-window lifecycle.

### 2. AudioRecord stream-open warm-up dead zone (partial mitigation)

**Hypothesis:** BT SCO/LE-Audio `AudioRecord` streams have a HAL-level ramp-up right after
`startRecording()` — early reads can be silence or garbage while the codec/jitter buffer settle.
Phone mic has no such ramp-up.

**Mitigation:** `warmUpBluetoothRoute()` drains frames after `startRecording()` (BT routes only),
computing mean-abs-amplitude per frame, until real signal is observed or `BT_WARMUP_TIMEOUT_MS`
(600 ms) elapses. Logs the observed settle time (`Log.i("VoiceCapture", "BT warm-up settled in
${ms}ms...")`) so `BT_WARMUP_ENERGY_THRESHOLD` (250, heuristic) can be tuned from real numbers.

**This did not fix the reported clipping** — it only guards stream *open*, once per
`AudioRecord` (session start / mid-session route rebuild). The actual bug recurred on *every*
utterance onset regardless of how long the user waited before speaking. Left in place; still a valid
guard against the dead-zone case, just not sufficient alone.

### 3. Root cause: `AudioManager.mode` was never set (the actual fix)

**Symptom:** every utterance's leading syllable(s) swallowed over BT — e.g. spoken "one, two, three,
four, five" captured as "o, three, four, five" — reproducible even with a deliberate pause before
speaking, so not a startup-timing issue.

**Cause:** `setCommunicationDevice()`/`startBluetoothSco()` are documented to require
`AudioManager.mode == MODE_IN_COMMUNICATION` (or `MODE_IN_CALL`) for the platform's audio HAL to
behave correctly. `AudioRouteManager` bound the input device but left `mode` at the default
(`MODE_NORMAL`) throughout. With mode/device mismatched, OEM audio HALs commonly keep applying
default (non-communication) onset gating/AGC to the BT link — chewing the first syllable of every
speech burst that follows silence, independent of session-open timing. This is the actual mechanism
behind Bug 2's symptom; Bug 2's fix addressed a real but different failure mode.

**Fix:** `AudioRouteManager` now sets `audioManager.mode = MODE_IN_COMMUNICATION` immediately before
`setCommunicationDevice()`/`startBluetoothSco()` on both resolution paths, and restores
`MODE_NORMAL`:
- when resolution falls back to `PHONE` (no BT device present), and
- in `deactivateRoute()` (called when each listening window closes, and from `releaseSession()`).

### 4. Audio source mismatch for BT routes

ADR-0027 flagged `preferredAudioSource()` as a single swappable line pending an on-device A/B test,
parked on `VOICE_COMMUNICATION` at the time ("re-enables OEM AEC/AGC/NS that may have gutted the
phone-mic signal — resolve by A/B test, not assumption").

**Change:** `preferredAudioSource()` is now route-dependent:

| Route | Source | Why |
|-------|--------|-----|
| `PHONE` | `MediaRecorder.AudioSource.MIC` | Unchanged. `VOICE_RECOGNITION` was tried and rejected — OEM NS/AGC (verified: Realme/MediaTek) attenuated the primary mic to near silence. |
| `BLUETOOTH_LE` / `BLUETOOTH_SCO` | `MediaRecorder.AudioSource.VOICE_COMMUNICATION` | The source semantically paired with `MODE_IN_COMMUNICATION` (fix #3). Android's audio policy wires SCO/LE-Audio input most consistently for this source across OEMs; `MIC` over a BT communication device is a comparatively under-tested combination. |

**Known risk, not yet resolved:** `VOICE_COMMUNICATION` pulls in platform AEC/NS/AGC tuned for
two-way calls. This is the same *class* of OEM-specific risk that sank `VOICE_RECOGNITION` on the
phone-mic path — watch for over-compression/pumping artifacts, not just the silence-gutting failure
mode, when validating on real hardware.

---

## Open risks

- **Not yet hardware-verified across OEMs.** All four fixes above are reasoned from Android's
  documented API contracts and the observed symptom, not confirmed against a matrix of real BT
  headsets/chipsets. Classic SCO vs LE Audio, and OEM audio HAL, likely need separate verification.
- **`BT_WARMUP_ENERGY_THRESHOLD` / `BT_WARMUP_TIMEOUT_MS`** are guessed constants. Tune from the
  logged settle times on real hardware before trusting them at the edges (e.g. quiet rooms, headsets
  with unusually long link ramp-up).
- **`VOICE_COMMUNICATION` regression risk** (see Bug 4) — if a specific OEM shows quality problems,
  make the source three-way (`PHONE` / `BLUETOOTH_LE` / `BLUETOOTH_SCO`) rather than reverting BT
  wholesale back to `MIC`, since `MIC` is the under-tested combination that likely contributed to
  Bug 3 in the first place.

## Debugging

Voice Debug screen (`app`, `BuildConfig.DEBUG`-only) exercises the full route + capture path.
Logcat tags: `VoiceCapture` (warm-up settle time, capture-loop errors) and `AudioRouteManager`
(`"route resolved -> ..."`, `"route changed ... -> ..."`, handshake failures).
