# Voice grading backend runs on Cloud Functions, grading LLM is Gemini via Vertex AI

> **Superseded in part by [ADR-0028](0028-voice-answering-not-background-only-streamed-transcript-then-grade.md)** (production grade path becomes a streaming `onCall` callable) **and [ADR-0029](0029-voice-grading-two-callables-payload-inferred-debug-mode.md)** (collapses to two `onCall` functions; `transcribe`/`sanitizeAndGrade` deleted; `entitlement` moves to `onCall`). The original four-`onRequest`-function shape, the URL-scheme rationale for it, and the four-cold-start-units consequence are all dead — see 0028/0029 for the current transport. What stands: the choice of Cloud Functions as the backend platform, and Gemini via Vertex AI as the grading LLM.

## Decision

The premium voice-grading backend (design doc: `docs/design/voice-answering.md`) runs on Firebase Cloud Functions (2nd gen, Node.js 20, TypeScript), deployed from `functions/` at the repo root — not a separate server.

Grading LLM is Gemini via Vertex AI, not Claude/OpenAI/OpenRouter (all three were live options per the design doc's "Open decisions"). Vertex AI runs in the same GCP project as Firestore/Cloud Functions already — no separate API key or vendor account, auth is the function's own runtime service account plus `roles/aiplatform.user`.

Premium entitlement is a real per-request Firestore check (`users/{uid}/entitlement/premium`, `isPremium: boolean`), not a stub that always passes — but the document itself is populated by hand (console/Admin SDK) rather than by a Play Billing sync, since that sync (RTDN → Firestore) is explicitly deferred to a separate design pass. The enforcement mechanism is real; the population mechanism is manual until that follow-up lands.

## Context

Calling ElevenLabs Scribe and a grading LLM directly from the Android app was already rejected in the design doc — any key shipped in the APK is extractable by decompiling it. A backend is mandatory purely to hold those two secrets server-side. Given the app already runs on Firebase/Firestore, a Cloud Function was chosen over standing up a separate server (Cloud Run, a VM) to avoid new infrastructure to operate.

## Alternatives considered

**Cloud Run instead of Cloud Functions** — rejected in the design doc already (stateless request/response fits Cloud Functions; no need for the long-lived-connection story Cloud Run would justify).

**Claude or OpenAI for grading** — rejected for this iteration. Neither has any infrastructure synergy with the existing Firebase/GCP project; both would need an independent API key/account for no offsetting benefit over Gemini. Left open as a later swap — `lib/grading.ts` is the only file that would change.

**OpenRouter for multi-model grading A/B testing** — rejected for now, not because it's a bad idea (design doc flags it as a live option) but because standing up a comparative-grading harness is a separate scope from getting one real vendor working end-to-end first.

**Always-true entitlement stub** (skip the Firestore check entirely until Play Billing sync exists) — rejected. Would violate the design doc's core invariant ("Entitlement enforcement": server-side check before any paid API call, never trust the client). A manually-populated Firestore doc keeps the real check in place; only the population mechanism is a placeholder.

## Consequences

- Swapping the grading vendor later (Claude/OpenAI/OpenRouter) touches only `functions/src/lib/grading.ts`; the JSON contract and every caller of it stay unchanged.
- Swapping entitlement from manual-doc to real Play Billing/RTDN sync later touches only what *writes* `users/{uid}/entitlement/premium` (a new Cloud Function trigger) — what *reads* it and the Android app do not change.
- Deploying `firestore.rules` alongside this feature closed a pre-existing gap: the ruleset previously in production was a single blanket `match /{document=**} { allow read, write: if request.auth != null }`, which let any signed-in user read/write any other user's documents — including `entitlement` itself. The rules now deployed from this repo (`firestore.rules`, tracked going forward instead of console-only) scope every `users/{uid}/**` path to its own owner and grant the `entitlement` subcollection no client rule at all (default-deny; only the Cloud Function's Admin SDK, which bypasses rules, may touch it).
