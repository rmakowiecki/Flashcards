# Voice grading Cloud Functions

Backend for the premium voice-answering pipeline. See `docs/design/premium-voice-grading-pipeline.md`
for the full pipeline design, `docs/adr/0024-voice-grading-cloud-function-proxy.md` for the original
proxy shape, and `docs/adr/0029-voice-grading-two-callables-payload-inferred-debug-mode.md` for the
current shape (two callables, one grade function serving prod + debug via payload-inferred mode).
This file is the practical "how to set it up / how it works" reference; the ADRs are the "why".

## Project

Firebase project: `flashcards-8ad6d` (see `.firebaserc` at repo root). Region: `us-central1` for
every function (`RUNTIME_OPTIONS.region` in `src/index.ts`).

## Transport

Both functions are Firebase **`onCall` callables** (ADR-0029) — not REST `onRequest`. The Android
client talks to them via the Firebase Functions SDK (`FirebaseFunctions.getHttpsCallable(name)`),
which resolves the endpoint from the initialized `FirebaseApp` (`google-services.json`) and attaches
the caller's Firebase ID token automatically. There is **no** `VOICE_GRADING_BASE_URL`, no Retrofit,
and no `Authorization` header to manage by hand — `request.auth` is populated (and verified) by the
callable runtime, or the call is rejected `unauthenticated` before any code runs.

## Layout

```
functions/
  package.json / tsconfig.json      — Node 22, TypeScript, strict
  src/index.ts                      — the 4 exported onCall functions
  src/lib/entitlement.ts            — Firestore entitlement read (isPremiumUser)
  src/lib/elevenlabs.ts             — ElevenLabs Scribe STT call
  src/lib/grading.ts                — Vertex AI Gemini sanitize + grade calls
  src/lib/httpError.ts              — HttpError(statusCode, message) thrown by elevenlabs/grading libs
  src/lib/xpScoring.ts              — pure XP/level calculation, mirrored by the client's SessionXpCalculation.kt
  src/lib/authGuard.ts              — requireActiveSession: revoked/deleted-caller guard for writing callables (ADR-0057)
  src/lib/submitStudySession.ts     — validation + the session-commit transaction
  src/lib/submitBugReport.ts        — Bug Report validation, per-User rate limit and write (ADR-0058)
  src/lib/requestValidation.ts      — shared field checks for the callables' request validators
  src/lib/*.test.ts                 — emulator-backed tests for the above (see "Local iteration" below)
```

`admin.initializeApp()` runs once at module load in `index.ts` (Application Default Credentials —
no service account key needed inside the deployed function itself; that's only used locally for
the Admin SDK scripts under `scripts/seed/` and one-off setup commands below).

## Function contract

Mirrors `core/data/.../network/VoiceGradingApi.kt` / `RealVoiceGradingApi.kt`.

### `entitlement`

Server-authoritative premium check. No request payload. Returns `{ "is_premium": bool }` by reading
`users/{uid}/entitlement/premium`. Rejects `unauthenticated` if no ID token. Its only current caller
is the debug screen; kept as the seam a future proactive paywall gate hangs off (ADR-0029 §6).

### `transcribeAndGradeSpokenAnswer`

STT + sanitize (+ grade), streamed over one connection (ADR-0028): `response.sendChunk({ sanitized_transcript })`
as soon as STT + sanitize finish, then a terminal `Result`. **The mode is inferred from the payload,
not a flag** (ADR-0029 §3):

| Payload | Terminal `Result` | Grade LLM |
|---|---|---|
| `audio_base64` + `question` + `expected_answer` (full mode) | `{ grade, feedback }` | runs |
| `audio_base64` only (debug mode) | `{}` (empty) | skipped |

Rules enforced server-side:
- `audio_base64` is **always** required → `invalid-argument` if missing.
- `question` + `expected_answer` are **both-or-neither** → exactly one present is `invalid-argument`.
- Premium entitlement is required → `permission-denied` (mapped client-side to `VoiceGradingEntitlementException`).

`card_id` is accepted but unused server-side — the client includes it and persists results itself
(`{cardId, sanitizedTranscript, gradePercent, feedback}`), not this function.

The Android client surfaces the two modes as two intent-revealing methods over this one function
(ADR-0029 §4): `transcribeAndGradeSpokenAnswer(...)` (production, always grades) and
`transcribeAndSanitize(wav)` (debug, rides the same callable with no question/answer and reads only
the first streamed chunk).

### `submitStudySession`

Server-authoritative session commit. Replaces the client-side write path
(`CommitStudySessionUseCase` / `StudySessionRemoteDataSource`):
the client submits what happened during a session, and this function alone computes and writes its
XP, level and progress. Named "submit", not "report" — this codebase's curation feature already owns
"report" for a flagged-content signal, so a finished session is submitted, never reported. `index.ts`
only delegates; the real logic lives in `src/lib/submitStudySession.ts` (the revoked-token guard
from `src/lib/authGuard.ts`, then validation and the transaction) and `src/lib/xpScoring.ts` (the pure XP/level calculation, mirrored by the
client's `calculateSessionXp` in `SessionXpCalculation.kt`).

Everything happens in one Firestore transaction, keyed for idempotency on the client-generated
`sessionId`: if `sessions/{sessionId}` already exists, the call is a no-op that returns the same
result again — safe for the client's own at-least-once retry queue to call
freely. See `submitStudySession`'s own doc comment in that file for the full read/write shape.

Before anything else it rejects, with `unauthenticated`, a caller whose tokens were revoked or whose
Auth user was deleted or disabled (ADR-0057). The client's delivery worker treats that code as
transient and keeps the session queued.

## One-time setup (from a clean checkout)

All commands below run from the repo root unless noted, via `npx firebase-tools`.

1. **Install deps and typecheck**
   ```
   cd functions && npm install && npx tsc --noEmit
   ```

2. **Log in** (interactive browser OAuth — must be run by a human, not scripted)
   ```
   npx firebase-tools login
   npx firebase-tools projects:list   # confirm flashcards-8ad6d shows up, marked (current)
   ```

3. **Confirm Blaze plan.** Cloud Functions v2 (and any outbound network call — ElevenLabs, Vertex
   AI) require the pay-as-you-go Blaze plan; Spark will not deploy these.
   ```
   open "https://console.firebase.google.com/project/flashcards-8ad6d/usage/details"
   ```

4. **Enable the Vertex AI API** (needed for the Gemini grading call — one-time per project):
   ```
   open "https://console.cloud.google.com/apis/library/aiplatform.googleapis.com?project=flashcards-8ad6d"
   ```
   Click Enable. The function's own runtime service account
   (`<project-number>-compute@developer.gserviceaccount.com`, the GCP default compute SA — visible
   in the deploy output) needs `roles/aiplatform.user` to actually call Gemini. If a future deploy
   gets a Vertex AI `PERMISSION_DENIED`, that role is what to check/add first.

5. **Set the ElevenLabs API key as a secret** (never in the app, never committed — Secret Manager only):
   ```
   npx firebase-tools functions:secrets:set ELEVENLABS_API_KEY
   ```
   `src/index.ts` declares it via `defineSecret("ELEVENLABS_API_KEY")` and binds it only to
   `transcribeAndGradeSpokenAnswer` (the only function that calls ElevenLabs).

6. **Build and deploy.** Renaming/removing a deployed function is a delete + create (Firebase has no
   in-place rename), so explicitly delete the retired functions from the previous shape first:
   ```
   npx firebase-tools functions:delete gradeVoiceAnswer transcribe sanitizeAndGrade --force
   cd functions && npm run build && cd .. && npx firebase-tools deploy --only functions
   npx firebase-tools functions:list   # expect only: entitlement, transcribeAndGradeSpokenAnswer
   ```
   (On a truly fresh project the `functions:delete` is a harmless no-op.)

7. **Set the container-image cleanup policy** (one-time; otherwise old Cloud Build images accumulate
   storage cost forever):
   ```
   npx firebase-tools functions:artifacts:setpolicy
   ```

8. **Firestore rules.** `firestore.rules` at the repo root is deployed via:
   ```
   npx firebase-tools deploy --only firestore:rules
   ```
   **Read `docs/adr/0024`'s Consequences section before touching this file.** Any new collection
   needs its own explicit `match` block or it default-denies. Confirm real Firestore paths by
   grepping `core/data/.../source/*.kt` for `.collection(...)` before writing a rule.

9. **Entitlement doc for a test account.** Play Billing → Firestore sync is deferred; until it
   exists, populate the doc by hand. Find the UID in
   `https://console.firebase.google.com/project/flashcards-8ad6d/authentication/users`, then create
   `users/{uid}/entitlement/premium` with field `isPremium: true` (boolean) via the console, or:
   ```js
   node -e '
   const admin = require("./functions/node_modules/firebase-admin");
   admin.initializeApp({ credential: admin.credential.cert(require("./firebase-service-account.json")) });
   const uid = "PASTE_UID_HERE";
   admin.firestore().collection("users").doc(uid).collection("entitlement").doc("premium")
     .set({ isPremium: true })
     .then(() => process.exit(0));
   '
   ```
   **Credential handling.** Prefer Application Default Credentials
   (`gcloud auth application-default login`, then drop the `credential:` arg) over a downloaded
   `firebase-service-account.json`. If you must use a key file, keep it **outside the repo** or
   ensure it is gitignored, never commit it, and delete/rotate it once the manual entitlement edit
   is done — it grants full Admin SDK access to the project.

10. **Point the app at the backend.** Nothing to configure — the app resolves the backend from
    `google-services.json` (already load-bearing for Auth + Firestore). Confirm it points at
    `flashcards-8ad6d` (project number 1044553396320). Rebuild (`./gradlew assembleDebug`), open the
    debug-only 🛠️ Voice Debug tab, and test in order: entitlement (no third-party cost) →
    transcribe + sanitize → a real study-session voice answer for the full streaming path.

### `submitBugReport`

The only writer of the top-level `bugReports` collection; security rules deny every client access
(ADR-0058). `index.ts` only delegates to `src/lib/submitBugReport.ts`, which runs the revoked-token
guard, validates the request, allows at most 10 reports per uid in a rolling 24 hours
(`resource-exhausted` past that), then adds the report with the server-set `uid`, `createdAt` and
`status: "new"`. Returns nothing on success. The rate limit's `count()` query needs the
`bugReports` (`uid`, `createdAt`) composite index in `../firestore.indexes.json`. That file also lists the
`subcategories` (`categoryId`, `order`) index already live in production, so it mirrors every deployed
composite index and an indexes deploy never finds one it doesn't know.

## Local iteration

- `npm run build:watch` inside `functions/` for a standing `tsc --watch`.
- `entitlement` and `transcribeAndGradeSpokenAnswer` have no local emulator setup (deferred,
  ADR-0029) — every test against them goes through the real deployed callables via the debug screen.
  The in-app fake/real toggles are gone — the fake now only exists as a unit-test double
  (`core/data/src/test`).
- `submitStudySession` and `submitBugReport` have real local test suites: `npm test`
  starts the Firestore and Auth emulators (`firebase emulators:exec`, reusing `../firebase.json`)
  and runs every `src/lib/*.test.ts` file against them with Node's built-in test runner. Tests call
  the lib functions directly rather than going through a running Functions emulator or an `onCall`
  HTTP round trip. The revoked-token guard reads real user records from the Auth emulator, but the
  tests build the decoded-token request `onCall` would pass, so no token minting is needed.
  `npm run test:unit` runs the same suite against whatever emulators are already listening on ports
  8080 (Firestore) and 9099 (Auth), handy for repeated runs while iterating, instead of paying
  emulator startup cost every time.

## Redeploying after a code change

```
cd functions && npm run build && cd .. && npx firebase-tools deploy --only functions
```
Firebase only actually updates functions whose source changed. Single function:
`--only functions:transcribeAndGradeSpokenAnswer`.

## Secrets and cost hygiene

- `ELEVENLABS_API_KEY` lives only in Secret Manager, bound only to `transcribeAndGradeSpokenAnswer`.
  Rotate via `functions:secrets:set` again (creates a new version; prune old versions manually).
- Set a per-key credit/usage cap on the ElevenLabs dashboard as a second line of defense.
- Gemini via Vertex AI bills to the project's normal billing — no separate cap beyond the
  entitlement gate and whatever budget alerts you set on the project.
- **Neither of these is a hard spend cap.** `isPremium` only gates *access* — a premium account can
  still call the grading function without bound — and GCP budget alerts only *notify*, they never
  stop billing or halt traffic. Before opening this to real users, add per-user request quotas / rate
  limiting (and, ideally, an ElevenLabs hard credit cap) as the actual abuse-and-spend controls.
