import * as admin from "firebase-admin";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import { isPremiumUser } from "./lib/entitlement";
import { transcribeWithElevenLabsScribe } from "./lib/elevenlabs";
import { sanitizeTranscript, gradeSanitizedTranscript } from "./lib/grading";
import { RequestDeadline } from "./lib/requestDeadline";
import { SubmitStudySessionResult, handleSubmitStudySessionCall } from "./lib/submitStudySession";

admin.initializeApp();

const elevenLabsApiKey = defineSecret("ELEVENLABS_API_KEY");

const RUNTIME_OPTIONS = {
  region: "us-central1",
  timeoutSeconds: 120,
  memory: "512MiB" as const,
};

// Guard against oversized uploads before allocating a decode buffer or paying for downstream STT.
// A base64 string is ~4/3 the byte size it decodes to, so this caps decoded audio at ~10 MiB —
// comfortably above any legitimate short spoken answer.
const MAX_AUDIO_BASE64_LENGTH = 14_000_000;

// The client gives the whole voice-grading call 20 s. Each step has its own limit, and the steps
// together stop at 19 s, so a slow service ends in an error the client reads as a service failure
// just before the client's own timeout would call it a lost connection. A cold start is outside
// this budget but inside the client's.
const TRANSCRIPTION_TIMEOUT_MS = 15_000;
const LLM_CALL_TIMEOUT_MS = 15_000;
const VOICE_GRADING_DEADLINE_MS = 19_000;
const VOICE_GRADING_TIMEOUT_SECONDS = 20;

interface EntitlementResult {
  is_premium: boolean;
}

/**
 * Server-authoritative premium check (ADR-0029 §1). `onCall` verifies the Firebase ID token
 * automatically (`request.auth`); no manual bearer parsing. Sole caller today is the debug
 * screen, kept as the seam a future proactive paywall gate hangs off (ADR-0029 §6).
 */
export const entitlement = onCall<void, Promise<EntitlementResult>>(
  RUNTIME_OPTIONS,
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Missing Firebase ID token");
    return { is_premium: await isPremiumUser(uid) };
  },
);

interface TranscribeAndGradeRequest {
  card_id?: string;
  question?: string;
  expected_answer?: string;
  audio_base64?: string;
}

interface TranscribeAndGradeChunk {
  sanitized_transcript: string;
}

/**
 * Terminal result. Full mode (question + expected_answer present) returns `{ grade, feedback }`;
 * debug mode (both absent) returns an empty object — the grade LLM never runs because there is
 * structurally nothing to grade (ADR-0029 §3).
 */
interface TranscribeAndGradeResult {
  grade?: number;
  feedback?: string;
}

/**
 * Streaming callable (ADR-0028 + ADR-0029): one client request, two ordered results over the same
 * connection — `response.sendChunk({ sanitized_transcript })` as soon as STT + sanitize finish,
 * then the terminal `Result`. The mode is inferred from the payload, not a flag (ADR-0029 §3):
 *   - `audio_base64` is always required.
 *   - `question` + `expected_answer` are both-or-neither; exactly one present → `invalid-argument`.
 *   - Full mode (both present): terminal `{ grade, feedback }`.
 *   - Debug mode (both absent): terminal `{}`; grade LLM skipped.
 * Must be `onCall`, not `onRequest` — that's what gives streaming (`request.acceptsStreaming`,
 * `response.sendChunk`) and automatic Firebase Auth ID-token verification (`request.auth`).
 */
export const transcribeAndGradeSpokenAnswer = onCall<
  TranscribeAndGradeRequest,
  Promise<TranscribeAndGradeResult>,
  TranscribeAndGradeChunk
>(
  { ...RUNTIME_OPTIONS, timeoutSeconds: VOICE_GRADING_TIMEOUT_SECONDS, secrets: [elevenLabsApiKey] },
  async (request, response) => {
    const deadline = new RequestDeadline(VOICE_GRADING_DEADLINE_MS);
    const uid = request.auth?.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Missing Firebase ID token");
    if (!(await isPremiumUser(uid))) {
      throw new HttpsError("permission-denied", "No active premium entitlement");
    }

    const { question, expected_answer: expectedAnswer, audio_base64: audioBase64 } = request.data;
    if (typeof audioBase64 !== "string" || audioBase64.length === 0) {
      throw new HttpsError("invalid-argument", "Missing or invalid audio_base64");
    }
    if (audioBase64.length > MAX_AUDIO_BASE64_LENGTH) {
      throw new HttpsError("invalid-argument", "audio_base64 exceeds the maximum allowed size");
    }
    // Mode is inferred from field presence, not truthiness: a field sent as an empty (or
    // non-string) value is a malformed request, not a signal to skip grading (ADR-0029 §3).
    const hasQuestion = question !== undefined;
    const hasExpectedAnswer = expectedAnswer !== undefined;
    if (hasQuestion !== hasExpectedAnswer) {
      throw new HttpsError(
        "invalid-argument",
        "question and expected_answer are both-or-neither",
      );
    }
    if (hasQuestion) {
      if (typeof question !== "string" || question.trim().length === 0) {
        throw new HttpsError("invalid-argument", "question must be a non-empty string");
      }
      if (typeof expectedAnswer !== "string" || expectedAnswer.trim().length === 0) {
        throw new HttpsError("invalid-argument", "expected_answer must be a non-empty string");
      }
    }

    const wavBuffer = Buffer.from(audioBase64, "base64");
    if (wavBuffer.length === 0) {
      throw new HttpsError("invalid-argument", "audio_base64 did not decode to any audio");
    }
    const rawTranscript = await transcribeWithElevenLabsScribe(
      wavBuffer,
      elevenLabsApiKey.value(),
      deadline.stepTimeoutMs(TRANSCRIPTION_TIMEOUT_MS),
    );
    const sanitizedTranscript = await sanitizeTranscript(
      rawTranscript,
      deadline.stepTimeoutMs(LLM_CALL_TIMEOUT_MS),
    );
    await response?.sendChunk({ sanitized_transcript: sanitizedTranscript });

    if (!hasQuestion) {
      return {};
    }
    const grade = await gradeSanitizedTranscript(
      question!,
      expectedAnswer!,
      sanitizedTranscript,
      deadline.stepTimeoutMs(LLM_CALL_TIMEOUT_MS),
    );
    return { grade: grade.gradePercent, feedback: grade.feedback };
  },
);

/**
 * Server-authoritative session commit. Replaces the client-side write path
 * (`CommitStudySessionUseCase` / `StudySessionRemoteDataSource`): the client submits what happened
 * during a session, and this function is the sole place that computes and writes its XP, level and
 * progress. Named "submit", not "report" — this codebase's curation feature already owns "report" for
 * a flagged-content signal, so a finished session is submitted, never reported. The wrapper stays thin:
 * the revoked-token guard, payload validation, transaction and scoring logic live in
 * `lib/authGuard.ts`, `lib/submitStudySession.ts` and `lib/xpScoring.ts`, where they can be tested
 * directly without going through this `onCall` wrapper.
 */
export const submitStudySession = onCall<unknown, Promise<SubmitStudySessionResult>>(RUNTIME_OPTIONS, (request) =>
  handleSubmitStudySessionCall(admin.auth(), request),
);
