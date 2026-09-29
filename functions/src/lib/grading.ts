import { VertexAI, SchemaType } from "@google-cloud/vertexai";
import { HttpError } from "./httpError";

const VERTEX_LOCATION = "us-central1";
const GEMINI_MODEL = "gemini-2.5-flash";

export interface GradeResult {
  gradePercent: number;
  feedback: string;
}

interface SanitizeJson {
  sanitized_transcript: string;
}

interface GradeJson {
  grade: number;
  feedback: string;
}

const SANITIZE_RESPONSE_SCHEMA = {
  type: SchemaType.OBJECT,
  properties: {
    sanitized_transcript: { type: SchemaType.STRING },
  },
  required: ["sanitized_transcript"],
};

const GRADE_RESPONSE_SCHEMA = {
  type: SchemaType.OBJECT,
  properties: {
    grade: { type: SchemaType.INTEGER },
    feedback: { type: SchemaType.STRING },
  },
  required: ["grade", "feedback"],
};

function buildSanitizePrompt(rawTranscript: string): string {
  return `You clean up a raw speech-to-text transcript of a spoken flashcard answer for a study app.

Raw spoken transcript (may contain filler words, disfluencies, and incidentally spoken personal
information): ${rawTranscript}

Return a single JSON object described by the response schema with one field,
"sanitized_transcript": the transcript with filler/disfluencies (um, uh, repeated words)
normalized, and any personal information the speaker incidentally said (names, emails, phone
numbers, addresses) removed/redacted. Keep the substantive answer content intact.`;
}

function buildGradePrompt(question: string, expectedAnswer: string, sanitizedTranscript: string): string {
  return `You grade a spoken flashcard answer for a study app.

Flashcard question: ${question}
Expected answer: ${expectedAnswer}
Spoken answer (already sanitized): ${sanitizedTranscript}

Return a single JSON object described by the response schema with two fields:
1. "grade": an integer 0-100 for how completely and correctly the spoken answer answers the
   question relative to the expected answer. Give partial credit for partially correct or
   incomplete answers; do not default to round numbers just because they look tidy.
2. "feedback": one short spoken-aloud sentence, tone depends on the grade:
   - grade < 40 (failed) or grade 40-79 (partial): state briefly what was missed, then include
     the full expected answer verbatim so the user hears the correct answer.
   - grade >= 80 (correct): a short affirmation only — do not repeat the expected answer.`;
}

let vertexAi: VertexAI | null = null;

function getVertexAi(): VertexAI {
  if (!vertexAi) {
    vertexAi = new VertexAI({
      project: process.env.GCLOUD_PROJECT,
      location: VERTEX_LOCATION,
    });
  }
  return vertexAi;
}

async function generateJson<T>(prompt: string, schema: object, timeoutMs: number): Promise<T> {
  const model = getVertexAi().getGenerativeModel(
    {
      model: GEMINI_MODEL,
      generationConfig: {
        responseMimeType: "application/json",
        responseSchema: schema,
      },
    },
    { timeout: timeoutMs },
  );

  const result = await model.generateContent(prompt);
  const text = result.response.candidates?.[0]?.content?.parts?.[0]?.text;
  if (!text) {
    throw new HttpError(502, "Grading LLM returned no content");
  }

  try {
    return JSON.parse(text) as T;
  } catch {
    throw new HttpError(502, "Grading LLM returned malformed JSON");
  }
}

/**
 * Phase 1 LLM call (ADR-0028 decision 3): PII-strip + normalize filler/disfluencies only, no
 * grading judgment yet. Must complete before the client sees any transcript, since the sanitized
 * (not raw) transcript is what gets displayed on screen.
 */
export async function sanitizeTranscript(rawTranscript: string, timeoutMs: number): Promise<string> {
  const parsed = await generateJson<SanitizeJson>(
    buildSanitizePrompt(rawTranscript),
    SANITIZE_RESPONSE_SCHEMA,
    timeoutMs,
  );
  if (typeof parsed.sanitized_transcript !== "string" || parsed.sanitized_transcript.length === 0) {
    throw new HttpError(502, "Sanitize LLM returned an empty transcript");
  }
  return parsed.sanitized_transcript;
}

/**
 * Phase 2 LLM call (ADR-0028): grades the already-sanitized transcript. Runs after
 * `response.sendChunk({ sanitizedTranscript })` has already gone out to the client, and its
 * result is the function's final return value delivered as the stream's `StreamResponse.Result`.
 */
export async function gradeSanitizedTranscript(
  question: string,
  expectedAnswer: string,
  sanitizedTranscript: string,
  timeoutMs: number,
): Promise<GradeResult> {
  const parsed = await generateJson<GradeJson>(
    buildGradePrompt(question, expectedAnswer, sanitizedTranscript),
    GRADE_RESPONSE_SCHEMA,
    timeoutMs,
  );
  if (typeof parsed.grade !== "number" || !Number.isFinite(parsed.grade)) {
    throw new HttpError(502, "Grading LLM returned an invalid grade");
  }
  if (typeof parsed.feedback !== "string" || parsed.feedback.length === 0) {
    throw new HttpError(502, "Grading LLM returned empty feedback");
  }
  return {
    gradePercent: Math.max(0, Math.min(100, Math.round(parsed.grade))),
    feedback: parsed.feedback,
  };
}
