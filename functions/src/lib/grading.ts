import { VertexAI, SchemaType } from "@google-cloud/vertexai";
import * as logger from "firebase-functions/logger";
import { HttpError } from "./httpError";

const VERTEX_LOCATION = "us-central1";
const GEMINI_MODEL = "gemini-2.5-flash";

/** Lowest Partial grade percent; mirrors the app's `VoiceAnswerGrade` bands, so change both. */
export const PARTIAL_GRADE_THRESHOLD = 30;
/** Lowest Correct grade percent; mirrors the app's `VoiceAnswerGrade` bands, so change both. */
export const CORRECT_GRADE_THRESHOLD = 75;

export interface GradeResult {
  gradePercent: number;
  /**
   * Shown on screen; may hold up to 2 inline code spans. Empty when the answer is correct, or
   * when the model gave no feedback at all (logged).
   */
  feedback: string;
  /** The same feedback worded for text-to-speech, with no markup. Empty exactly when `feedback` is. */
  spokenFeedback: string;
}

interface SanitizeJson {
  sanitized_transcript: string;
}

export interface GradeJson {
  grade: number;
  feedback: string;
  spoken_feedback: string;
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
    spoken_feedback: { type: SchemaType.STRING },
  },
  required: ["grade", "feedback", "spoken_feedback"],
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

function buildGradePrompt(
  question: string,
  expectedAnswer: string,
  spokenAnswer: string | undefined,
  sanitizedTranscript: string,
): string {
  const spokenAnswerLine = spokenAnswer === undefined
    ? ""
    : `\nSpoken form of the expected answer: ${spokenAnswer}`;
  return `You grade a spoken flashcard answer for a study app. The user hears your feedback read aloud
and reads it in a small panel, so keep it as short as it can be while still useful.

Flashcard question: ${question}
Expected answer: ${expectedAnswer}${spokenAnswerLine}
Spoken answer (already sanitized): ${sanitizedTranscript}

Return a single JSON object described by the response schema with three fields:

1. "grade": an integer 0-100 for how completely and correctly the spoken answer answers the
   question, judged against the expected answer. Judge the ideas, not the wording: accept
   paraphrases, synonyms and a different order, and do not penalize a small missing detail
   heavily. Give partial credit for partially correct or incomplete answers; do not default to
   round numbers just because they look tidy. Bands: below ${PARTIAL_GRADE_THRESHOLD} is failed,
   ${PARTIAL_GRADE_THRESHOLD}-${CORRECT_GRADE_THRESHOLD - 1} is partial, ${CORRECT_GRADE_THRESHOLD} and above is correct.

2. "feedback": shown on screen. Never repeat or paraphrase the whole expected answer, never
   restate what the user already got right, and never open with "The correct answer is" or
   similar. Use exactly one of these cases:
   - Correct, nothing meaningful missed: an empty string.
   - Correct, a minor point missed: name only that point, at most 8 words.
   - Partial: name only what was missing or wrong, at most 25 words.
   - Failed, with a real but wrong attempt: say what was wrong, then the key correct idea in one
     short clause, at most 25 words.
   - Non-answer (the user says they don't know, gives up, or says something unrelated to the
     question): grade 0, and give the essence of the expected answer, key points only, at most 35
     words.
   Format: plain sentences on one line. No markdown or line breaks. Never use underscores for
   emphasis. Wrap code in single backticks only when words would be less precise, at most 2
   spans, each an identifier, keyword, type or a single-line expression of about 30 characters or
   fewer, e.g. \`suspend\` or \`fun empty() = null\`. Never write code blocks.

3. "spoken_feedback": the same content as "feedback", worded to be read aloud by text-to-speech.
   No backticks and no markdown. Say code in words (e.g. "a function returning null" instead of
   its syntax); bare names like suspend or Flow may stay. When a spoken form of the expected
   answer is given, reuse its wording for any part of the answer you include. Empty exactly when
   "feedback" is empty.`;
}

const FENCED_BLOCK = /```[\s\S]*?(```|$)/g;
const LIST_MARKER = /^\s*(?:[-*\u2022]|\d+[.)])\s+/gm;
const HEADING_MARKER = /^\s*#+\s+/gm;
const QUOTE_MARKER = /^\s*>\s?/gm;
// A run of 1-3 asterisks hugging its text on both sides, so `a * b` keeps its operator.
const EMPHASIS = /(\*{1,3})(\S(?:[^*]*?\S)?)\1/g;
const STRIKETHROUGH = /~~/g;
const LINK = /\[([^\]]+)\]\([^)]*\)/g;
const WHITESPACE_RUN = /\s+/g;

/** Drops fenced code and line-start markdown (lists, headings, quotes). */
function flattenMarkdown(text: string): string {
  return text
    .replace(FENCED_BLOCK, " ")
    .replace(LIST_MARKER, "")
    .replace(HEADING_MARKER, "")
    .replace(QUOTE_MARKER, "");
}

/** Unwraps emphasis, strikethrough and links in prose, leaving symbols like `C#` or `a * b`. */
function stripInlineMarks(prose: string): string {
  return prose.replace(EMPHASIS, "$2").replace(STRIKETHROUGH, "").replace(LINK, "$1");
}

function collapseWhitespace(text: string): string {
  return text.replace(WHITESPACE_RUN, " ").trim();
}

/**
 * Cleans the on-screen feedback: inline code spans stay as written, other markdown goes. With an
 * odd number of backticks the spans can't be told apart, so every backtick goes too.
 */
export function cleanDisplayFeedback(feedback: string): string {
  const segments = flattenMarkdown(feedback).split("`");
  if (segments.length % 2 === 0) {
    return cleanSpokenFeedback(feedback);
  }
  // Even segments are prose, odd ones are code spans.
  const cleaned = segments
    .map((segment, index) => (index % 2 === 0 ? stripInlineMarks(segment) : segment))
    .join("`");
  return collapseWhitespace(cleaned);
}

/** Cleans the feedback text-to-speech reads: no backticks and no markdown. */
export function cleanSpokenFeedback(spokenFeedback: string): string {
  return collapseWhitespace(stripInlineMarks(flattenMarkdown(spokenFeedback).replace(/`/g, "")));
}

/**
 * Checks and cleans the grade model's reply. Missing display feedback falls back to the spoken
 * text, and missing spoken feedback to the display text without its backticks.
 */
export function normalizeGrade(parsed: GradeJson): GradeResult {
  if (typeof parsed.grade !== "number" || !Number.isFinite(parsed.grade)) {
    throw new HttpError(502, "Grading LLM returned an invalid grade");
  }
  if (typeof parsed.feedback !== "string" || typeof parsed.spoken_feedback !== "string") {
    throw new HttpError(502, "Grading LLM returned no feedback");
  }
  const gradePercent = Math.max(0, Math.min(100, Math.round(parsed.grade)));
  const spokenFeedback = cleanSpokenFeedback(parsed.spoken_feedback);
  const feedback = cleanDisplayFeedback(parsed.feedback) || spokenFeedback;
  if (feedback.length === 0) {
    if (gradePercent < CORRECT_GRADE_THRESHOLD) {
      logger.warn("Grading LLM returned no feedback for an answer that was not correct", { gradePercent });
    }
    return { gradePercent, feedback: "", spokenFeedback: "" };
  }
  return { gradePercent, feedback, spokenFeedback: spokenFeedback || cleanSpokenFeedback(feedback) };
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
  spokenAnswer: string | undefined,
  sanitizedTranscript: string,
  timeoutMs: number,
): Promise<GradeResult> {
  const parsed = await generateJson<GradeJson>(
    buildGradePrompt(question, expectedAnswer, spokenAnswer, sanitizedTranscript),
    GRADE_RESPONSE_SCHEMA,
    timeoutMs,
  );
  return normalizeGrade(parsed);
}
