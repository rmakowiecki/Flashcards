# `core:voice`

On-device voice capture pipeline for the **voice answering** feature: continuous microphone
capture → neural voice-activity detection (VAD) → utterance segmentation → on-device voice
obfuscation → WAV encoding. It turns a live mic stream into discrete, privacy-obfuscated spoken
utterances that the study session uploads for LLM grading.

This module is **pure audio DSP + ML inference**. It has no Compose, no ViewModel, and no
networking. It knows nothing about cards, sessions, or grading — it only emits
`VoiceCaptureEvent`s. Orchestration (when to listen, what to do with an utterance) lives in
`feature:study`; the grading round-trip lives in `core:domain` / `core:data` / the Cloud Function.

---

## Where it sits in the full pipeline

```
core:domain + feature:study           core:voice (THIS MODULE)            core:domain / core:data / functions
────────────────────────────          ─────────────────────────           ───────────────────────────────────
RatedStudySessionCoordinator
  starts voice answering (Rated only)
        │
        ▼
StudySessionVoiceService  ──hosts──► TtsPlayer
  (foreground service)                  speaks the card question (Media3)
        │
        │ PlaybackEvent.QuestionFinished
        ▼
RatedStudySessionCoordinator opens the listening window
        │ VoiceCaptureSession.startListening()
        ▼
                              VoiceCaptureEngine.startListening()
                                AudioRecord (MIC, 16kHz mono PCM16)
                                  │  20ms / 320-sample frames
                                  ▼
                                SileroVoiceActivityDetector.isSpeech(frame)
                                  │  buffers to 512-sample Silero windows
                                  ▼  (OnnxSileroVadSession → ONNX Runtime)
                                utterance segmentation (pre-roll + hangover)
                                  │
                                  ▼  on utterance end:
                                PitchShiftVoiceObfuscator.obfuscate(pcm)
                                WavEncoder.encode(obfuscatedPcm)
                                  │
                                  ▼
                              emits VoiceCaptureEvent.UtteranceCaptured(wavBytes)
        │  collected by
        ▼
RatedStudySessionCoordinator (CaptureEvent.UtteranceCaptured)
        │ transcribeAndGradeSpokenAnswer(question, expectedAnswer, obfuscatedWav)
        ▼
                                                                    VoiceAnswerGradingRepository
                                                                      → VoiceGradingApiRouter (fake/real per stage)
                                                                      → RetrofitVoiceGradingApi → Cloud Function
                                                                          (STT transcription + transcript
                                                                           sanitization + LLM grading)
                                                                      → VoiceAnswerGrade { gradePercent, feedback }
        │  Graded(grade)
        ▼
RatedStudySessionCoordinator has NoticeSpeaker speak the grade (dedicated notice TTS),
waits 1s after it finishes, syncs the queue, then reads the next question.
```

The UX is **phone-in-pocket / screen-off**: the question is spoken, the user answers out loud, and
the grade is spoken back. There is no visual step in the happy path, which is why failures are
spoken, not toasted.

---

## Public surface

| Type | File | Responsibility |
|------|------|----------------|
| `VoiceCaptureEngine` | `VoiceCaptureEngine.kt` | The orchestrator. Owns `AudioRecord`, the 20ms frame loop, BT SCO routing, utterance segmentation, and event emission. `@Singleton`. |
| `VoiceCaptureEvent` | `VoiceCaptureEngine.kt` | Sealed events: `SpeechStarted`, `SpeechEnded`, `UtteranceCaptured`, `CaptureFailed`. |
| `CapturedUtterance` | `VoiceCaptureEngine.kt` | One VAD-bounded utterance: `obfuscatedPcm`, `wavBytes`, `durationMs`. Raw audio is never present here. |
| `VoiceActivityDetector` | `VoiceActivityDetector.kt` | Per-frame speech/silence classifier interface. |
| `SileroVoiceActivityDetector` | `SileroVoiceActivityDetector.kt` | The real (and only) VAD. Silero v5 neural model. Buffers 320-sample frames into 512-sample model windows. `@Singleton`. |
| `SileroVadSession` | `SileroVadSession.kt` | Inference seam over the ONNX model — kept as an interface so the buffering logic is JVM-unit-testable without native libs. |
| `OnnxSileroVadSession` | `OnnxSileroVadSession.kt` | ONNX Runtime implementation of the seam. Owns the `OrtSession`, the recurrent state, and the Silero context window. |
| `VoiceObfuscator` | `VoiceObfuscator.kt` | On-device voice-anonymization interface. |
| `PitchShiftVoiceObfuscator` | `PitchShiftVoiceObfuscator.kt` | VTLP pitch+formant shift + WSOLA time-stretch. |
| `WavEncoder` | `WavEncoder.kt` | Wraps 16-bit PCM mono in a WAV (RIFF) container. |
| `PcmPlayer` | `PcmPlayer.kt` | Debug-only `AudioTrack` playback (used by the Voice Debug screen; not in the production flow). |
| `VoiceCaptureModule` | `di/VoiceCaptureModule.kt` | Hilt bindings: VAD, obfuscator, and the `SileroVadSession` provider that loads the model asset. |

---

## Audio capture (`VoiceCaptureEngine`)

### Format & sampling

16 kHz, mono, signed 16-bit PCM, little-endian (`AudioFormat.CHANNEL_IN_MONO` +
`ENCODING_PCM_16BIT`).

- **16 kHz sample rate** — 16000 samples per second. By Nyquist this captures audio up to 8 kHz,
  which covers the speech-intelligibility band; it is also the rate Silero and essentially every
  STT model expects, so no resampling is needed anywhere in the pipeline. Every duration↔sample
  conversion in the module is `samples = ms * 16000 / 1000` (i.e. **16 samples per millisecond**).
- **Mono** — one channel. Halves the data vs. stereo and matches the model's expected `[1, N]`
  layout.
- **16-bit signed PCM** — each sample is a `Short` in `[-32768, 32767]`. This is the raw amplitude;
  `0` is silence, `±32767` is full scale. In Kotlin the buffer is a `ShortArray`; on the wire (WAV)
  it is 2 little-endian bytes per sample.

**Source is route-dependent** — see `preferredAudioSource()`. Phone mic uses
`MediaRecorder.AudioSource.MIC`, deliberately and non-obviously: `VOICE_RECOGNITION` (the "natural"
choice) routes through OEM noise-suppression/AGC/beamforming that, on some HALs (verified:
Realme/MediaTek), attenuates the primary mic to near silence — you get a coherent-but-inaudible
stream (peak amplitude ~8–480 out of 32767 while speaking normally). `UNPROCESSED` was also low on
that device. `MIC` (primary mic with standard processing) gives a usable level (peak ~9000–17000 for
normal speech). The `AudioChannelLayout{layoutMask: 16}` warning in logcat is the tell that the mono
capture path is being remapped by the OEM. Bluetooth routes use `VOICE_COMMUNICATION` instead — see
[BLUETOOTH_ROUTING.md](BLUETOOTH_ROUTING.md) for why.

### Frames & buffers

`AudioRecord` is a ring buffer that the HAL fills continuously; the capture loop drains it one frame
at a time.

**Frame = 20 ms = 320 samples = 640 bytes.** `FRAME_SIZE_SAMPLES = SAMPLE_RATE_HZ * FRAME_DURATION_MS
/ 1000 = 16000 * 20 / 1000 = 320`. At 2 bytes/sample that is 640 bytes per frame. 20 ms is the
standard VAD frame granularity — short enough to place utterance boundaries tightly, long enough to
keep per-frame overhead low.

**The read call.** `runCaptureLoop()` reads into a reused `ShortArray(320)`:

```
val read = audioRecord.read(frame, 0, frame.size)   // blocking; returns SHORTS read (≤ 320)
if (read <= 0) continue                              // 0 / negative = transient underrun or error
```

`read()` blocks until the requested samples are available, so the loop is naturally paced by
wall-clock audio time (~one iteration per 20 ms) without any manual sleep. A non-positive return is
a transient underrun and is skipped; hard failures throw and are caught as `CaptureFailed`.

**Ring-buffer size.** `createAudioRecord()` requests
`max(AudioRecord.getMinBufferSize(...), FRAME_SIZE_SAMPLES * Short.SIZE_BYTES * BUFFER_FRAMES)`.
`BUFFER_FRAMES = 8` → a floor of `320 * 2 * 8 = 5120 bytes` (~160 ms) of slack, so a scheduling
hiccup on the `Dispatchers.Default` thread doesn't drop audio before the next `read()`. The device
minimum is used if it is larger. If `getMinBufferSize` returns ≤ 0 (unsupported config) or the
`AudioRecord` doesn't reach `STATE_INITIALIZED`, capture returns `null` → `CaptureFailed`.

**Bluetooth earphones.** Routing (which device, when it's ready, mid-session switching) is owned by
`AudioRouteManager`, not this engine — the engine only binds `AudioRecord.setPreferredDevice(route.device)`
to whatever route it resolved. See **[BLUETOOTH_ROUTING.md](BLUETOOTH_ROUTING.md)** for the full
LE-Audio-first/SCO-fallback/BT-strict policy, the `MODE_IN_COMMUNICATION` requirement, and the bug
trail that got the onset-clipping fixed.

**Threading.** Capture runs as a single coroutine on `Dispatchers.Default`. Everything downstream
(VAD, obfuscation) is single-threaded per session as a result — the `SileroVadSession` is **not**
thread-safe and relies on this.

### Utterance segmentation

The engine layers boundary detection on top of the per-frame VAD:

| Constant | Value | Purpose |
|----------|-------|---------|
| `PRE_ROLL_FRAMES` | 10 (200 ms) | Audio kept *before* speech onset, so the attack of the first word isn't clipped. |
| `END_SILENCE_FRAMES` | 75 (**1.5 s**) | Trailing silence that closes an utterance (the "hangover"). |
| `MIN_UTTERANCE_FRAMES` | 15 (<300 ms) | Utterances shorter than this are discarded as noise. |
| `MAX_UTTERANCE_FRAMES` | 750 (15 s) | Hard cap per utterance. |

**The hangover is deliberately generous (1.5 s).** Spoken answers contain thinking-pauses; a
shorter window cut recordings off mid-answer. Segmentation — not the VAD — owns this: shrinking
`END_SILENCE_FRAMES` re-introduces premature cutoff even with a perfect VAD. Silero's accurate
soft-speech detection complements it by not padding pauses with false-positive frames.

When an utterance closes, `finishUtterance()` concatenates the buffered frames, obfuscates them,
**zeroes the raw buffer**, WAV-encodes the obfuscated copy, and emits `UtteranceCaptured`.

---

## Voice activity detection (Silero v5 via ONNX Runtime)

The VAD is a real neural model, not an energy threshold. (An energy-based detector previously
filled this seam; it was removed — it mis-classified soft/breathy speech as silence, which drove the
premature-cutoff problem.)

### Frame-size bridging (`SileroVoiceActivityDetector`)

The engine delivers **320-sample** frames, but Silero v5 at 16 kHz requires exactly **512-sample**
inference windows. The detector reconciles the two rates by buffering.

`isSpeech(frame)`:

1. Prepend any leftover samples: `buffer = carry + frame`.
2. While `buffer` holds ≥ 512 samples, slice off a 512-sample window, normalise it, and run one
   inference; advance the offset by 512.
3. Keep the remainder as the new `carry` (always < 512).
4. Return `lastProbability ≥ SPEECH_THRESHOLD` (0.5).

Because a 512-sample window doesn't divide a 320-sample frame, inference cadence is fractional but
periodic — it repeats every 8 frames (2560 samples = exactly 5 windows):

| frame | `buffer` before | windows run | `carry` after |
|------:|----------------:|:-----------:|--------------:|
| 1 | 320 | 0 | 320 |
| 2 | 640 | 1 | 128 |
| 3 | 448 | 0 | 448 |
| 4 | 768 | 1 | 256 |
| 5 | 576 | 1 | 64 |
| 6 | 384 | 0 | 384 |
| 7 | 704 | 1 | 192 |
| 8 | 512 | 1 | 0 |
| 9 | 320 | 0 | 320 | (cycle repeats)

Two consequences fall out of this design and are intentional:

- **Contiguous, non-overlapping windows.** The `carry` mechanism guarantees the model sees the audio
  stream partitioned into back-to-back 512-sample windows with no gaps or overlap — required for the
  recurrent state (below) to stay coherent. You cannot pad a short frame to 512 or overlap windows.
- **Partial frames hold their classification.** Frames that don't complete a new window (rows 1, 3,
  6, 9) return the **last** computed probability, so a single low-energy partial frame can never on
  its own flip an in-progress utterance to silence.

`speechProbability: StateFlow<Float>` mirrors the latest raw model probability for the debug screen.

### Data shapes, end to end

One utterance's worth of data as it changes type and shape through the pipeline:

| Stage | Kotlin type | Shape / size | Value domain |
|-------|-------------|--------------|--------------|
| `AudioRecord.read` | `ShortArray` | 320 (one 20 ms frame) | PCM16 `[-32768, 32767]` |
| VAD accumulation | `ShortArray` | grows to a 512 window | PCM16 |
| Normalisation (`normalisedWindow`) | `FloatArray` | 512 | `[-1.0, 1.0)` (`sample / 32768f`) |
| + Silero context (`OnnxSileroVadSession`) | `FloatArray` | 64 + 512 = **576** | `[-1.0, 1.0)` |
| ONNX `input` tensor | `OnnxTensor` | `[1, 576]` `float32` | `[-1.0, 1.0)` |
| ONNX `state` tensor | `OnnxTensor` | `[2, 1, 128]` `float32` | recurrent state |
| ONNX `sr` tensor | `OnnxTensor` | scalar `[]` `int64` | `16000` |
| ONNX `output` | `float[1][1]` | `[1, 1]` `float32` | probability `[0.0, 1.0]` |
| Segmentation buffer | `List<ShortArray>` | N × 320 frames | PCM16 (raw) |
| Concatenated utterance | `ShortArray` | N × 320 | PCM16 (raw — then zeroed) |
| Obfuscated | `ShortArray` | ≈ same length | PCM16 |
| WAV payload | `ByteArray` | 44-byte header + 2·samples | little-endian bytes |

### ONNX inference contract (`OnnxSileroVadSession`) — read this before touching it

Silero v5 is a small **recurrent** neural net (an LSTM behind a lightweight learned feature
front-end), not a stateless classifier. Three properties of its calling contract each silently
produce **~0 probability for all speech** if gotten wrong — no crash, no exception, just a dead VAD:

1. **64-sample context prefix (the one that actually blocked the green light).** The model input is
   **not** a bare 512 window. The feature front-end needs a short lookback, so the real input is
   `concat(previousChunkTail[64], chunk[512])` = **576 samples**; you carry the last 64 samples of
   each chunk forward as the next call's context (`CONTEXT_SIZE = 64` at 16 kHz; 32 at 8 kHz). Feed a
   bare 512 window and the model scores real speech at ~0.003; feed it *with* context and speech
   scores ~1.0. This is easy to miss because the input dimension is dynamic (`[None, None]`), so the
   model accepts 512 without error and simply produces garbage.
2. **Tensors must come from Java arrays / direct NIO buffers.** ONNX Runtime's JNI layer reads a
   **direct** (off-heap) buffer here; a heap `FloatBuffer.wrap(...)` is accepted but silently
   delivers **zeros** to native code → constant ~0.0005 output regardless of input. Build `input`
   and `state` via the array overload `OnnxTensor.createTensor(env, arrayOf(...))` (ORT copies the
   Java array into a direct buffer itself), and build `sr` as an explicit direct `LongBuffer`.
3. **The recurrent state must be carried across calls.** `state` is shaped `[2, 1, 128]`:
   dim 0 = the LSTM's two state vectors (hidden + cell), dim 1 = batch (1), dim 2 = 128 hidden units.
   It is zero on the first window, and each `run` returns an updated `stateN` that you copy back in
   (`copyNextStateIn`) for the next window. Because the model relies on this temporal context, a
   dropped/scrambled state degrades it to near-blindness — speech only "looks like speech" once the
   state has been fed the preceding windows in order (this is *why* the contiguous-window guarantee
   above matters).

**Per-call mechanics** (`run(window: FloatArray)`):

- The `OrtEnvironment` is a process singleton (`OrtEnvironment.getEnvironment()`); the `OrtSession`
  is created lazily on the **first** inference (parsing the 2.3 MB model must not run on the DI/main
  thread) and reused for the session's lifetime.
- Build the three input tensors (`input [1,576]`, `state [2,1,128]`, `sr []`), run
  `session.run(mapOf("input" to …, "state" to …, "sr" to …))`, which returns an `OrtSession.Result`.
- Read output `0` (`output`, `float[1][1]`) → probability; read output `1` (`stateN`,
  `float[2][1][128]`) → copy into the flat `state` array. Every `OnnxTensor` and the `Result` are
  closed via `use {}` — they wrap native memory and leak if not.
- Inputs/outputs are addressed by the model's declared names/order (`inputs=[input, state, sr]`,
  `outputs=[output, stateN]`), verified against the asset before bundling.

`reset()` (called on every `startListening()`) zeroes **both** the recurrent state and the context,
giving each session a clean temporal context.

### The model asset

`src/main/assets/silero_vad.onnx` — Silero VAD **v5**, ~2.3 MB, **MIT-licensed** (redistributable),
from `snakers4/silero-vad`. To update: replace the file, re-verify the I/O signature (input names,
state shape, context size) against the new version, and re-run the on-device check — Silero has
changed its calling contract between major versions before (this is exactly how the v5 context
requirement bites).

---

## Voice obfuscation (`PitchShiftVoiceObfuscator`)

**Privacy invariant: raw voice never leaves the capture stage.** `finishUtterance()` obfuscates,
then zeroes the raw buffer before anything downstream sees it. Only `obfuscatedPcm` / `wavBytes`
exist on `CapturedUtterance`.

The transform is **VTLP-style**: a linear resample shifts pitch *and* formants together by a
per-session random factor (±2–4 semitones, sign randomized), and a **WSOLA** (waveform-similarity
overlap-add) time-stretch restores the original duration so transcript timing is unchanged. It keeps
speech intelligible for STT while perturbing the raw biometric voiceprint.
`randomizeSessionShift()` re-draws the factor once per session so separate sessions can't be
correlated by a stable transform.

**Caveat (by design):** this is deterrence against casual re-identification, **not** cryptographic
anonymization — it will not defeat a determined speaker-identification model.

---

## How the study session drives it (`feature:study`)

The round's rules live in `core:domain`: `RatedSessionReducer` decides, and
`RatedStudySessionCoordinator` runs its effects and timers (Rated mode only, ADR-0025, ADR-0054).
The platform side lives in `feature:study`'s `StudySessionVoiceService`, so listening shares the
study session's foreground-service lifecycle: `VoiceCaptureSession` owns this module's
`VoiceCaptureEngine`, the `AudioRouteManager` session route and a `PARTIAL_WAKE_LOCK` renewed for
each listening window, so OEM battery managers can't starve the 20 ms frame loop.

The round's phases (`VoiceAnswerPhase`): `Idle → WaitingForQuestion → Listening → SpeechDetected →
Grading → SpeakingNotice → …`. Critically, **listening only runs between the question finishing
(`PlaybackEvent.QuestionFinished`) and either a captured utterance or an 8 s silence timeout** —
never while the question or a notice is being spoken, which closes the phone-speaker ↔ mic feedback
overlap.

On `UtteranceCaptured` the coordinator calls `VoiceAnswerGradingRepository` with the card's
question/expected-answer and the obfuscated WAV. That resolves through `VoiceGradingApiRouter`
(which can route each stage to a fake or the real backend independently) → the Cloud Function, which
does STT transcription, transcript sanitization, and LLM grading, returning a
`VoiceAnswerGrade { gradePercent, feedback }`, or a terminal failure with its classified cause.
`NoticeSpeaker` speaks the grade on a dedicated notice `TextToSpeech` engine (kept separate from
`TtsPlayer`'s Media3 card playback so notices can't corrupt its state machine); 1 s after it
finishes, the coordinator syncs the queue and reads the next question.

`CaptureFailed` and grading failures both fall back to a **spoken** notice — silent-drop was
explicitly rejected for this eyes-free UX.

---

## Caveats & gotchas

- **`onnxruntime-android` 16 KB page alignment.** Use the **1.21+ line** (this module is on 1.27.0),
  not 1.20.0 — older builds ship `.so`s whose LOAD segments aren't 16 KB-aligned, which fails on
  Android 15+ 16 KB devices and blocks Google Play submissions. Alignment is baked into the prebuilt
  `.so`; AGP/zipalign can't fix it.
- **Native library size.** ONNX Runtime ships four ABIs (~90 MB of native libs in a *universal* APK).
  This is fine via Play App Bundle per-device delivery (one ABI per device). Add `abiFilters` / ABI
  splits only if you ever produce a universal APK.
- **Emulators can't run the VAD.** ONNX Runtime's native `.so` requires a real device ABI; the VAD
  path throws on most emulators. `VoiceCaptureEngine` surfaces this as a `CaptureFailed` event (see
  the broad catch in `runCaptureLoop`) rather than dying silently.
- **The obfuscator is not strong anonymization** (see above).
- **BT SCO routing and FGS-mic survival under aggressive OEM battery managers are not yet
  hardware-verified** — they need a physical device with BT Classic earphones and a screen-off idle
  soak.
- **`SileroVadSession` is not thread-safe** — it is driven only from the single capture-loop thread.

---

## Debugging & testing

**Voice Debug screen** (`app` module, `BuildConfig.DEBUG`-only 🛠️ tab): exercises each stage in
isolation — live VAD indicator with the raw Silero `speech prob:` readout, "Play last answer"
(plays back the last VAD-bounded utterance to audit segmentation boundaries), raw-clip record/play,
obfuscation A/B, and the per-stage transcription/grading blocks.

**Isolating audio-ML failures:** the productive method here is to bisect the layers — is audio
reaching the model (check the frame peak amplitude), and is the model responding (check the raw
probability)? When the Android glue looks right, validate the model + protocol **offline in Python**
against the same `.onnx` (`say` → `afconvert` to 16 kHz mono → an onnxruntime inference loop) before
assuming the device is at fault. That is how the Silero context requirement was found.

**Unit tests** (`src/test`): `SileroVoiceActivityDetectorTest` uses a fake `SileroVadSession` to lock
in the 320→512 frame buffering and the partial-frame-stays-speech behavior without needing the native
runtime. The ONNX contract itself (context/state/sr) can't run on the JVM and is validated on-device
+ in Python.
