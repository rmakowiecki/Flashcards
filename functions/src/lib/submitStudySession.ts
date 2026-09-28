import * as admin from "firebase-admin";
import * as logger from "firebase-functions/logger";
import { HttpsError } from "firebase-functions/v2/https";
import { loadXpConfig } from "./xpConfig";
import {
  DEFAULT_SCORING_STATE,
  ScoringState,
  StreakAndGoalInput,
  XpBreakdown,
  computeSessionXp,
  levelThreshold,
} from "./xpScoring";

/**
 * The sole writer of a session's XP, level and progress. Everything this function decides
 * lands in one Firestore transaction, keyed for idempotency on the client-generated `sessionId`: a
 * retry of an already-processed session is a no-op that returns the same result again, never a
 * second award. See `submitStudySession`'s own doc comment for the transaction's shape.
 *
 * Deliberately kept out of `index.ts`: `validateSubmitStudySessionRequest` and `submitStudySession`
 * are plain, directly-testable functions (mirroring `lib/entitlement.ts`'s `isPremiumUser`), so a
 * test can invoke them without going through the `onCall` wrapper or a running Functions emulator —
 * only a real Firestore (emulator or production) is ever needed to exercise this file.
 */

type StudyMode = "Rated" | "Fast";
type CardState = "Mastered" | "Partial" | "Failed" | "Seen";

// Firestore collection/document segments — every one named, per this repo's Firestore-constants
// convention (AGENTS.md's "Data Layer Standards", already followed in TS by `entitlement.ts`).
// Collection/document names mirror the four Kotlin remote-data-source classes this function
// replaces: StudySessionRemoteDataSource, CardProgressRemoteDataSource, ProgressSummaryRemoteDataSource,
// ScoringStateRemoteDataSource.
const USERS_COLLECTION = "users";
const SESSIONS_COLLECTION = "sessions";
const PROGRESS_COLLECTION = "progress";
const PROGRESS_DETAILS_DOCUMENT = "details";
const PROGRESS_SUBCATEGORIES_COLLECTION = "subcategories";
const PROGRESS_SUMMARY_DOCUMENT = "summary";
const PROGRESS_USER_STATS_DOCUMENT = "user-stats";

// `sessions/{sessionId}` document fields — mirrors StudySessionRemoteDataSource.kt's own FIELD_* names.
const FIELD_SESSION_ID = "sessionId";
const FIELD_START_TIMESTAMP = "startTimestamp";
const FIELD_DURATION_SECONDS = "durationSeconds";
const FIELD_STUDY_MODE = "studyMode";
const FIELD_IS_ABANDONED = "isAbandoned";
const FIELD_CATEGORY_ID = "categoryId";
const FIELD_CATEGORY_NAME = "categoryName";
const FIELD_SUBCATEGORY_IDS = "subcategoryIds";
const FIELD_SUBCATEGORY_NAMES = "subcategoryNames";
const FIELD_CARD_COUNT = "cardCount";
const FIELD_NEW_CARDS_STUDIED = "newCardsStudied";
const FIELD_CARD_RESULTS = "cardResults";
const FIELD_CARDS_MASTERED = "cardsMastered";
const FIELD_CARDS_PARTIAL = "cardsPartial";
const FIELD_CARDS_DEFENDED = "cardsDefended";
const FIELD_CARDS_DEMASTERED = "cardsDemastered";
const FIELD_CARD_SUBCATEGORY_ID = "subcategoryId";
const FIELD_ATTEMPTS_USED = "attemptsUsed";
const FIELD_WAS_PREVIOUSLY_MASTERED = "wasPreviouslyMastered";

// A `sessions/{sessionId}` document field only, no longer a trusted request-body field: the
// "today's total minutes" query below filters the session collection on this same name, but the
// value written here is always server-derived — see `deriveLocalStudyDate`.
const FIELD_STUDY_DATE = "studyDate";

// Additive fields written to the session document, beyond what StudySessionRemoteDataSource.kt
// ever wrote — needed so a retried (idempotent) call can answer from the session document alone,
// without depending on any other document's current, possibly-since-moved-on state.
const FIELD_LEVELS_CROSSED = "levelsCrossed";
const FIELD_LEVEL_AFTER = "levelAfter";
const FIELD_XP_INTO_CURRENT_LEVEL_AFTER = "xpIntoCurrentLevelAfter";
const FIELD_XP_FOR_NEXT_LEVEL_AFTER = "xpForNextLevelAfter";

// Shared between a card's stored result fields and a card's stored progress fields.
const FIELD_STATE = "state";

// `progress/details/subcategories/{id}` document fields — mirrors CardProgressRemoteDataSource.kt.
const FIELD_CARDS = "cards";
const FIELD_FIRST_STUDIED_AT = "firstStudiedAt";
const FIELD_MASTERED_AT = "masteredAt";

// `progress/summary` document fields — mirrors ProgressSummaryRemoteDataSource.kt.
const FIELD_SUBCATEGORIES = "subcategories";
const FIELD_MASTERED_COUNT = "masteredCount";
const FIELD_STUDIED_COUNT = "studiedCount";

// `progress/user-stats` document fields — mirrors ScoringStateRemoteDataSource.kt.
const FIELD_XP = "xp";
const FIELD_LEVEL = "level";
const FIELD_XP_INTO_CURRENT_LEVEL = "xpIntoCurrentLevel";
const FIELD_CURRENT_STREAK = "currentStreak";
const FIELD_BEST_STREAK = "bestStreak";
const FIELD_LAST_STUDY_DATE = "lastStudyDate";
const FIELD_GOAL_MET_DATE = "goalMetDate";

// The itemised XP breakdown's own fields — mirrors StudySessionRemoteDataSource.kt's FIELD_XP_* names.
const FIELD_XP_NEW_CARDS = "newCards";
const FIELD_XP_MASTERED = "mastered";
const FIELD_XP_PARTIAL = "partial";
const FIELD_XP_MASTERY_DEFENSE_BONUS = "masteryDefenseBonus";
const FIELD_XP_DEMASTERED = "demastered";
const FIELD_XP_TIME_STUDIED = "timeStudied";
const FIELD_XP_SESSION_COMPLETION_BONUS = "sessionCompletionBonus";
const FIELD_XP_DAILY_GOAL_BONUS = "dailyGoalBonus";
const FIELD_XP_STREAK_BONUS = "streakBonus";
const FIELD_XP_TOTAL = "xpTotal";

export interface SubmitStudySessionCardResult {
  cardId: string;
  subcategoryId: string;
  state: CardState;
  attemptsUsed?: number;
  wasPreviouslyMastered?: boolean;
}

export interface ValidatedSubmitStudySessionRequest {
  /**
   * The uid of the User who finished the session, as stored in the client's pending-submission queue.
   * Checked against the caller's auth uid by `requireOwnerMatchesCaller`: the client picks the entry
   * before the SDK attaches an ID token, so a sign-in change in between must never commit one User's
   * session to another.
   */
  ownerUid: string;
  sessionId: string;
  studyMode: StudyMode;
  startedAtEpochMillis: number;
  durationSeconds: number;
  abandoned: boolean;
  categoryId: string;
  categoryName: string;
  subcategoryIds: string[];
  subcategoryNames: string[];
  cardResults: SubmitStudySessionCardResult[];
  /**
   * Minutes east of UTC for the device's timezone offset at `startedAtEpochMillis` — the
   * server derives the session's local calendar day from this and `startedAtEpochMillis` itself
   * (`deriveLocalStudyDate`), rather than trusting a client-supplied date string for streak/Daily-Goal
   * scoring.
   */
  studyDateUtcOffsetMinutes: number;
  /** the Daily Goal (minutes/day) in effect when this session ended — never persisted, see ADR-0048. */
  dailyGoalMinutes: number;
}

export interface SubmitStudySessionResult {
  breakdown: XpBreakdown;
  level: number;
  xpIntoCurrentLevel: number;
  xpForNextLevel: number;
  levelsCrossed: number[];
}

function fail(message: string): never {
  throw new HttpsError("invalid-argument", message);
}

function requireNonEmptyString(value: unknown, field: string): string {
  if (typeof value !== "string" || value.length === 0) fail(`${field} must be a non-empty string`);
  return value as string;
}

// Firestore's own reserved-name rule for both document ids and nested map-field-path segments:
// a value matching `__.*__` can never be written (Firestore itself throws INVALID_ARGUMENT). Any id
// this function later uses as a document id (`subcategoryId`) or a nested map key inside a `set`
// (`cardId`) must be rejected here first, with a clear `invalid-argument`, rather than surfacing as
// an opaque internal Firestore error from deep inside the transaction.
const RESERVED_FIRESTORE_NAME = /^__.*__$/;

// Mirrors StudySessionConfig.MAX_LENGTH (core/domain, client-enforced) — a session's card count is
// already capped on the client; enforced again here so a crafted payload can't inflate this
// transaction's reads or push a session document toward Firestore's 1 MiB limit.
const MAX_CARD_RESULTS = 50;

const SECONDS_PER_MINUTE = 60;
const MILLIS_PER_MINUTE = 60_000;

// The real-world extremes of a UTC offset (Baker Island UTC-12:00 to Kiritimati/Line Islands
// UTC+14:00) — a request outside this range cannot be a genuine device timezone, whatever
// startedAtEpochMillis claims.
const MAX_UTC_OFFSET_MINUTES = 14 * 60;

/**
 * Derives `startedAtEpochMillis`'s local calendar day (`yyyy-MM-dd`) from the client-reported UTC
 * offset, instead of trusting a client-supplied date string directly (CWE-20): shifting the
 * instant by the offset and reading its UTC-anchored date fields is the standard offset-only idiom for
 * "what date is it on the wall clock at this instant" without a timezone database. A forged offset can
 * only move the derived day by as much as [MAX_UTC_OFFSET_MINUTES] ever allows, never further —
 * unlike an arbitrary date string, which had no relationship to the timestamp at all.
 *
 * **Known residual risk (accepted, not fixed)**: both `startedAtEpochMillis` and `utcOffsetMinutes`
 * are still client-supplied — nothing here attests that a session actually started when the client
 * claims. An authenticated caller could submit forged values across distinct session ids to farm
 * streak/Daily-Goal XP for days not actually studied. This is accepted rather than fixed with a
 * server-recorded session start because the exposure is confined to the submitter's own account
 * stats (no cross-user harm, no financial/security stake) — a server-attested start would need a new
 * session-start endpoint plus offline-start handling, disproportionate to that stake. The
 * [MAX_UTC_OFFSET_MINUTES] bound above remains the only mitigation in place.
 */
function deriveLocalStudyDate(startedAtEpochMillis: number, utcOffsetMinutes: number): string {
  const shifted = new Date(startedAtEpochMillis + utcOffsetMinutes * MILLIS_PER_MINUTE);
  const year = shifted.getUTCFullYear();
  const month = String(shifted.getUTCMonth() + 1).padStart(2, "0");
  const day = String(shifted.getUTCDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function requireFiniteNumberInRange(value: unknown, field: string, minimum: number, maximum: number): number {
  if (typeof value !== "number" || !Number.isFinite(value) || value < minimum || value > maximum) {
    fail(`${field} must be a number between ${minimum} and ${maximum}`);
  }
  return value as number;
}

function requireFirestoreSafeId(value: unknown, field: string): string {
  const id = requireNonEmptyString(value, field);
  if (RESERVED_FIRESTORE_NAME.test(id) || id.includes("/")) fail(`${field} is not a valid Firestore id: "${id}"`);
  return id;
}

function requireStringArray(value: unknown, field: string): string[] {
  if (!Array.isArray(value) || value.length === 0 || !value.every((v) => typeof v === "string" && v.length > 0)) {
    fail(`${field} must be a non-empty array of non-empty strings`);
  }
  return value as string[];
}

function requireFiniteNumber(value: unknown, field: string, minimum = 0): number {
  if (typeof value !== "number" || !Number.isFinite(value) || value < minimum) {
    fail(`${field} must be a number >= ${minimum}`);
  }
  return value as number;
}

function requireBoolean(value: unknown, field: string): boolean {
  if (typeof value !== "boolean") fail(`${field} must be a boolean`);
  return value as boolean;
}

const VALID_STATES: readonly CardState[] = ["Mastered", "Partial", "Failed", "Seen"];

function validateCardResult(raw: unknown, studyMode: StudyMode, index: number): SubmitStudySessionCardResult {
  if (typeof raw !== "object" || raw === null) fail(`cardResults[${index}] must be an object`);
  const entry = raw as Record<string, unknown>;
  const cardId = requireFirestoreSafeId(entry.cardId, `cardResults[${index}].cardId`);
  const subcategoryId = requireFirestoreSafeId(entry.subcategoryId, `cardResults[${index}].subcategoryId`);
  const state = entry.state;
  if (typeof state !== "string" || !VALID_STATES.includes(state as CardState)) {
    fail(`cardResults[${index}].state must be one of ${VALID_STATES.join(", ")}`);
  }

  if (studyMode === "Fast") {
    if (state !== "Seen") fail(`cardResults[${index}].state must be Seen for a Fast session`);
    return { cardId, subcategoryId, state: "Seen" };
  }

  // Rated
  if (state === "Seen") fail(`cardResults[${index}].state can never be Seen for a Rated session`);
  const attemptsUsed = requireFiniteNumber(entry.attemptsUsed, `cardResults[${index}].attemptsUsed`, 1);
  const wasPreviouslyMastered = requireBoolean(entry.wasPreviouslyMastered, `cardResults[${index}].wasPreviouslyMastered`);
  return { cardId, subcategoryId, state: state as CardState, attemptsUsed, wasPreviouslyMastered };
}

/** Rejects a structurally invalid payload before any transaction opens. */
export function validateSubmitStudySessionRequest(data: unknown): ValidatedSubmitStudySessionRequest {
  if (typeof data !== "object" || data === null) fail("request body must be an object");
  const body = data as Record<string, unknown>;

  const ownerUid = requireNonEmptyString(body.ownerUid, "ownerUid");
  const sessionId = requireFirestoreSafeId(body.sessionId, "sessionId");
  const studyMode = body.studyMode;
  if (studyMode !== "Rated" && studyMode !== "Fast") fail("studyMode must be Rated or Fast");
  const startedAtEpochMillis = requireFiniteNumber(body.startedAtEpochMillis, "startedAtEpochMillis");
  const durationSeconds = requireFiniteNumber(body.durationSeconds, "durationSeconds");
  const abandoned = requireBoolean(body.abandoned, "abandoned");
  const categoryId = requireNonEmptyString(body.categoryId, "categoryId");
  const categoryName = requireNonEmptyString(body.categoryName, "categoryName");
  const subcategoryIds = requireStringArray(body.subcategoryIds, "subcategoryIds");
  const subcategoryNames = requireStringArray(body.subcategoryNames, "subcategoryNames");
  if (subcategoryIds.length !== subcategoryNames.length) fail("subcategoryIds and subcategoryNames must be the same length");
  subcategoryIds.forEach((id, index) => requireFirestoreSafeId(id, `subcategoryIds[${index}]`));

  if (!Array.isArray(body.cardResults) || body.cardResults.length === 0) fail("cardResults must be a non-empty array");
  if (body.cardResults.length > MAX_CARD_RESULTS) fail(`cardResults must not exceed ${MAX_CARD_RESULTS} entries`);
  const cardResults = (body.cardResults as unknown[]).map((raw, index) => validateCardResult(raw, studyMode, index));

  const studyDateUtcOffsetMinutes = requireFiniteNumberInRange(
    body.studyDateUtcOffsetMinutes,
    "studyDateUtcOffsetMinutes",
    -MAX_UTC_OFFSET_MINUTES,
    MAX_UTC_OFFSET_MINUTES,
  );
  const dailyGoalMinutes = requireFiniteNumber(body.dailyGoalMinutes, "dailyGoalMinutes", 1);

  const cardIds = new Set<string>();
  for (const entry of cardResults) {
    if (cardIds.has(entry.cardId)) fail(`cardResults contains duplicate cardId "${entry.cardId}"`);
    cardIds.add(entry.cardId);
  }

  const declaredSubcategoryIds = new Set(subcategoryIds);
  for (const entry of cardResults) {
    if (!declaredSubcategoryIds.has(entry.subcategoryId)) {
      fail(`cardResults references subcategoryId "${entry.subcategoryId}" not present in subcategoryIds`);
    }
  }

  return {
    ownerUid,
    sessionId,
    studyMode,
    startedAtEpochMillis,
    durationSeconds,
    abandoned,
    categoryId,
    categoryName,
    subcategoryIds,
    subcategoryNames,
    cardResults,
    studyDateUtcOffsetMinutes,
    dailyGoalMinutes,
  };
}

/**
 * Rejects a session whose queued owner is not the signed-in caller. `unauthenticated`, not a
 * validation code: the session itself is fine, only the token is the wrong User's, so the client keeps
 * it queued and delivers it once its owner signs in again instead of dead-lettering it.
 */
export function requireOwnerMatchesCaller(callerUid: string, request: ValidatedSubmitStudySessionRequest): void {
  if (request.ownerUid !== callerUid) {
    throw new HttpsError("unauthenticated", "Session owner does not match the signed-in user");
  }
}

interface CardProgressUpdateFields {
  state: CardState;
  stampFirstStudied: boolean;
  stampMastered: boolean;
}

/** Mirrors `CommitStudySessionUseCase.resolveMasteredDelta`. Fast's `state` is always `Seen`, hence always `0`. */
function resolveMasteredDelta(entry: SubmitStudySessionCardResult, wasMastered: boolean): number {
  switch (entry.state) {
    case "Mastered":
      return wasMastered ? 0 : 1;
    case "Failed":
      return wasMastered ? -1 : 0;
    default:
      return 0;
  }
}

/** Mirrors `CommitStudySessionUseCase.resolveUpdate`/`resolveFastUpdate`/`resolveRatedUpdate`. `null` means "write nothing". */
function resolveUpdate(
  studyMode: StudyMode,
  entry: SubmitStudySessionCardResult,
  priorExists: boolean,
  wasMastered: boolean,
): CardProgressUpdateFields | null {
  if (studyMode === "Fast") {
    return priorExists ? null : { state: "Seen", stampFirstStudied: true, stampMastered: false };
  }
  if (!priorExists) {
    return { state: entry.state, stampFirstStudied: true, stampMastered: entry.state === "Mastered" };
  }
  switch (entry.state) {
    case "Mastered":
      return wasMastered ? null : { state: "Mastered", stampFirstStudied: false, stampMastered: true };
    case "Partial":
      return wasMastered ? null : { state: "Partial", stampFirstStudied: false, stampMastered: false };
    case "Failed":
      return { state: "Failed", stampFirstStudied: false, stampMastered: false };
    default:
      // Rejected at validation time: a Rated cardResults entry can never be "Seen".
      throw new Error("unreachable: Rated card result resolved to Seen");
  }
}

function usersDoc(db: FirebaseFirestore.Firestore, uid: string): FirebaseFirestore.DocumentReference {
  return db.collection(USERS_COLLECTION).doc(uid);
}

function sessionDocRef(db: FirebaseFirestore.Firestore, uid: string, sessionId: string): FirebaseFirestore.DocumentReference {
  return usersDoc(db, uid).collection(SESSIONS_COLLECTION).doc(sessionId);
}

function subcategoryProgressDocRef(db: FirebaseFirestore.Firestore, uid: string, subcategoryId: string): FirebaseFirestore.DocumentReference {
  return usersDoc(db, uid)
    .collection(PROGRESS_COLLECTION)
    .doc(PROGRESS_DETAILS_DOCUMENT)
    .collection(PROGRESS_SUBCATEGORIES_COLLECTION)
    .doc(subcategoryId);
}

function progressSummaryDocRef(db: FirebaseFirestore.Firestore, uid: string): FirebaseFirestore.DocumentReference {
  return usersDoc(db, uid).collection(PROGRESS_COLLECTION).doc(PROGRESS_SUMMARY_DOCUMENT);
}

function scoringStateDocRef(db: FirebaseFirestore.Firestore, uid: string): FirebaseFirestore.DocumentReference {
  return usersDoc(db, uid).collection(PROGRESS_COLLECTION).doc(PROGRESS_USER_STATS_DOCUMENT);
}

function scoringStateFields(state: ScoringState): Record<string, unknown> {
  return {
    [FIELD_XP]: state.xp,
    [FIELD_LEVEL]: state.level,
    [FIELD_XP_INTO_CURRENT_LEVEL]: state.xpIntoCurrentLevel,
    [FIELD_CURRENT_STREAK]: state.currentStreak,
    [FIELD_BEST_STREAK]: state.bestStreak,
    [FIELD_LAST_STUDY_DATE]: state.lastStudyDate,
    [FIELD_GOAL_MET_DATE]: state.goalMetDate,
  };
}

function readScoringState(snapshot: FirebaseFirestore.DocumentSnapshot): ScoringState {
  if (!snapshot.exists) return DEFAULT_SCORING_STATE;
  const data = snapshot.data() ?? {};
  return {
    xp: (data[FIELD_XP] as number) ?? 0,
    level: (data[FIELD_LEVEL] as number) ?? DEFAULT_SCORING_STATE.level,
    xpIntoCurrentLevel: (data[FIELD_XP_INTO_CURRENT_LEVEL] as number) ?? 0,
    currentStreak: (data[FIELD_CURRENT_STREAK] as number) ?? 0,
    bestStreak: (data[FIELD_BEST_STREAK] as number) ?? 0,
    lastStudyDate: (data[FIELD_LAST_STUDY_DATE] as string) ?? "",
    goalMetDate: (data[FIELD_GOAL_MET_DATE] as string) ?? "",
  };
}

function xpBreakdownFields(breakdown: XpBreakdown): Record<string, unknown> {
  return {
    [FIELD_XP_NEW_CARDS]: breakdown.newCards,
    [FIELD_XP_MASTERED]: breakdown.mastered,
    [FIELD_XP_PARTIAL]: breakdown.partial,
    [FIELD_XP_MASTERY_DEFENSE_BONUS]: breakdown.masteryDefenseBonus,
    [FIELD_XP_DEMASTERED]: breakdown.demastered,
    [FIELD_XP_TIME_STUDIED]: breakdown.timeStudied,
    [FIELD_XP_SESSION_COMPLETION_BONUS]: breakdown.sessionCompletionBonus,
    [FIELD_XP_DAILY_GOAL_BONUS]: breakdown.dailyGoalBonus,
    [FIELD_XP_STREAK_BONUS]: breakdown.streakBonus,
    [FIELD_XP_TOTAL]: breakdown.xpTotal,
  };
}

function readXpBreakdownFields(data: FirebaseFirestore.DocumentData): XpBreakdown {
  return {
    newCards: data[FIELD_XP_NEW_CARDS] ?? 0,
    mastered: data[FIELD_XP_MASTERED] ?? 0,
    partial: data[FIELD_XP_PARTIAL] ?? 0,
    masteryDefenseBonus: data[FIELD_XP_MASTERY_DEFENSE_BONUS] ?? 0,
    demastered: data[FIELD_XP_DEMASTERED] ?? 0,
    timeStudied: data[FIELD_XP_TIME_STUDIED] ?? 0,
    sessionCompletionBonus: data[FIELD_XP_SESSION_COMPLETION_BONUS] ?? 0,
    dailyGoalBonus: data[FIELD_XP_DAILY_GOAL_BONUS] ?? 0,
    streakBonus: data[FIELD_XP_STREAK_BONUS] ?? 0,
    xpTotal: data[FIELD_XP_TOTAL] ?? 0,
  };
}

/**
 * Runs the whole session commit as one Firestore transaction, keyed for idempotency on `sessionId`.
 *
 * 1. Reads `sessions/{sessionId}` first. If it already exists, every other document this function
 *    would otherwise touch was necessarily already written alongside it in that earlier, successful
 *    transaction — so this is a pure cache hit: the session document's own stored fields (its XP
 *    breakdown and the level/xp-into-level/xp-for-next-level/levels-crossed *as they stood right
 *    after that original commit*) are returned unchanged, with no further reads or writes at all.
 *    Returning the account's *current* scoring state here instead would break idempotency — other
 *    sessions committed since would have moved it on.
 * 2. Otherwise, reads every touched Subcategory's prior progress and the account's prior
 *    [ScoringState], computes the new progress writes, the [XpBreakdown] and the new [ScoringState]
 *    (mirroring `CommitStudySessionUseCase`/`CalculateSessionXpUseCase`) with the server-owned XP
 *    configuration (`config/xp`, see `xpConfig.ts`), and writes all four
 *    documents — the session document, every touched Subcategory's progress, the progress summary's
 *    increments, and the full scoring-state overwrite — before returning the freshly computed result.
 */
export async function submitStudySession(uid: string, request: ValidatedSubmitStudySessionRequest): Promise<SubmitStudySessionResult> {
  const db = admin.firestore();
  const sessionRef = sessionDocRef(db, uid, request.sessionId);

  // A plain read outside the transaction: the configuration is admin-edited, not part of this
  // commit's consistency boundary, and a transaction retry has no reason to read it again. A missing
  // or broken document never rejects a submission; it scores with the bundled default instead.
  const { config, fallbackReason } = await loadXpConfig(db);
  if (fallbackReason !== undefined) {
    logger.error(`XP configuration unavailable, scoring with the bundled default: ${fallbackReason}`);
  }

  return db.runTransaction(async (transaction) => {
    const sessionSnapshot = await transaction.get(sessionRef);
    if (sessionSnapshot.exists) {
      const data = sessionSnapshot.data() ?? {};
      return {
        breakdown: readXpBreakdownFields(data),
        level: data[FIELD_LEVEL_AFTER],
        xpIntoCurrentLevel: data[FIELD_XP_INTO_CURRENT_LEVEL_AFTER],
        xpForNextLevel: data[FIELD_XP_FOR_NEXT_LEVEL_AFTER],
        levelsCrossed: (data[FIELD_LEVELS_CROSSED] as number[]) ?? [],
      };
    }

    const derivedStudyDate = deriveLocalStudyDate(request.startedAtEpochMillis, request.studyDateUtcOffsetMinutes);

    const touchedSubcategoryIds = [...new Set(request.cardResults.map((entry) => entry.subcategoryId))];
    const subcategorySnapshots = await Promise.all(touchedSubcategoryIds.map((id) => transaction.get(subcategoryProgressDocRef(db, uid, id))));
    const scoringRef = scoringStateDocRef(db, uid);
    const scoringSnapshot = await transaction.get(scoringRef);
    const todaySessionsSnapshot = await transaction.get(
      usersDoc(db, uid).collection(SESSIONS_COLLECTION).where(FIELD_STUDY_DATE, "==", derivedStudyDate),
    );

    const priorCardsBySubcategory = new Map<string, Map<string, CardState>>();
    touchedSubcategoryIds.forEach((subcategoryId, index) => {
      const snapshot = subcategorySnapshots[index];
      const cardsRaw = (snapshot.exists ? (snapshot.data()?.[FIELD_CARDS] as Record<string, { state?: CardState }>) : undefined) ?? {};
      const cardStates = new Map<string, CardState>();
      for (const [cardId, entry] of Object.entries(cardsRaw)) {
        if (entry?.state) cardStates.set(cardId, entry.state);
      }
      priorCardsBySubcategory.set(subcategoryId, cardStates);
    });

    let newCardsStudied = 0;
    // Map, not a plain object, keyed by client-controlled cardId/subcategoryId strings. Belt-and-
    // suspenders: `requireFirestoreSafeId` already rejects the one value ("__proto__") a bracket
    // assignment into `{}` would mishandle (Object.prototype's `__proto__` is an accessor, so
    // `obj[key] = v` reassigns the prototype instead of creating an own property), and Firestore's
    // own write path independently rejects that same reserved-name shape too — but a Map sidesteps
    // the whole hazard class without depending on either of those holding.
    const progressWrites = new Map<string, Map<string, CardProgressUpdateFields>>();
    const summaryDeltas = new Map<string, { masteredDelta: number; studiedDelta: number }>();

    for (const subcategoryId of touchedSubcategoryIds) {
      const priorCards = priorCardsBySubcategory.get(subcategoryId) ?? new Map<string, CardState>();
      const entries = request.cardResults.filter((entry) => entry.subcategoryId === subcategoryId);
      const cardUpdates = new Map<string, CardProgressUpdateFields>();
      let masteredDelta = 0;
      let studiedDelta = 0;

      for (const entry of entries) {
        const priorState = priorCards.get(entry.cardId);
        const priorExists = priorState !== undefined;
        const wasMastered = priorState === "Mastered";
        if (!priorExists) {
          newCardsStudied++;
          studiedDelta++;
        }
        masteredDelta += resolveMasteredDelta(entry, wasMastered);

        const update = resolveUpdate(request.studyMode, entry, priorExists, wasMastered);
        if (update) cardUpdates.set(entry.cardId, update);
      }

      if (cardUpdates.size > 0) progressWrites.set(subcategoryId, cardUpdates);
      if (masteredDelta !== 0 || studiedDelta !== 0) summaryDeltas.set(subcategoryId, { masteredDelta, studiedDelta });
    }

    // wasPreviouslyMastered is authoritative here, not trusted from the client: this transaction
    // already read every touched card's prior state above (priorCardsBySubcategory) to compute
    // progressWrites/summaryDeltas, so scoring and the persisted defended/demastered counters reuse
    // that same server-read state rather than request.cardResults' own copy of the flag.
    // Fast entries carry no wasPreviouslyMastered at all (ADR-0014: Rated-only field) — left
    // untouched here, not coerced into a stray `false`.
    const authoritativeCardResults = request.cardResults.map((entry) =>
      entry.wasPreviouslyMastered === undefined
        ? entry
        : { ...entry, wasPreviouslyMastered: priorCardsBySubcategory.get(entry.subcategoryId)?.get(entry.cardId) === "Mastered" },
    );

    // This session isn't in todaySessionsSnapshot's results yet — it doesn't exist until this
    // transaction commits — so its own durationSeconds is added on top of the query's sum.
    const todaySeconds =
      todaySessionsSnapshot.docs.reduce((sum, doc) => sum + ((doc.data()[FIELD_DURATION_SECONDS] as number) ?? 0), 0) + request.durationSeconds;
    const todayTotalMinutes = Math.floor(todaySeconds / SECONDS_PER_MINUTE);

    const currentScoringState = readScoringState(scoringSnapshot);
    const streakAndGoalInput: StreakAndGoalInput = {
      studyDate: derivedStudyDate,
      dailyGoalMinutes: request.dailyGoalMinutes,
      todayTotalMinutes,
    };
    const { breakdown, newScoringState, levelsCrossed } = computeSessionXp(
      {
        studyMode: request.studyMode,
        durationSeconds: request.durationSeconds,
        abandoned: request.abandoned,
        cardResults: authoritativeCardResults,
      },
      newCardsStudied,
      currentScoringState,
      config,
      streakAndGoalInput,
    );
    const xpForNextLevel = levelThreshold(config, newScoringState.level);

    const sessionFields: Record<string, unknown> = {
      [FIELD_SESSION_ID]: request.sessionId,
      [FIELD_START_TIMESTAMP]: admin.firestore.Timestamp.fromMillis(request.startedAtEpochMillis),
      [FIELD_DURATION_SECONDS]: request.durationSeconds,
      [FIELD_STUDY_MODE]: request.studyMode,
      [FIELD_IS_ABANDONED]: request.abandoned,
      [FIELD_CATEGORY_ID]: request.categoryId,
      [FIELD_CATEGORY_NAME]: request.categoryName,
      [FIELD_SUBCATEGORY_IDS]: request.subcategoryIds,
      [FIELD_SUBCATEGORY_NAMES]: request.subcategoryNames,
      [FIELD_STUDY_DATE]: derivedStudyDate,
      [FIELD_CARD_COUNT]: request.cardResults.length,
      [FIELD_NEW_CARDS_STUDIED]: newCardsStudied,
      [FIELD_CARD_RESULTS]: Object.fromEntries(
        authoritativeCardResults.map((entry) => [
          entry.cardId,
          {
            [FIELD_CARD_SUBCATEGORY_ID]: entry.subcategoryId,
            [FIELD_STATE]: entry.state,
            ...(entry.attemptsUsed !== undefined ? { [FIELD_ATTEMPTS_USED]: entry.attemptsUsed } : {}),
            ...(entry.wasPreviouslyMastered !== undefined ? { [FIELD_WAS_PREVIOUSLY_MASTERED]: entry.wasPreviouslyMastered } : {}),
          },
        ]),
      ),
      [FIELD_LEVELS_CROSSED]: levelsCrossed,
      [FIELD_LEVEL_AFTER]: newScoringState.level,
      [FIELD_XP_INTO_CURRENT_LEVEL_AFTER]: newScoringState.xpIntoCurrentLevel,
      [FIELD_XP_FOR_NEXT_LEVEL_AFTER]: xpForNextLevel,
      ...xpBreakdownFields(breakdown),
    };
    if (request.studyMode === "Rated") {
      sessionFields[FIELD_CARDS_MASTERED] = request.cardResults.filter((entry) => entry.state === "Mastered").length;
      sessionFields[FIELD_CARDS_PARTIAL] = request.cardResults.filter((entry) => entry.state === "Partial").length;
      sessionFields[FIELD_CARDS_DEFENDED] = authoritativeCardResults.filter((entry) => entry.state === "Mastered" && entry.wasPreviouslyMastered).length;
      sessionFields[FIELD_CARDS_DEMASTERED] = authoritativeCardResults.filter((entry) => entry.state === "Failed" && entry.wasPreviouslyMastered).length;
    }
    transaction.set(sessionRef, sessionFields);

    for (const [subcategoryId, cardUpdates] of progressWrites) {
      const fields: Record<string, unknown> = {
        [FIELD_CATEGORY_ID]: request.categoryId,
        // Object.fromEntries builds genuine own properties (CreateDataProperty), unlike a bracket
        // assignment into `{}` — safe even if cardId is a reserved key like "__proto__".
        [FIELD_CARDS]: Object.fromEntries(
          [...cardUpdates].map(([cardId, update]) => {
            const entryFields: Record<string, unknown> = { [FIELD_STATE]: update.state };
            if (update.stampFirstStudied) entryFields[FIELD_FIRST_STUDIED_AT] = admin.firestore.FieldValue.serverTimestamp();
            if (update.stampMastered) entryFields[FIELD_MASTERED_AT] = admin.firestore.FieldValue.serverTimestamp();
            return [cardId, entryFields];
          }),
        ),
      };
      transaction.set(subcategoryProgressDocRef(db, uid, subcategoryId), fields, { merge: true });
    }

    if (summaryDeltas.size > 0) {
      const subcategories = Object.fromEntries(
        [...summaryDeltas].map(([subcategoryId, delta]) => [
          subcategoryId,
          {
            [FIELD_MASTERED_COUNT]: admin.firestore.FieldValue.increment(delta.masteredDelta),
            [FIELD_STUDIED_COUNT]: admin.firestore.FieldValue.increment(delta.studiedDelta),
          },
        ]),
      );
      transaction.set(progressSummaryDocRef(db, uid), { [FIELD_SUBCATEGORIES]: subcategories }, { merge: true });
    }

    transaction.set(scoringRef, scoringStateFields(newScoringState));

    return {
      breakdown,
      level: newScoringState.level,
      xpIntoCurrentLevel: newScoringState.xpIntoCurrentLevel,
      xpForNextLevel,
      levelsCrossed,
    };
  });
}
