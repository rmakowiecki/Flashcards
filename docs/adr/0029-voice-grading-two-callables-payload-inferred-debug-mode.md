# Voice grading collapses to two Firebase callables; one grade function serves both production and debug via payload-inferred mode

## Status

Accepted. Refines ADR-0024 (four-`onRequest`-function proxy shape) and ADR-0028 (streaming callable for the production grade path). Supersedes the parts of both noted under "Supersession" below.

## Decision

The voice-grading backend collapses from **four `onRequest` REST functions** (ADR-0024) to **two `onCall` callable functions**, and the entire Android REST/Retrofit stack that talked to them is deleted. The whole codebase speaks one transport — Firebase callables.

### 1. Two deployed functions, both `onCall`

- **`entitlement`** — server-authoritative premium check. Returns `{ is_premium }`. Kept (not deleted) as the server-side seam that a future proactive paywall gate and eventual Play Billing validation hang off, even though its only *current* caller is the debug screen (see decision 6).
- **`transcribeAndGradeSpokenAnswer`** (renamed from `gradeVoiceAnswer`) — STT + sanitize (+ grade), streamed over one connection per ADR-0028.

`transcribe` and `sanitizeAndGrade` are **deleted**. They existed only to let the debug screen exercise individual pipeline stages; that capability is now served by a mode of the single grade function (decision 3) and, later, the Firebase emulator.

### 2. Whole-vertical rename to `transcribeAndGradeSpokenAnswer`

The production path is renamed at **every layer** — the deployed function, `VoiceGradingApi` and `VoiceAnswerGradingRepository.transcribeAndGradeSpokenAnswer`. There is no use case in between: the Rated study session coordinator calls the repository directly ([ADR-0054](0054-study-session-rules-in-domain-coordinators.md)). The old `gradeVoiceAnswer` / `gradeSpokenAnswer` names hid that the call owns two responsibilities (transcribe+sanitize, then grade); the new name states both. The debug sibling is named in parallel: `transcribeAndSanitize` (decision 4).

Renaming the deployed function is a `functions:delete gradeVoiceAnswer` + create `transcribeAndGradeSpokenAnswer` (Firebase has no in-place rename). Acceptable because there are no production users yet.

### 3. One grade function, two modes, inferred from the payload — not a flag

`transcribeAndGradeSpokenAnswer` decides what to do from the *presence of grading inputs*, not a debug switch:

- `audio_base64` is **always** required (non-empty string).
- `question` + `expected_answer` are **both-or-neither**, keyed on field *presence* (`undefined`), not truthiness. Passing exactly one is a malformed request → `invalid-argument`. When present, each must be a **non-empty** string: an empty or whitespace-only value is rejected as `invalid-argument`, never silently coerced into debug mode. Debug mode is entered only by *omitting* both fields.
- **Full mode** (both present): `sendChunk({ sanitized_transcript })` as soon as STT + sanitize finish, then return `{ grade, feedback }` as the terminal `StreamResponse.Result`.
- **Debug mode** (both absent): `sendChunk({ sanitized_transcript })`, then return an **empty** `Result` (`{}`). The grade LLM never runs — there is structurally nothing to grade.

This deliberately avoids a `stopAfterSanitize`-style debug flag. A flag would be a debug-only code path shipping in the prod function body — the exact "debug logic in prod" smell that motivated deleting `transcribe`/`sanitizeAndGrade`. Inferring from the payload is honest: with no question/answer, grading is *impossible*, not *disabled*.

### 4. The client exposes two intent-revealing methods over the one function

The single deployed function has two modes; the Android `VoiceGradingApi` surfaces them as two named entry points, so the mode is legible at the call site and the production streaming contract never has to represent "no grade":

- `transcribeAndGradeSpokenAnswer(cardId, question, expectedAnswer, wav): Flow<VoiceAnswerGradingEvent>` — production, unchanged in shape. Always grades, so `VoiceAnswerGradingEvent.Graded` stays non-nullable and honest. An empty terminal `Result` is *impossible* on this path; if one ever arrived it would correctly surface as a mapping error (a real server bug), not be silently swallowed.
- `transcribeAndSanitize(wav): Result<String>` — debug. Calls the same callable with no question/answer, collects, returns the sanitized transcript from the chunk, ignores the empty `Result`. Its return type (`Result<String>`) matches what a debug caller actually wants — a transcript — rather than forcing it to consume a `Flow<VoiceAnswerGradingEvent>` and cherry-pick one event. This is the old debug `transcribe`/`sanitizeAndGrade` pair collapsed into one method riding the production callable.

### 5. Entitlement moves to `onCall`; the entire REST/Retrofit stack is deleted

With the two REST debug functions gone, `entitlement` is the lone REST holdout. It becomes an `onCall` callable, and everything that existed to talk REST is deleted:

- `VoiceGradingRetrofitService`, `FirebaseAuthTokenInterceptor`
- `VOICE_GRADING_BASE_URL` (the `local.properties` value and its `BuildConfig` field)

A callable resolves its endpoint from the initialized `FirebaseApp` (bootstrapped from `google-services.json`), not from a hand-configured base URL. `google-services.json` is already load-bearing for Firebase Auth + Firestore, so this introduces no new dependency — it removes a *second*, parallel backend selector (`VOICE_GRADING_BASE_URL`). One file now selects the backend for auth, Firestore, and voice, which is exactly what the future dev/prod Firebase-project split keys on.

### 6. `entitlement` is kept despite being debug-only today, on an explicit product commitment

`entitlement` / `CheckVoiceGradingEntitlementUseCase` currently has exactly one caller — `VoiceDebugViewModel`. Production entitlement enforcement today is purely the server-side gate inside the grade function (`PERMISSION_DENIED` → `VoiceGradingEntitlementException`, handled reactively).

We keep the function anyway, on the commitment to wire a **proactive premium gate** into domain logic later: a free user must be shown a premium-upgrade screen *before* they reach the voice-answering feature, rather than being allowed to record, obfuscate, and upload audio only to be rejected. That is the product decision that retroactively justifies `entitlement` as a first-class function. Had we declined that commitment, intellectual honesty required deleting `entitlement` too — a debug-only function deployed "just for the debug screen" is precisely what this ADR eliminates elsewhere.

### 7. `FakeVoiceGradingApi` demotes to a test-only double; the runtime router is deleted

`VoiceGradingApiRouter` gated fake-vs-real on `BuildConfig.VOICE_GRADING_BASE_URL.isNotBlank()` — a guard that no longer exists once the base URL is deleted (decision 5). The router is deleted. `FakeVoiceGradingApi` survives only as a DI test double under `core/data/src/test`; production DI injects the real callable client directly. The debug screen loses its fake/real toggles. Offline / no-cost manual iteration becomes the Firebase emulator's job (deferred, see below), not an in-app fake.

## Context

Two forces converged. First, ADR-0028 had already turned the production grade path into a streaming `onCall` callable while leaving `entitlement`/`transcribe`/`sanitizeAndGrade` as ADR-0024 `onRequest` REST — leaving the codebase with two transports, a Retrofit stack maintained for three endpoints, and a standing "if you change one side, change the other" dual-contract burden (`functions/README.md`). Second, `transcribe` and `sanitizeAndGrade` were only ever debug scaffolding, yet were deployed to the live project as authenticated, entitlement-gated, paid endpoints — a cost and attack surface whose sole product consumer was a debug tool that ships in debug builds only.

The grade function *already* streams the sanitized transcript as its first event, before grading. So the debug screen's "transcribe + sanitize only" need was already satisfiable by reading that first chunk — the only thing a separate endpoint bought was skipping the grade LLM cost. That made a whole second function unjustifiable when a payload-inferred mode on the existing function does the same thing with one branch.

There are no production users yet, so breaking wire changes (renaming the function, dropping REST endpoints, base64 vs multipart) are free to make now.

## Alternatives considered

- **Keep the four-function REST shape (ADR-0024 as-is).** Rejected: two transports forever, a Retrofit stack for three endpoints, perpetual dual-contract sync, and two paid debug-only endpoints live in prod.
- **`stopAfterSanitize` request flag for debug mode.** Rejected: a debug-only branch shipping in the prod function — reintroduces the smell being deleted. Payload inference is structurally honest instead.
- **Treat "either grading input missing" as debug mode.** Rejected in favor of both-or-neither: exactly-one-present is never intentional; silently dropping to debug mode would hide a client bug by mysteriously skipping grading on the real path.
- **Debug mode returns `Result = { sanitized_transcript }` (no chunk), or sends the transcript on both chunk and Result.** Rejected in favor of "same chunk + empty Result": keeps the wire shape identical to full mode up to the point grading would differ, so the client mapper needs no debug-specific branch; the debug path is literally "the real path minus the final grade."
- **One client method for both modes** (blank question/answer; make `Graded` nullable or add a `NoGrade` variant). Rejected: softens the production contract to accommodate debug. Two intent-named methods keep production's `Graded` non-nullable and push the distinction to the call site.
- **Delete `entitlement`; read `users/{uid}/entitlement/premium` from Firestore directly on the client.** Rejected: couples the client to the doc shape, which must stay server-authoritative for future Play Billing validation. Cheaper at runtime but bakes in a client that trusts a doc.
- **Delete `entitlement` entirely (reactive-only enforcement).** Rejected only because of the decision-6 commitment to a proactive gate; absent that commitment this would have been the honest choice.
- **Keep `FakeVoiceGradingApi` + router as a runtime debug toggle.** Rejected: its config guard is gone with the base URL, and the emulator is the better future home for offline iteration. Kept the fake only as a test double.

## Supersession

- **ADR-0024, decision "four separate `onRequest` functions" and "each function its own `onRequest` export":** superseded. Two `onCall` functions now; `transcribe` and `sanitizeAndGrade` deleted. The URL-scheme rationale (relative paths matching `VoiceGradingRetrofitService`) is moot — Retrofit is deleted; callables resolve by name via `FirebaseApp`.
- **ADR-0024, entitlement/read-write split:** unchanged. `entitlement` still reads `users/{uid}/entitlement/premium`; only its transport changes (`onRequest` → `onCall`). The eventual Play Billing sync still touches only what *writes* that doc.
- **ADR-0028:** the streaming shape (chunk-then-result over one callable) stands. This ADR adds the payload-inferred debug mode, renames the function/vertical, and folds `entitlement` into the callable transport. ADR-0028's "worth a dedicated look at whether `checkEntitlement()` and other members migrate to callable or stay REST — left open" is resolved here: they migrate; REST is deleted entirely.

## Consequences

- **`functions/README.md` and `docs/design/premium-voice-grading-pipeline.md`** need updating: two functions not four, callable transport, the payload-inferred debug mode, and the deleted REST/Retrofit stack.
- **Redeploy is a delete + create**, not an update: `functions:delete gradeVoiceAnswer transcribe sanitizeAndGrade`, then deploy `entitlement` (now `onCall`) + `transcribeAndGradeSpokenAnswer`.
- **`google-services.json` becomes the single backend selector** for auth, Firestore, and voice — aligning cleanly with the future Option-A dev/prod project split (swap that one file per build flavor; no parallel base URL to keep in sync).
- **Node 20 is deprecated** (decommission 2026-10-30); bump `runtime`/`engines` to `nodejs22` opportunistically during this rework.
- **Deferred, explicitly not built here:**
  - Firebase emulator for offline/no-cost iteration — the in-app fake's replacement; only after it lands should the fake be collapsed further.
  - The proactive entitlement paywall gate in domain logic (the commitment behind decision 6).
  - The Option-A dev/prod Firebase-project split.
