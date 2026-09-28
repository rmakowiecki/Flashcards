# Premium Voice Answer Capture & Grading Pipeline

Design overview for a premium feature: while studying in Rated mode, the app listens for the user's spoken answer, obfuscates their voice on-device before it ever leaves the phone, transcribes it, sanitizes the transcript, and has an LLM grade completeness/quality. This works equally well **screen-on** (the user watching the session, reading their recognized answer and then the grade in the bottom sheet) and **screen-off/pocket** (hands-free, audio-only feedback via TTS) — see ADR-0028. Earlier revisions of this document treated the background/pocket case as the single driving constraint; that was wrong — both are first-class.

This document is an **intermediary design overview**, produced by a structured decision-tree interview. It captures the architecture and the rationale behind each choice so it can later be distilled into a concrete implementation plan. It intentionally does not yet contain task breakdowns, file-level plans, or Gradle module scaffolding. **See ADR-0024 (proxy shape), ADR-0027 (BT/screen-off mechanics), ADR-0028 (not background-only; streamed transcript-then-grade over one Firebase Callable connection), and ADR-0029 (voice grading collapses to two `onCall` callables; the entire REST/Retrofit stack deleted; one grade function serves prod + debug via payload-inferred mode) for decisions made after this document was first written — those ADRs are authoritative where they conflict with prose below that hasn't been fully rewritten yet.**

> **ADR-0029 correction (supersedes the REST/four-function framing throughout this doc):** the backend is now **two `onCall` callables** — `entitlement` and `transcribeAndGradeSpokenAnswer` (renamed from `gradeVoiceAnswer`). `transcribe` and `sanitizeAndGrade` are deleted; their debug capability is now a payload-inferred mode of the grade function (omit `question`/`expected_answer` → transcribe+sanitize only, grade LLM skipped). The Android REST/Retrofit stack (`VoiceGradingRetrofitService`, `FirebaseAuthTokenInterceptor`, `VoiceGradingApiRouter`, `VOICE_GRADING_BASE_URL`) is deleted; `google-services.json` is the sole backend selector. `FakeVoiceGradingApi` is now a test-only double.

## Scope

**In scope (this document):** the mechanism for capturing, obfuscating, and streaming the user's spoken answer, and getting it transcribed + graded.

**Explicitly deferred to separate design passes:**
- ElevenLabs premium TTS track (higher-quality voice output, including reading code aloud) — a separate feature track from this capture pipeline, sharing only the ElevenLabs vendor relationship.
- Grading LLM vendor selection (Claude vs Gemini vs OpenRouter multi-model A/B testing) — deliberately left open, see "Open decisions" below.
- Play Billing integration details (subscription purchase flow, Real-time Developer Notifications → Firestore entitlement sync) — required before this feature can ship, but is its own design effort.

## Why this shape: the driving constraints

Two equally valid usage modes shape this design (ADR-0028):

- **Screen-off/pocket**: the phone is in the user's pocket, they talk through earphones, there is no screen to tap or button to hold. This constraint is why VAD replaces a push-to-talk trigger, a foreground service + wake lock is mandatory (Android kills/throttles background mic access otherwise), errors must be communicated by voice (TTS) as a fallback, and Bluetooth earphone mic routing (SCO/LE Audio) is a first-class concern.
- **Screen-on**: the user is watching the session, wants to see their recognized answer promptly and then the grade (not just hear the grade), and expects manual controls (skip/prev/next/pause) to behave sensibly around the listening/grading window rather than race it.

Nothing in the capture/VAD/obfuscation/routing mechanics below differs between the two modes — the mic pipeline runs the same way regardless of whether the screen is on. What differs is purely the UI layer: whether transcript/grade text renders on screen, and whether TTS is the only feedback channel or a supplement to visible text.

## End-to-end pipeline

```
[Phone, foreground service — session-scoped, tied to study session lifecycle]

AudioRecord (VOICE_RECOGNITION source, 16kHz mono, BT SCO started if BT earphones)
        │  20ms PCM frames, continuous while session is active
        ▼
Silero VAD (onnxruntime-android, on-device ONNX model)
        │  flags speech-start / speech-end
        ▼
Utterance buffer (raw PCM, accumulated between speech-start and speech-end)
        │
        ▼
On-device obfuscation DSP: randomized pitch shift + formant shift
(re-randomized per session; PSOLA/phase-vocoder style transform)
        │  raw voice never leaves this stage un-obfuscated
        ▼
Wrap as 16-bit PCM WAV, 16kHz mono
        │
        ▼
Base64-encoded WAV in a Firebase Callable Function `data` payload
(`functions.getHttpsCallable("transcribeAndGradeSpokenAnswer").stream(...)` — auth token attached
automatically by the Firebase SDK, no manual interceptor needed; ADR-0029)
        │
        ▼
[Firebase Cloud Function — `onCall`, 2nd gen, stateless, ephemeral — see ADR-0028]

1. `request.auth` — Firebase Auth ID token already verified by the callable-function runtime
2. Verify server-side premium entitlement (Firestore record synced from Play Billing —
   client-side "is premium" flag is never trusted)
3. Forward WAV to ElevenLabs Scribe (server-held API key) → raw transcript
4. Sanitize LLM call: PII-strip + normalize filler/disfluencies → sanitized_transcript
5. `response.sendChunk({ sanitizedTranscript })` — client receives this immediately,
   over the same connection, well before grading finishes (ADR-0028)
6. Grade LLM call: prompt asks for {grade, feedback} given the sanitized transcript
   (server-held API key; vendor TBD)
7. `return { grade, feedback }` — delivered to the client as the stream's final result
8. Discard the audio (never written to disk/Cloud Storage — fully ephemeral)
        │
        ▼
Client shows sanitized_transcript in the bottom sheet when step 5 arrives, then the Rating and
feedback once step 7 arrives, at least 1 s later (ADR-0053) — two on-screen updates, one
spoken grade notice, one connection
        │
        ▼
Client displays sanitized_transcript and grade/feedback transiently, on screen, during the session
Only the resulting Terminal State/outcome — never the transcript itself — is persisted, at the
Summary commit (ADR-0014)
```

**Sanitize now runs in phase 1 (transcribe stage), not bundled into the grade LLM call as originally designed** — the text shown to the user must already be PII-stripped and disfluency-normalized, so sanitize can't wait for grading to finish (ADR-0028, decision 3).

## Client-side details

### Module placement

- **`core:voice`** (new module): `VoiceCaptureEngine` (AudioRecord + BT SCO management), Silero VAD wrapper, obfuscation DSP. No Compose/ViewModel dependencies — reusable and unit-testable in isolation from any specific feature.
- **`feature/study`**: orchestrates `core:voice` into the study session flow, alongside the existing `TtsPlayer` (`feature/study/src/main/kotlin/com/rossomak/flashcards/feature/study/voice/TtsPlayer.kt`). Owns the foreground service lifecycle, wake lock, consent screen, and upload via a repository (Retrofit-based `NetworkModule`, per this project's existing Hilt DI convention in AGENTS.md).

Rationale: capture/VAD/obfuscation logic has no inherent dependency on the study-session concept and could plausibly be reused elsewhere; session orchestration is genuinely study-specific and belongs where `TtsPlayer` already lives.

### Background listening scope

Session-scoped only: the mic/VAD pipeline is active only while a study session's foreground service is alive — the same lifecycle `TtsPlayer`'s `MediaSessionService` already uses for voice playback. It does **not** persist after the app is swiped away or the session ends. A true always-on/system-wide listener was considered and rejected: it multiplies battery cost, privacy exposure, and Play Store policy burden for no stated benefit — the actual need is "hands-free during an active study session," not "always listening."

Rated full-voice sessions support **screen-off** (a hands-free walk, answering aloud without looking at the device) as a first-class case, alongside **screen-on** (the user watching, reading their recognized answer and then the grade — ADR-0028); neither is "the" target use case to the exclusion of the other. Fast mode already proves the screen-off TTS-playback lifecycle; Rated full-voice reuses that same playback service and adds the listen+grade turn (mic capture is net-new and Rated-only — Fast mode is consume-only, no answering). Capture and playback share **one dual-typed foreground service** (`mediaPlayback|microphone`), so the existing `MediaSessionService` gains the `microphone` FGS type + `FOREGROUND_SERVICE_MICROPHONE` permission (required on Android 14+; target SDK 36) — one lifecycle, one notification, one wake lock. `MediaSession`/media-button (bud-tap) controls drive pause/resume/skip/repeat with the screen off; the turn loop auto-advances after grading-feedback TTS finishes + ~1s.

### Capture trigger: VAD, not push-to-talk

Continuous background use with the phone pocketed rules out any button-based trigger. **Silero VAD** (ONNX model, via `onnxruntime-android`) was chosen over WebRTC's older GMM-based VAD (rougher accuracy in noisy pocket/earphone conditions) and over relying on Android's `SpeechRecognizer` built-in endpointing (less control over chunk boundaries, ties capture to on-device recognizer availability). Silero is small (~1-2MB), fast (~1ms/chunk on CPU), and runs fully offline.

### Audio capture API and earphone routing

`AudioRecord`, 16kHz mono — raw PCM frames are needed for real-time VAD analysis, which rules out `MediaRecorder` (writes encoded files, not raw frames). The **audio source** is deliberately a single swappable line (`preferredAudioSource()`), pending an on-device A/B test — see below. See ADR-0027 for the full routing decision.

**BT-strict routing.** If a Bluetooth *mic-capable* device is connected, capture must bind to its mic — falling back to the phone mic while the phone is pocketed produces muffled, useless audio for grading (worse than failing). "Mic-capable" means a connected communication input device of type `TYPE_BLUETOOTH_SCO` **or** `TYPE_BLE_HEADSET`; A2DP-only devices (output, no mic) do not count — with those, phone mic is the only option and is allowed (logged). Detection and dynamic switching use `AudioManager` device-type routing only — **no `BLUETOOTH_CONNECT` permission** (routing is by type, not by named device).

**LE-Audio-first, SCO fallback.** LE Audio (`TYPE_BLE_HEADSET`, Android 13+) carries mic + hi-fi output *simultaneously*, so it is used session-long with no quality penalty and no per-turn toggling. When only Classic Bluetooth is available, SCO/HFP is used session-long instead — mono, narrowband (8/16kHz), tinny TTS, but the only reliable BT Classic mic path. LE Audio is a genuine first-class path here, not merely a second code path bolted onto SCO. Wired earphones need no special handling.

**SCO-ready handshake** (fixes the current fire-and-forget order/async bugs): (1) detect the BT mic device; (2) request the route — `setCommunicationDevice()` on API 31+, `startBluetoothSco()` below; (3) **wait for ready** — pre-31 via `ACTION_SCO_AUDIO_STATE_UPDATED` → `SCO_AUDIO_STATE_CONNECTED`, API 31+ by polling `communicationDevice`, ~3s timeout + 1 retry; (4) **then** `createAudioRecord()` and `setPreferredDevice(scoInput)`; (5) `startRecording()`, then **verify `routedDevice`** is the BT device — a mismatch is a failure (some OEMs report SCO connected while input stays on the phone mic). On timeout/mismatch capture strict-pauses (does not silently use the phone mic).

**Dynamic mid-session changes.** An `AudioManager.AudioDeviceCallback` + SCO-state receiver observe connect/disconnect during a session (long in-pocket walks reconnect buds mid-run). Switches happen **at utterance boundaries only** — never mid-clip, so a grading recording is never corrupted; a partial clip on an abrupt drop is discarded. BT-appears → auto-adopt; BT-drops → strict-pause + auto-reacquire on reconnect. Rare route events (drop / reconnect / phone-fallback) are **detected and logged only** for v1 — no audible cue yet (happy-path assumption); the engine still exposes the current capture route as state for the debug screen and future audible notices.

### Obfuscation: what, where, why

**Where:** on-device, before any audio leaves the phone. This was treated as non-negotiable given the stated goal ("preserve user privacy and right to their voice") — obfuscating server-side would mean the raw, unaltered voiceprint still transits the network and touches server memory/logs first, which doesn't actually satisfy that goal.

**What:** randomized pitch shift (~±2–4 semitones) plus formant shift (vocal-tract-length perturbation), re-randomized each session. This keeps speech fully intelligible for downstream transcription while meaningfully perturbing the raw biometric voiceprint. A neural voice-conversion approach (converting every speaker to one of several fixed "anonymous" voices) was considered and rejected for v1: real-time on-device voice conversion is heavy (model size, latency, battery) and immature for this use case — high implementation risk for a first version.

**Important caveat, stated plainly for future reference:** pitch/formant randomization is *not* cryptographically strong anonymization. It deters casual re-identification and naive voice-clone training; it is not a guaranteed defense against a determined speaker-identification model. If a stronger guarantee is ever required, that's the point to revisit neural voice conversion.

### Encoding

Raw 16-bit PCM, 16kHz mono, wrapped in a WAV container. Utterances are short (seconds, not minutes) — a 10-second answer is ~320KB uncompressed, so Opus compression's ~10x size reduction wasn't judged worth the added complexity (MediaCodec Opus encoder fragmentation, extra encode step after obfuscation, most STT providers wanting Opus demuxed from ogg/webm anyway). The obfuscation DSP also already operates on raw PCM, so WAV requires zero extra conversion work.

**Transport correction (ADR-0028):** the grading call moved from a Retrofit multipart POST to a Firebase Callable Function, whose `data` payload is JSON, not raw binary — the WAV bytes must be base64-encoded into that payload. A ~320KB WAV becomes ~426KB base64 (the usual ~33% overhead), which is still negligible for an utterance-length clip over a modern connection. Whether `checkEntitlement()` and other `VoiceGradingApi` members also migrate to callable functions, or stay plain REST since they don't need streaming, is left open for the implementing session.

### Consent and foreground notification

Required, not deferred: an explicit first-run consent screen (explaining that the mic listens in the background during study sessions, that voice is obfuscated on-device before ever leaving the phone, and what it's used for) plus a persistent notification whenever the foreground service is actively listening (with a stop action). This is both a Play Store policy requirement for background `RECORD_AUDIO` use and a direct expression of the feature's own stated privacy motivation.

### Wake lock

A `PARTIAL_WAKE_LOCK`, scoped to the foreground service's listening lifecycle (acquired when listening starts, released when the session ends or the service stops). Without it, aggressive OEM battery managers (Samsung, Xiaomi, etc.) can throttle background threads enough to drop frames or miss VAD boundaries mid-utterance, even with a foreground service running.

### Upload failure handling

Retry with exponential backoff (in-process retry or WorkManager). If it ultimately fails, the failure is classified as no connection or a service problem ([ADR-0053](../adr/0053-voice-answer-sheet-states-and-live-input-level.md)). `VoiceAnswerController`'s dedicated notice TTS speaks a short notice naming the cause ("Couldn't reach grading. Check your connection. Moving on." / "Grading had a problem, moving on."), and a matching snackbar shows for a user watching the screen. The spoken notice is the one that matters: in pocket use there's no screen to look at, so any failure signal has to be audible. The card then moves on, with no Attempt consumed. Silent-drop was considered and rejected: the user would have no idea their answer wasn't graded, undermining trust in the feature.

## Debug dev screen: manual per-stage testing

Every stage of this pipeline is hard to verify by ear alone (is the VAD boundary right? did the pitch shift actually change? did the transcript come back garbled?), and the feature only becomes usable hands-free once several independently-built stages are wired together. To keep each stage independently verifiable as it's built, a **debug-only 5th bottom nav tab** is added: 🛠️ Voice Debug (alongside the existing 4: Home, Study, Progress, Settings — see `SYSTEMDESIGN.md`'s Nav graph structure). Gated behind `BuildConfig.DEBUG` (or a debug flag, consistent with the existing "Debug Curation (debug builds only)" screen already in this codebase's `SYSTEMDESIGN.md`) — never present in release builds, never part of the premium feature's real UX.

The screen exposes each pipeline stage as its own testable block, independent of the others:

- **VAD trigger test**: start/stop listening, live speech-detected/silence indicator, so VAD start/end boundaries can be checked against actual speech without needing a full session running.
- **Raw capture playback**: record a short clip via `AudioRecord`, play it back unmodified — confirms capture + (if relevant) BT SCO routing works before any DSP is involved. **Audio-source A/B test (pending):** the swap from `VOICE_RECOGNITION` to `MIC` (commit d54cb34) is confounded — that commit also fixed silent loop-death (missing `Log.e`) and rode alongside the Silero v5 context-prefix fix, so it is unproven that `VOICE_RECOGNITION`'s OEM DSP was ever the near-silence cause. This block should capture the same clip under each source (`VOICE_RECOGNITION`, `VOICE_COMMUNICATION`, `MIC`, `UNPROCESSED`) and report per-source RMS level, tested on Realme 9 Pro (phone mic) **and** a BT headset (SCO route), to pick the `preferredAudioSource()` line empirically rather than by assumption. Note the tension: `VOICE_COMMUNICATION` routes BT-SCO input most reliably but re-enables the OEM DSP that may have gutted the phone-mic signal.
- **Obfuscation A/B playback**: play the same captured clip both pre- and post-obfuscation, so the pitch/formant shift's effect (and continued intelligibility) can be judged by ear, and re-randomization can be verified across repeated takes.
- **Transcription test**: send a captured (obfuscated) clip through the STT step in isolation and show the raw transcript returned — verifies the ElevenLabs Scribe integration independent of grading.
- **Sanitize + grade test**: feed an arbitrary/typed transcript (bypassing audio entirely) into the sanitize+grade LLM call and show the returned `{sanitized_transcript, grade, feedback}` JSON — verifies the Cloud Function's LLM step without needing a live recording each time.
- **Entitlement check test**: trigger the Cloud Function call as a non-premium and as a premium test account, to confirm the server-side gate actually rejects/allows correctly.

Each block should show raw request/response data (not just a pass/fail), since the point is inspecting intermediate output while stages are still being built out, not asserting correctness automatically.

## Backend details

### Why a backend is unavoidable

Calling ElevenLabs Scribe and an LLM grader directly from the client (skipping a backend entirely) was considered, since it would simplify the client. Rejected: any API key embedded in the APK (including `BuildConfig` fields) is extractable by decompiling the app. For a paid third-party service, that's a direct cost-abuse vector, and it's also flatly inconsistent with this project's own AGENTS.md Security section ("NEVER commit API keys, tokens, or secrets to Git"). The sanitize/grade LLM call has the exact same problem. A backend — even a thin one — is the only way to keep those secrets server-side.

### Proxy shape: Cloud Function, not Cloud Run

Since the app already uses Firebase (Firestore, Auth), a Firebase Cloud Function was chosen over standing up a dedicated server, to avoid new infrastructure to run/maintain/monitor. Buffering the full VAD-bounded utterance and sending one call once speech ends (rather than streaming audio *up* chunk-by-chunk) is still the shape — the added latency (waiting for the user to stop talking before transcription starts) is invisible either screen-on or screen-off.

**Correction (ADR-0028):** the original reasoning here — "this feature deliberately runs in the background with no visible transcript," used to reject any response streaming — no longer holds, since a visible transcript is now a requirement. This does *not* mean moving to Cloud Run/WebSockets, though: Cloud Functions 2nd gen (built on Cloud Run under the hood) has **native streaming callable-function support** (`onCall` + `response.sendChunk()` server-side, `.stream().asFlow()` client-side) — no separate long-lived-connection infrastructure needed, no new deployment target. This endpoint moves from a plain `onRequest` proxy to `onCall`, which also means Firebase Auth ID token verification (`request.auth`) is handled by the callable-function runtime automatically, rather than needing the client's `FirebaseAuthTokenInterceptor` for this specific call. See ADR-0028 for the verified API shape and version requirements.

### STT vendor: ElevenLabs Scribe

Chosen primarily for vendor consolidation: this project is already integrating ElevenLabs for premium TTS (separate track), so using Scribe for STT means one vendor relationship/API key/billing account instead of two for the voice pipeline as a whole. Google Cloud Speech-to-Text and OpenAI Whisper were both viable alternatives on pure accuracy grounds but don't share that synergy.

**Important correction surfaced during design:** ElevenLabs Scribe is transcription-only — it has no built-in PII redaction/sanitization feature. (Deepgram is the one major STT vendor with a native `redact` parameter; ElevenLabs isn't.) This means the "sanitize" step below has to be built, not obtained for free from the STT vendor.

### Sanitize, then grade: two sequential LLM calls, streamed back over one connection

**Superseded by ADR-0028** — originally this was a single combined LLM call returning `{sanitized_transcript, grade, feedback}` all at once. It is now **two sequential LLM calls within the same function invocation**:
1. **Sanitize** (given the raw ElevenLabs transcript): strips PII (names, emails, phone numbers the user might blurt out) and normalizes filler/disfluencies (um, uh, repeated words) → `sanitized_transcript`. Result is sent to the client immediately via `response.sendChunk()`, before grading starts.
2. **Grade** (given the sanitized transcript, question, and expected answer): returns `{grade, feedback}` as the function's final return value.

This split exists specifically so the user sees their own (cleaned-up) spoken answer promptly, rather than waiting for grading to finish before anything appears on screen. It costs one extra LLM round-trip server-side (two calls instead of one) in exchange for that responsiveness — judged worth it once a visible transcript became a requirement.

The alternative — a separate deterministic regex/NER redaction pass instead of an LLM sanitize call — would give a stronger technical guarantee that PII never reaches a third-party model's context at all, at the cost of its own false-negative risk (regex/NER missing creative PII phrasing). Not revisited by ADR-0028; still an open option if the LLM-based sanitize proves unreliable in practice.

### Grading LLM vendor — open decision

Deliberately left open. During the interview, an initial "Claude has more ecosystem synergy than OpenAI" framing was raised and then explicitly corrected: **neither Claude nor OpenAI has any real infrastructure synergy with this project's existing Firebase/ElevenLabs stack** — both require an independent API key/account for the same reason. The one vendor with genuine infra synergy would be Gemini via Vertex AI (shared GCP IAM/billing with the existing Firebase project and Cloud Functions).

The actual decision to make later, honestly stated:
- **Claude**: no infra-synergy argument, just ecosystem familiarity (already developing with Claude Code).
- **Gemini**: real GCP/Firebase billing and IAM synergy, less battle-tested than Claude/GPT for this specific open-ended grading task.
- **OpenAI**: no particular argument for it from this project's context, absent an independent reason (e.g. existing credits).
- **OpenRouter**: raised by the user as a way to call multiple LLMs through one integration, enabling A/B testing/comparative grading-quality analysis across models without committing to a single vendor upfront. This is a live option and may end up being the actual answer — worth designing the grading call behind a provider-agnostic interface regardless of which vendor(s) end up behind it.

### Entitlement enforcement

The Cloud Function must verify server-side that the caller has an active premium subscription **before** calling ElevenLabs/the grading LLM — trusting a client-side "is premium" flag alone was rejected, since a modified or rooted client could fake that flag and any user who found the endpoint URL could burn the ElevenLabs/LLM budget for free with no subscription at all. This requires syncing Play Billing purchase state to a server-side Firestore record (e.g. via Real-time Developer Notifications) that the Cloud Function checks per request — the mechanics of that sync are deferred to a separate design pass, but the requirement itself is locked in as part of this pipeline.

### Data retention

Fully ephemeral, server and client alike: the Cloud Function receives the WAV in the request body, forwards it to ElevenLabs in-memory, and discards it once the transcript comes back — nothing is written to Cloud Storage or disk. Neither the sanitized transcript nor the grade is persisted anywhere, in Firestore or otherwise (see ADR-0014/ADR-0028) — both are shown on screen transiently, during the session, to display grading feedback, then discarded once the card's `FlashcardResult.Rated` is sealed. Only the resulting outcome (Terminal State, Attempts used, previously-mastered flag) survives, into `cardResults`. Persisting the obfuscated audio itself (e.g. to let a user replay their own answer, or for grading disputes/audits) was considered and rejected for v1: it adds real storage cost, a retention policy, and GDPR-style deletion-on-request obligations for a capability nobody has asked for yet, and works against the "ephemeral by design" privacy posture the rest of the pipeline commits to.

## Implementation strategy for blocked/inaccessible dependencies

This feature has several hard external dependencies an implementing agent won't necessarily have credentials or infrastructure access for during a build-out session: ElevenLabs API key, a grading LLM API key, a deployed Cloud Function, a Play Console subscription product wired to real entitlement sync, a physical device with paired Bluetooth earphones to validate SCO routing. None of these being available is a reason to leave a stage half-built or stubbed with a `TODO`.

**Rule: implement the full pipeline end-to-end every time. Wherever a real integration is blocked by a missing credential, missing infra, or missing hardware, fake it at the data layer instead of stopping.**

- Fakes live behind the same repository/interface boundary the real implementation would use (e.g. a `VoiceGradingApi` interface with a `FakeVoiceGradingApi` and a future `RealVoiceGradingApi`, swapped via the existing Hilt `NetworkModule`/`RepositoryModule` convention). Swapping fake → real later is a DI wiring change, not a rewrite.
- Fakes should mimic realistic behavior, not just return a hardcoded stub: a faked ElevenLabs Scribe response should look like a plausible transcript for the audio content actually spoken (or a plausible transcript for a representative sample answer, if transcript content can't be derived), a faked grading LLM response should return a plausible `{sanitized_transcript, grade, feedback}` shape with varied, believable grades/feedback — not always the same canned "success" response, since that would mask bugs in how the app handles varied real responses (partial credit, low scores, malformed feedback, etc.).
- The debug dev screen (above) is where fakes get exercised and eyeballed during development, and should support toggling fake vs. real per stage, so replacing a fake with the real integration later can be verified in the same place it was faked.
- Faking never applies to the security-relevant boundaries themselves: the entitlement check logic, the "raw audio never leaves the device unobfuscated" guarantee, and the "audio is never persisted" guarantee must be implemented for real even if the systems they'd talk to (Play Billing, Cloud Storage) are mocked out — these are the properties the whole design exists to guarantee, not integration details to fake around.

**Rule: whenever a step gets faked instead of really implemented, this must surface as an explicit, itemized to-do list handed back to the user** — not buried in code comments or left implicit. Each item should name exactly what's blocking it and what the user needs to do: e.g. "add `ELEVENLABS_API_KEY` to `local.properties`", "deploy the Cloud Function to project X", "create the premium subscription product in Play Console and wire RTDN", "test BT SCO routing on a physical device with earphones paired — untestable on emulator". This checklist is a required deliverable of any implementation pass on this feature, exactly as much as the code itself.

## Security summary

- No third-party API key (ElevenLabs, grading LLM) ever ships inside the Android app.
- Raw, unobfuscated voice audio never leaves the device.
- Obfuscated audio is never persisted anywhere, client or server.
- Premium entitlement is checked server-side per request, not trusted from the client.
- Transcripts and grades are never written to Firestore — only shown transiently on screen during the session. Only the resulting `FlashcardResult.Rated` (Terminal State, Attempts used, previously-mastered flag) is persisted, at the Summary commit.

## Open decisions carried forward

0. ~~Whether voice-graded Attempts count toward Terminal-State/Mastery/XP~~ — resolved by ADR-0026: they unify with manual Rating. That system is designed by ADR-0044/0046/0016/0014 and not yet built. See also ADR-0026 for the on-screen reveal-timing and grade-display decisions made alongside it.
1. Grading LLM vendor (or OpenRouter multi-model setup) — see above.
2. ElevenLabs premium TTS track — separate design pass.
3. Play Billing entitlement sync mechanics (RTDN → Firestore) — separate design pass; required before ship, not before implementation of the capture mechanism itself.
4. Exact obfuscation DSP implementation (which library/algorithm implements the pitch/formant shift on Android) — implementation detail, not yet chosen.
5. ~~Firestore schema for storing sanitized transcript + grade history per card/session~~ — resolved by ADR-0014, and reversed from earlier revisions of this document: **no transcript is persisted at all.** The sanitized transcript is shown transiently, on screen, during the session, to display grading feedback — then discarded. Only the resulting outcome (Terminal State, Attempts used, previously-mastered flag) is written to the session document's embedded `cardResults` map — voice grading only ever produces a Rated result, so this is always a `FlashcardResult.Rated` entry. Nothing today needs the transcript back after the session ends; a future read-only revisit UI, if ever built, would need its own design and its own decision about persisting transcripts, not an assumption carried from here. Session-state tracking and any such revisit UI remain undesigned.
6. ~~Whether the grading response can be split into a fast transcript-first phase and a separate grade phase without a second client round-trip~~ — resolved by ADR-0028: yes, via Firebase's native streaming callable functions (`onCall` + `sendChunk()`), not raw HTTP streaming.
7. ~~`checkEntitlement()` and other non-audio `VoiceGradingApi` members: migrate to callable functions for consistency with the now-`onCall`-based grading endpoint, or leave as plain REST since they don't need streaming~~ — **re-resolved by ADR-0029**: they migrate; the entire REST/Retrofit stack is deleted. `entitlement` becomes an `onCall` callable; `transcribe`/`sanitizeAndGrade` are deleted, their debug capability folded into a payload-inferred mode of `transcribeAndGradeSpokenAnswer`. (The earlier "leave as plain REST" resolution stood only until the maintenance cost of two transports + a Retrofit stack for two paid debug-only endpoints outweighed it.)
8. ~~`VoiceGradingApi`/`VoiceAnswerGradingRepository`/`GradeSpokenAnswerUseCase`/`VoiceAnswerController`/`FakeVoiceGradingApi` file-level changes needed to actually carry the two-phase result through the app~~ — implemented (ADR-0028) then renamed whole-vertical (ADR-0029): `transcribeAndGradeSpokenAnswer` returns `Flow<VoiceGradingStreamEventDto>` (data) / `Flow<VoiceAnswerGradingEvent>` (domain), collected in `VoiceAnswerController.gradeUtterance()` and written into `VoiceAnswerState.sanitizedTranscript` ahead of `lastGrade`. `RealVoiceGradingApi` is now callable-only (no Retrofit). `FakeVoiceGradingApi` (test-only double) emits a `TranscriptChunk` after a simulated upload+STT+sanitize delay, then a `Graded` after a second simulated grading-LLM delay, matching the real streamed call's two-window latency shape.
