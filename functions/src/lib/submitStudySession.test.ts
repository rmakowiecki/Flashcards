// Transaction integration tests — run against a real Firestore emulator, not a
// mock, since the whole point of this function is atomic, idempotent multi-document writes that a
// mocked Firestore could not meaningfully exercise. `npm test` (see package.json) starts the
// Firestore emulator via `firebase emulators:exec` before this file runs.
//
// Deliberately does not go through a running Functions emulator or an `onCall` HTTP round trip:
// `submitStudySession`/`validateSubmitStudySessionRequest` are called directly, the same seam
// `index.ts`'s thin `onCall` wrapper delegates to. This exercises every line this ticket is
// responsible for — the auth check `index.ts` itself performs is a single `if (!uid) throw` guard,
// trivial enough that a direct call with/without a uid covers it without needing a live Auth
// emulator and token round trip for zero extra coverage.
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { after, afterEach, before, describe, it } from "node:test";
import * as admin from "firebase-admin";
import {
  MAX_RECENT_ENTRIES,
  RecentEntry,
  ValidatedSubmitStudySessionRequest,
  nextRecentEntries,
  requireOwnerMatchesCaller,
  submitStudySession,
  validateSubmitStudySessionRequest,
} from "./submitStudySession";
import { loadXpConfig, xpConfigDocRef } from "./xpConfig";
import { DEFAULT_XP_CONFIG, XpConfig } from "./xpScoring";

const DEFAULT_XP_RATES = {
  newCardStudied: DEFAULT_XP_CONFIG.newCardStudied,
  cardMastered: DEFAULT_XP_CONFIG.cardMastered,
  cardPartial: DEFAULT_XP_CONFIG.cardPartial,
  masteryDefended: DEFAULT_XP_CONFIG.masteryDefended,
  cardDemastered: DEFAULT_XP_CONFIG.cardDemastered,
  minuteStudied: DEFAULT_XP_CONFIG.minuteStudied,
  sessionCompleted: DEFAULT_XP_CONFIG.sessionCompleted,
};

const TEST_PROJECT_ID = "flashcards-functions-test";

before(() => {
  admin.initializeApp({ projectId: TEST_PROJECT_ID });
});

after(async () => {
  await Promise.all(admin.apps.map((app) => app?.delete()));
});

// Every uid in this file starts with an empty ScoringState (currentStreak: 0, lastStudyDate: "").
// A default studyDate later than "" therefore always advances the streak on a fresh uid's first
// submission (see computeStreakAndGoalAwards's "first-ever submission" rule) — DEFAULT_STREAK_BONUS
// below is that award, added into every xpTotal assertion a first submission's test still makes.
// dailyGoalMinutes defaults far out of reach so no test here accidentally also earns dailyGoalBonus;
// the streak-and-goal describe block below exercises that award on its own, deliberately.
//
// studyDate is no longer a request field: the server derives it from startedAtEpochMillis
// and studyDateUtcOffsetMinutes (deriveLocalStudyDate). DEFAULT_STARTED_AT_EPOCH_MILLIS is fixed —
// not Date.now() — precisely so it derives to the fixed DEFAULT_STUDY_DATE below at offset 0,
// regardless of which real-world date the test suite happens to run on.
const DEFAULT_STARTED_AT_EPOCH_MILLIS = Date.UTC(2026, 8, 1, 12, 0, 0); // noon UTC, 2026-09-01
const DEFAULT_STUDY_DATE = "2026-09-01";
const DEFAULT_DAILY_GOAL_MINUTES = 999999;
const DEFAULT_STREAK_BONUS = 250; // 1 * DEFAULT_XP_CONFIG.streakPerDay
const MAX_UTC_OFFSET_MINUTES = 14 * 60;
const DEFAULT_OWNER_UID = "owner-uid";
const MINUTE_MILLIS = 60_000;
const INVALID_SOURCE_TYPE_MESSAGE = /sourceType must be SingleSubcategory, Quick or Custom/;

function rawRatedRequest(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    ownerUid: DEFAULT_OWNER_UID,
    sessionId: randomUUID(),
    studyMode: "Rated",
    startedAtEpochMillis: DEFAULT_STARTED_AT_EPOCH_MILLIS,
    durationSeconds: 60,
    abandoned: false,
    categoryId: "cat-1",
    categoryName: "Category One",
    subcategoryIds: ["sub-1"],
    subcategoryNames: ["Subcategory One"],
    sourceType: "SingleSubcategory",
    voiceAnswering: false,
    cardResults: [{ cardId: "card-1", subcategoryId: "sub-1", state: "Mastered", attemptsUsed: 1, wasPreviouslyMastered: false }],
    studyDateUtcOffsetMinutes: 0,
    dailyGoalMinutes: DEFAULT_DAILY_GOAL_MINUTES,
    ...overrides,
  };
}

function rawFastRequest(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  const { voiceAnswering, ...ratedWithoutVoiceAnswering } = rawRatedRequest();
  return {
    ...ratedWithoutVoiceAnswering,
    studyMode: "Fast",
    readAloud: false,
    cardResults: [{ cardId: "card-1", subcategoryId: "sub-1", state: "Seen" }],
    ...overrides,
  };
}

describe("validateSubmitStudySessionRequest", () => {
  it("rejects a payload missing ownerUid", () => {
    const { ownerUid, ...withoutOwnerUid } = rawRatedRequest();
    assert.throws(() => validateSubmitStudySessionRequest(withoutOwnerUid), /ownerUid/);
  });

  it("rejects an empty ownerUid", () => {
    assert.throws(() => validateSubmitStudySessionRequest(rawRatedRequest({ ownerUid: "" })), /ownerUid/);
  });

  it("rejects a payload missing sessionId", () => {
    const { sessionId, ...withoutSessionId } = rawRatedRequest();
    assert.throws(() => validateSubmitStudySessionRequest(withoutSessionId), /sessionId/);
  });

  it("rejects a payload missing categoryId", () => {
    const { categoryId, ...withoutCategoryId } = rawRatedRequest();
    assert.throws(() => validateSubmitStudySessionRequest(withoutCategoryId), /categoryId/);
  });

  it("rejects a non-finite durationSeconds", () => {
    assert.throws(() => validateSubmitStudySessionRequest(rawRatedRequest({ durationSeconds: Number.NaN })), /durationSeconds/);
    assert.throws(() => validateSubmitStudySessionRequest(rawRatedRequest({ durationSeconds: Number.POSITIVE_INFINITY })), /durationSeconds/);
  });

  it("rejects mismatched subcategoryIds/subcategoryNames lengths", () => {
    const request = rawRatedRequest({ subcategoryIds: ["sub-1", "sub-2"], subcategoryNames: ["Subcategory One"] });
    assert.throws(() => validateSubmitStudySessionRequest(request), /same length/);
  });

  it("rejects an unknown studyMode", () => {
    assert.throws(() => validateSubmitStudySessionRequest(rawRatedRequest({ studyMode: "Bogus" })), /studyMode/);
  });

  it("rejects a Rated cardResults entry with state Seen", () => {
    const request = rawRatedRequest({ cardResults: [{ cardId: "card-1", subcategoryId: "sub-1", state: "Seen" }] });
    assert.throws(() => validateSubmitStudySessionRequest(request), /can never be Seen/);
  });

  it("rejects a Fast cardResults entry with a non-Seen state", () => {
    const request = rawFastRequest({ cardResults: [{ cardId: "card-1", subcategoryId: "sub-1", state: "Mastered" }] });
    assert.throws(() => validateSubmitStudySessionRequest(request), /must be Seen/);
  });

  it("rejects an empty cardResults array", () => {
    assert.throws(() => validateSubmitStudySessionRequest(rawRatedRequest({ cardResults: [] })), /non-empty array/);
  });

  it("rejects a duplicate cardId within cardResults", () => {
    const duplicateCardId = "card-1";
    const request = rawRatedRequest({
      cardResults: [
        { cardId: duplicateCardId, subcategoryId: "sub-1", state: "Mastered", attemptsUsed: 1, wasPreviouslyMastered: false },
        { cardId: duplicateCardId, subcategoryId: "sub-1", state: "Mastered", attemptsUsed: 1, wasPreviouslyMastered: false },
      ],
    });
    assert.throws(() => validateSubmitStudySessionRequest(request), /duplicate cardId/);
  });

  it("rejects a cardResults entry whose subcategoryId isn't declared in subcategoryIds", () => {
    const request = rawRatedRequest({ cardResults: [{ cardId: "card-1", subcategoryId: "undeclared-sub", state: "Mastered", attemptsUsed: 1, wasPreviouslyMastered: false }] });
    assert.throws(() => validateSubmitStudySessionRequest(request), /not present in subcategoryIds/);
  });

  it("rejects a cardId matching Firestore's reserved __name__ pattern", () => {
    // Firestore rejects `__.*__` outright (both as a document id and as a nested map field-path
    // segment) — reject it here, cleanly, rather than let it surface as a raw internal error from
    // deep inside the transaction.
    const request = rawRatedRequest({ cardResults: [{ cardId: "__proto__", subcategoryId: "sub-1", state: "Mastered", attemptsUsed: 1, wasPreviouslyMastered: false }] });
    assert.throws(() => validateSubmitStudySessionRequest(request), /not a valid Firestore id/);
  });

  it("rejects a subcategoryId matching Firestore's reserved __name__ pattern", () => {
    const request = rawRatedRequest({ subcategoryIds: ["__proto__"], cardResults: [{ cardId: "card-1", subcategoryId: "__proto__", state: "Mastered", attemptsUsed: 1, wasPreviouslyMastered: false }] });
    assert.throws(() => validateSubmitStudySessionRequest(request), /not a valid Firestore id/);
  });

  it("rejects a missing studyDateUtcOffsetMinutes", () => {
    const { studyDateUtcOffsetMinutes, ...withoutOffset } = rawRatedRequest();
    assert.throws(() => validateSubmitStudySessionRequest(withoutOffset), /studyDateUtcOffsetMinutes/);
  });

  it("rejects a studyDateUtcOffsetMinutes outside the real-world UTC offset range", () => {
    assert.throws(
      () => validateSubmitStudySessionRequest(rawRatedRequest({ studyDateUtcOffsetMinutes: MAX_UTC_OFFSET_MINUTES + 1 })),
      /studyDateUtcOffsetMinutes/,
    );
    assert.throws(
      () => validateSubmitStudySessionRequest(rawRatedRequest({ studyDateUtcOffsetMinutes: -MAX_UTC_OFFSET_MINUTES - 1 })),
      /studyDateUtcOffsetMinutes/,
    );
  });

  it("accepts a studyDateUtcOffsetMinutes at either real-world extreme", () => {
    assert.equal(
      validateSubmitStudySessionRequest(rawRatedRequest({ studyDateUtcOffsetMinutes: MAX_UTC_OFFSET_MINUTES })).studyDateUtcOffsetMinutes,
      MAX_UTC_OFFSET_MINUTES,
    );
    assert.equal(
      validateSubmitStudySessionRequest(rawRatedRequest({ studyDateUtcOffsetMinutes: -MAX_UTC_OFFSET_MINUTES })).studyDateUtcOffsetMinutes,
      -MAX_UTC_OFFSET_MINUTES,
    );
  });

  it("rejects a missing dailyGoalMinutes", () => {
    const { dailyGoalMinutes, ...withoutDailyGoalMinutes } = rawRatedRequest();
    assert.throws(() => validateSubmitStudySessionRequest(withoutDailyGoalMinutes), /dailyGoalMinutes/);
  });

  it("rejects a non-positive dailyGoalMinutes", () => {
    assert.throws(() => validateSubmitStudySessionRequest(rawRatedRequest({ dailyGoalMinutes: 0 })), /dailyGoalMinutes/);
    assert.throws(() => validateSubmitStudySessionRequest(rawRatedRequest({ dailyGoalMinutes: -5 })), /dailyGoalMinutes/);
  });

  it("rejects a payload missing sourceType", () => {
    const { sourceType, ...withoutSourceType } = rawRatedRequest();
    assert.throws(() => validateSubmitStudySessionRequest(withoutSourceType), INVALID_SOURCE_TYPE_MESSAGE);
  });

  it("rejects an unknown sourceType", () => {
    assert.throws(() => validateSubmitStudySessionRequest(rawRatedRequest({ sourceType: "Composite" })), INVALID_SOURCE_TYPE_MESSAGE);
  });

  it("rejects a Rated payload missing voiceAnswering", () => {
    const { voiceAnswering, ...withoutVoiceAnswering } = rawRatedRequest();
    assert.throws(() => validateSubmitStudySessionRequest(withoutVoiceAnswering), /voiceAnswering must be a boolean for a Rated session/);
  });

  it("rejects a Rated payload carrying readAloud, even as false", () => {
    assert.throws(() => validateSubmitStudySessionRequest(rawRatedRequest({ readAloud: false })), /readAloud must not be present on a Rated session/);
  });

  it("rejects a Fast payload missing readAloud", () => {
    const { readAloud, ...withoutReadAloud } = rawFastRequest();
    assert.throws(() => validateSubmitStudySessionRequest(withoutReadAloud), /readAloud must be a boolean for a Fast session/);
  });

  it("rejects a Fast payload carrying voiceAnswering, even as false", () => {
    assert.throws(() => validateSubmitStudySessionRequest(rawFastRequest({ voiceAnswering: false })), /voiceAnswering must not be present on a Fast session/);
  });

  for (const sourceType of ["SingleSubcategory", "Quick", "Custom"]) {
    it(`accepts a Rated and a Fast payload from a ${sourceType} session, each with only its own delivery flag`, () => {
      const rated = validateSubmitStudySessionRequest(rawRatedRequest({ sourceType, voiceAnswering: true }));
      assert.equal(rated.sourceType, sourceType);
      assert.deepEqual(rated.delivery, { voiceAnswering: true });

      const fast = validateSubmitStudySessionRequest(rawFastRequest({ sourceType, readAloud: true }));
      assert.equal(fast.sourceType, sourceType);
      assert.deepEqual(fast.delivery, { readAloud: true });
    });
  }

  it("accepts a structurally valid Rated payload", () => {
    const validated = validateSubmitStudySessionRequest(rawRatedRequest());
    assert.equal(validated.studyMode, "Rated");
    assert.equal(validated.cardResults.length, 1);
    assert.equal(validated.studyDateUtcOffsetMinutes, 0);
    assert.equal(validated.dailyGoalMinutes, DEFAULT_DAILY_GOAL_MINUTES);
  });
});

describe("requireOwnerMatchesCaller", () => {
  it("accepts a session owned by the caller", () => {
    const request = validateSubmitStudySessionRequest(rawRatedRequest());
    assert.doesNotThrow(() => requireOwnerMatchesCaller(DEFAULT_OWNER_UID, request));
  });

  it("rejects a session owned by another User as unauthenticated", () => {
    const request = validateSubmitStudySessionRequest(rawRatedRequest());
    assert.throws(() => requireOwnerMatchesCaller("other-uid", request), { code: "unauthenticated" });
  });
});

describe("nextRecentEntries", () => {
  const MAX_ENTRIES = 3;

  function recentEntry(sessionId: string, startedAtMinute: number): RecentEntry {
    return {
      sessionId,
      startTimestamp: admin.firestore.Timestamp.fromMillis(DEFAULT_STARTED_AT_EPOCH_MILLIS + startedAtMinute * MINUTE_MILLIS),
      durationSeconds: 60,
      studyMode: "Rated",
      voiceAnswering: false,
      sourceType: "SingleSubcategory",
      categoryId: "cat-1",
      categoryName: "Category One",
      subcategoryIds: ["sub-1"],
      subcategoryNames: ["Subcategory One"],
      cardCount: 1,
      xpTotal: 100,
    };
  }

  function entriesOf(...entries: RecentEntry[]): Record<string, RecentEntry> {
    return Object.fromEntries(entries.map((entry) => [entry.sessionId, entry]));
  }

  it("adds a new session's entry", () => {
    const existing = entriesOf(recentEntry("a", 1));
    const added = recentEntry("b", 2);

    assert.deepEqual(nextRecentEntries(existing, added, MAX_ENTRIES), entriesOf(recentEntry("a", 1), added));
  });

  it("returns null for a session already present, whatever the new entry holds", () => {
    const existing = entriesOf(recentEntry("a", 1));

    assert.equal(nextRecentEntries(existing, { ...recentEntry("a", 5), xpTotal: 999 }, MAX_ENTRIES), null);
  });

  it("evicts the entry with the oldest startTimestamp once over the limit", () => {
    const existing = entriesOf(recentEntry("a", 1), recentEntry("b", 2), recentEntry("c", 3));

    assert.deepEqual(
      nextRecentEntries(existing, recentEntry("d", 4), MAX_ENTRIES),
      entriesOf(recentEntry("b", 2), recentEntry("c", 3), recentEntry("d", 4)),
    );
  });

  it("orders by startTimestamp, not submission order: a late session in the middle is kept", () => {
    const existing = entriesOf(recentEntry("a", 1), recentEntry("c", 3), recentEntry("d", 4));

    assert.deepEqual(
      nextRecentEntries(existing, recentEntry("b", 2), MAX_ENTRIES),
      entriesOf(recentEntry("b", 2), recentEntry("c", 3), recentEntry("d", 4)),
    );
  });

  it("returns null for a late session older than every kept entry, since it is evicted straight away", () => {
    const existing = entriesOf(recentEntry("b", 2), recentEntry("c", 3), recentEntry("d", 4));

    assert.equal(nextRecentEntries(existing, recentEntry("a", 1), MAX_ENTRIES), null);
  });

  it("breaks a startTimestamp tie by evicting the lower sessionId", () => {
    const withLowerIdOldest = entriesOf(recentEntry("a", 1), recentEntry("c", 2), recentEntry("d", 3));
    const withHigherIdOldest = entriesOf(recentEntry("b", 1), recentEntry("c", 2), recentEntry("d", 3));

    assert.deepEqual(nextRecentEntries(withLowerIdOldest, recentEntry("b", 1), MAX_ENTRIES), withHigherIdOldest);
    assert.equal(nextRecentEntries(withHigherIdOldest, recentEntry("a", 1), MAX_ENTRIES), null);
  });

  it("evicts an entry with a missing or malformed startTimestamp first", () => {
    const missing = { ...recentEntry("x", 9), startTimestamp: undefined } as unknown as RecentEntry;
    const malformed = { ...recentEntry("y", 9), startTimestamp: "2026-09-01" } as unknown as RecentEntry;
    const existing = { ...entriesOf(recentEntry("b", 2)), x: missing, y: malformed };

    assert.deepEqual(nextRecentEntries(existing, recentEntry("a", 1), 2), entriesOf(recentEntry("a", 1), recentEntry("b", 2)));
  });
});

describe("submitStudySession", () => {
  it("a first submission writes the session, progress, summary and scoring-state documents and returns the correct breakdown", async () => {
    const uid = randomUUID();
    const request = validateSubmitStudySessionRequest(rawRatedRequest());

    const result = await submitStudySession(uid, request);

    assert.equal(result.breakdown.newCards, 10, "card-1 has no prior progress entry, so it counts as newly studied");
    assert.equal(result.breakdown.mastered, 100);
    assert.equal(result.breakdown.timeStudied, 10);
    assert.equal(result.breakdown.sessionCompletionBonus, 500);
    // A fresh uid's ScoringState starts with lastStudyDate "" — this first submission's studyDate
    // is necessarily later, so the streak also advances to 1 (DEFAULT_STREAK_BONUS). dailyGoalMinutes
    // is deliberately far out of reach (DEFAULT_DAILY_GOAL_MINUTES), so no dailyGoalBonus here.
    assert.equal(result.breakdown.streakBonus, DEFAULT_STREAK_BONUS);
    assert.equal(result.breakdown.dailyGoalBonus, 0);
    assert.equal(result.breakdown.xpTotal, 10 + 100 + 10 + 500 + DEFAULT_STREAK_BONUS);
    assert.equal(result.level, 1);
    assert.equal(result.xpIntoCurrentLevel, result.breakdown.xpTotal);
    assert.deepEqual(result.levelsCrossed, []);
    assert.equal(result.durationSeconds, 60);
    assert.deepEqual(result.rates, DEFAULT_XP_RATES);
    assert.deepEqual(result.counts, { newCardsStudied: 1, newlyMastered: 1, partial: 0, defended: 0, demastered: 0 });

    const db = admin.firestore();
    const sessionDoc = await db.doc(`users/${uid}/sessions/${request.sessionId}`).get();
    assert.ok(sessionDoc.exists);
    assert.equal(sessionDoc.data()?.xpTotal, result.breakdown.xpTotal);
    assert.equal(sessionDoc.data()?.cardsMastered, 1);
    assert.equal(sessionDoc.data()?.studyDate, DEFAULT_STUDY_DATE);

    const progressDoc = await db.doc(`users/${uid}/progress/details/subcategories/sub-1`).get();
    const cards = progressDoc.data()?.cards ?? {};
    assert.equal(cards["card-1"].state, "Mastered");
    assert.ok(cards["card-1"].masteredAt, "a newly mastered card must stamp masteredAt");
    assert.ok(cards["card-1"].firstStudiedAt, "a card with no prior entry must stamp firstStudiedAt");
    assert.deepEqual(sessionDoc.data()?.xpRates, DEFAULT_XP_RATES, "the rates the session was scored with are stored with it");

    const summaryDoc = await db.doc(`users/${uid}/progress/summary`).get();
    assert.equal(summaryDoc.data()?.subcategories?.["sub-1"]?.masteredCount, 1);
    assert.equal(summaryDoc.data()?.subcategories?.["sub-1"]?.studiedCount, 1);

    const scoringDoc = await db.doc(`users/${uid}/progress/user-stats`).get();
    assert.equal(scoringDoc.data()?.xp, result.breakdown.xpTotal);
    assert.equal(scoringDoc.data()?.level, result.level);
  });

  it("a retried submission of an already-processed session is a no-op: same result, no double award", async () => {
    const uid = randomUUID();
    const request = validateSubmitStudySessionRequest(rawRatedRequest());

    const first = await submitStudySession(uid, request);
    const second = await submitStudySession(uid, request);

    assert.deepEqual(second, first);

    const scoringDoc = await admin.firestore().doc(`users/${uid}/progress/user-stats`).get();
    assert.equal(scoringDoc.data()?.xp, first.breakdown.xpTotal, "xp must not be awarded twice");
  });

  it("a retry answers with the stored rates, not the XP configuration in force at retry time", async () => {
    const uid = randomUUID();
    const request = validateSubmitStudySessionRequest(rawRatedRequest());
    const first = await submitStudySession(uid, request);

    await xpConfigDocRef(admin.firestore()).set({ ...DEFAULT_XP_CONFIG, cardMastered: 1 });
    try {
      const second = await submitStudySession(uid, request);
      assert.deepEqual(second, first);
    } finally {
      await xpConfigDocRef(admin.firestore()).delete();
    }
  });

  it("a retry of a session stored before rates were recorded omits rates and still returns every other field", async () => {
    const uid = randomUUID();
    const request = validateSubmitStudySessionRequest(rawRatedRequest());
    const first = await submitStudySession(uid, request);
    await admin.firestore().doc(`users/${uid}/sessions/${request.sessionId}`).update({ xpRates: admin.firestore.FieldValue.delete() });

    const second = await submitStudySession(uid, request);

    assert.equal("rates" in second, false, "rates must be absent, not a placeholder");
    const { rates, ...firstWithoutRates } = first;
    assert.deepEqual(second, firstWithoutRates);
  });

  it("two concurrent submissions of the same not-yet-processed session award exactly once (the actual race the idempotency check exists for)", async () => {
    const uid = randomUUID();
    const request = validateSubmitStudySessionRequest(rawRatedRequest());

    // Sequential calls (the test above) never race the "does sessionId already exist" check itself —
    // both transactions here start from a state where the session doc genuinely does not exist yet.
    const [first, second] = await Promise.all([submitStudySession(uid, request), submitStudySession(uid, request)]);

    assert.deepEqual(second, first, "one of the two concurrent calls must retry and observe the other's committed write");

    const scoringDoc = await admin.firestore().doc(`users/${uid}/progress/user-stats`).get();
    assert.equal(scoringDoc.data()?.xp, first.breakdown.xpTotal, "xp must not be awarded twice");

    const summaryDoc = await admin.firestore().doc(`users/${uid}/progress/summary`).get();
    assert.equal(summaryDoc.data()?.subcategories?.["sub-1"]?.studiedCount, 1, "the card must not be counted studied twice");
  });

  it("two different sessions submitted in close succession both apply, neither lost", async () => {
    const uid = randomUUID();
    const subcategoryId = "sub-1";
    // A warm-up submission on the same studyDate first, awaited (not concurrent), so both A and B
    // below race from a ScoringState whose lastStudyDate already equals studyDate — same-day, no
    // further streak advance for either — rather than one of them winning the "first submission ever"
    // streak award and the other not, which would make their award shapes genuinely asymmetric.
    const warmup = await submitStudySession(
      uid,
      validateSubmitStudySessionRequest(
        rawRatedRequest({ cardResults: [{ cardId: "card-warmup", subcategoryId, state: "Mastered", attemptsUsed: 1, wasPreviouslyMastered: false }] }),
      ),
    );
    const requestA = validateSubmitStudySessionRequest(
      rawRatedRequest({ cardResults: [{ cardId: "card-a", subcategoryId, state: "Mastered", attemptsUsed: 1, wasPreviouslyMastered: false }] }),
    );
    const requestB = validateSubmitStudySessionRequest(
      rawRatedRequest({ cardResults: [{ cardId: "card-b", subcategoryId, state: "Mastered", attemptsUsed: 1, wasPreviouslyMastered: false }] }),
    );

    const [resultA, resultB] = await Promise.all([submitStudySession(uid, requestA), submitStudySession(uid, requestB)]);

    // Whichever transaction Firestore serializes first sees level 1 -> 1 (or crosses into a level
    // the other then starts from); either way, applied together they must sum, never clobber.
    assert.equal(resultA.breakdown.xpTotal, resultB.breakdown.xpTotal, "both sessions earn the identical award shape");

    const scoringDoc = await admin.firestore().doc(`users/${uid}/progress/user-stats`).get();
    assert.equal(scoringDoc.data()?.xp, warmup.breakdown.xpTotal + resultA.breakdown.xpTotal + resultB.breakdown.xpTotal);

    const summaryDoc = await admin.firestore().doc(`users/${uid}/progress/summary`).get();
    assert.equal(summaryDoc.data()?.subcategories?.[subcategoryId]?.masteredCount, 3);
    assert.equal(summaryDoc.data()?.subcategories?.[subcategoryId]?.studiedCount, 3);
  });

  it("a defended card (already Mastered) produces no progress write and earns the defense bonus, not a fresh mastery", async () => {
    const uid = randomUUID();
    const subcategoryId = "sub-1";
    const cardId = "card-1";
    const first = validateSubmitStudySessionRequest(
      rawRatedRequest({ cardResults: [{ cardId, subcategoryId, state: "Mastered", attemptsUsed: 1, wasPreviouslyMastered: false }] }),
    );
    await submitStudySession(uid, first);

    const second = validateSubmitStudySessionRequest(
      rawRatedRequest({ cardResults: [{ cardId, subcategoryId, state: "Mastered", attemptsUsed: 1, wasPreviouslyMastered: true }] }),
    );
    const result = await submitStudySession(uid, second);

    assert.equal(result.breakdown.mastered, 0);
    assert.equal(result.breakdown.masteryDefenseBonus, 50);

    const summaryDoc = await admin.firestore().doc(`users/${uid}/progress/summary`).get();
    // Only the first session's mastery counted; the defended session's masteredDelta is 0.
    assert.equal(summaryDoc.data()?.subcategories?.[subcategoryId]?.masteredCount, 1);
  });

  it("Rated counts use the server-read prior mastery, matching the breakdown line for line, and a retry repeats them", async () => {
    const uid = randomUUID();
    const subcategoryId = "sub-1";
    await submitStudySession(
      uid,
      validateSubmitStudySessionRequest(
        rawRatedRequest({
          cardResults: [
            { cardId: "card-defended", subcategoryId, state: "Mastered", attemptsUsed: 1, wasPreviouslyMastered: false },
            { cardId: "card-lost", subcategoryId, state: "Mastered", attemptsUsed: 1, wasPreviouslyMastered: false },
          ],
        }),
      ),
    );
    // The client claims no card was previously Mastered; the server's own Card Progress read wins.
    const request = validateSubmitStudySessionRequest(
      rawRatedRequest({
        cardResults: [
          { cardId: "card-defended", subcategoryId, state: "Mastered", attemptsUsed: 1, wasPreviouslyMastered: false },
          { cardId: "card-lost", subcategoryId, state: "Failed", attemptsUsed: 3, wasPreviouslyMastered: false },
          { cardId: "card-new-mastered", subcategoryId, state: "Mastered", attemptsUsed: 2, wasPreviouslyMastered: false },
          { cardId: "card-new-partial", subcategoryId, state: "Partial", attemptsUsed: 3, wasPreviouslyMastered: false },
        ],
      }),
    );

    const result = await submitStudySession(uid, request);

    assert.deepEqual(result.counts, { newCardsStudied: 2, newlyMastered: 1, partial: 1, defended: 1, demastered: 1 });
    const counts = result.counts as Required<typeof result.counts>;
    assert.equal(result.breakdown.newCards, counts.newCardsStudied * DEFAULT_XP_CONFIG.newCardStudied);
    assert.equal(result.breakdown.mastered, counts.newlyMastered * DEFAULT_XP_CONFIG.cardMastered);
    assert.equal(result.breakdown.partial, counts.partial * DEFAULT_XP_CONFIG.cardPartial);
    assert.equal(result.breakdown.masteryDefenseBonus, counts.defended * DEFAULT_XP_CONFIG.masteryDefended);
    assert.equal(result.breakdown.demastered, counts.demastered * DEFAULT_XP_CONFIG.cardDemastered);

    const retry = await submitStudySession(uid, request);
    assert.deepEqual(retry, result);
  });

  it("a Fast submission's counts carry newCardsStudied only, with no Rated-only counts", async () => {
    const uid = randomUUID();
    const request = validateSubmitStudySessionRequest(
      rawFastRequest({
        durationSeconds: 125,
        cardResults: [
          { cardId: "card-1", subcategoryId: "sub-1", state: "Seen" },
          { cardId: "card-2", subcategoryId: "sub-1", state: "Seen" },
        ],
      }),
    );

    const result = await submitStudySession(uid, request);

    assert.deepEqual(result.counts, { newCardsStudied: 2 });
    assert.equal(result.durationSeconds, 125);
    assert.deepEqual(result.rates, DEFAULT_XP_RATES);
    assert.deepEqual(await submitStudySession(uid, request), result);
  });

  it("a card Failed on its first exposure still counts as studied, exactly like a Fast session's Seen card", async () => {
    const uid = randomUUID();
    const request = validateSubmitStudySessionRequest(
      rawRatedRequest({ cardResults: [{ cardId: "card-1", subcategoryId: "sub-1", state: "Failed", attemptsUsed: 3, wasPreviouslyMastered: false }] }),
    );

    const result = await submitStudySession(uid, request);

    // No mastery/demastery XP for a card that was never mastered to begin with — but the flat
    // per-card newCards award still applies: the card was studied this session either way.
    assert.equal(result.breakdown.mastered, 0);
    assert.equal(result.breakdown.demastered, 0);
    assert.equal(result.breakdown.newCards, 10);

    const progressDoc = await admin.firestore().doc(`users/${uid}/progress/details/subcategories/sub-1`).get();
    const card = progressDoc.data()?.cards?.["card-1"];
    assert.equal(card.state, "Failed", "the terminal state itself is preserved, not collapsed into Seen");
    assert.ok(card.firstStudiedAt, "first exposure to a card must stamp firstStudiedAt regardless of outcome");
    assert.equal(card.masteredAt, undefined, "a Failed card must never stamp masteredAt");

    const summaryDoc = await admin.firestore().doc(`users/${uid}/progress/summary`).get();
    assert.equal(
      summaryDoc.data()?.subcategories?.["sub-1"]?.studiedCount,
      1,
      "the subcategory's studied-card progress must increase even though the terminal state was Failed — " +
        "the user still saw and studied that card",
    );
    assert.equal(summaryDoc.data()?.subcategories?.["sub-1"]?.masteredCount, 0);
  });

  it("derives the persisted studyDate from startedAtEpochMillis and studyDateUtcOffsetMinutes, not a client-claimed date string (CWE-20 regression)", async () => {
    const uid = randomUUID();
    // Noon UTC on 2026-09-01 shifted by a -14h offset lands on 2026-08-31 local — an offset at the
    // real-world extreme, deliberately chosen so the derived day differs from the UTC-instant day.
    const request = validateSubmitStudySessionRequest(
      rawRatedRequest({ startedAtEpochMillis: DEFAULT_STARTED_AT_EPOCH_MILLIS, studyDateUtcOffsetMinutes: -MAX_UTC_OFFSET_MINUTES }),
    );

    const result = await submitStudySession(uid, request);

    const sessionDoc = await admin.firestore().doc(`users/${uid}/sessions/${request.sessionId}`).get();
    assert.equal(sessionDoc.data()?.studyDate, "2026-08-31", "the offset must shift the derived day, not just be stored inertly");
    // ValidatedSubmitStudySessionRequest itself carries no client-claimed date string any more — the
    // type, not just this assertion, is what closes the original finding.
    assert.equal("studyDate" in request, false);
    assert.equal(result.breakdown.streakBonus, DEFAULT_STREAK_BONUS, "still a fresh account's first-ever submission");
  });
});

describe("submitStudySession — recents/state", () => {

  function recentsStateDoc(uid: string): Promise<FirebaseFirestore.DocumentSnapshot> {
    return admin.firestore().doc(`users/${uid}/recents/state`).get();
  }

  /** Submits a Rated session started [startedAtMinute] minutes after the default start, and returns its id. */
  async function submitRatedSessionAt(uid: string, startedAtMinute: number): Promise<string> {
    const request = validateSubmitStudySessionRequest(
      rawRatedRequest({ startedAtEpochMillis: DEFAULT_STARTED_AT_EPOCH_MILLIS + startedAtMinute * MINUTE_MILLIS }),
    );
    await submitStudySession(uid, request);
    return request.sessionId;
  }

  /** Submits [MAX_RECENT_ENTRIES] sessions one minute apart, starting at minute 10, and returns their ids oldest first. */
  async function fillRecents(uid: string): Promise<string[]> {
    const sessionIds: string[] = [];
    for (let index = 0; index < MAX_RECENT_ENTRIES; index++) {
      sessionIds.push(await submitRatedSessionAt(uid, 10 + index));
    }
    return sessionIds;
  }

  it("a User's first session creates recents/state holding exactly that session's entry", async () => {
    const uid = randomUUID();
    const request = validateSubmitStudySessionRequest(
      rawRatedRequest({
        sourceType: "Quick",
        voiceAnswering: true,
        subcategoryIds: ["sub-1", "sub-2"],
        subcategoryNames: ["Subcategory One", "Subcategory Two"],
        cardResults: [
          { cardId: "card-1", subcategoryId: "sub-1", state: "Mastered", attemptsUsed: 1, wasPreviouslyMastered: false },
          { cardId: "card-2", subcategoryId: "sub-2", state: "Partial", attemptsUsed: 3, wasPreviouslyMastered: false },
        ],
      }),
    );

    const result = await submitStudySession(uid, request);

    const sessionDoc = await admin.firestore().doc(`users/${uid}/sessions/${request.sessionId}`).get();
    const entries = (await recentsStateDoc(uid)).data()?.entries;
    assert.deepEqual(Object.keys(entries), [request.sessionId]);
    const { startTimestamp, ...entryWithoutStart } = entries[request.sessionId];
    assert.ok(startTimestamp instanceof admin.firestore.Timestamp, "startTimestamp is stored as a Firestore Timestamp");
    assert.ok(startTimestamp.isEqual(sessionDoc.data()?.startTimestamp), "the same start as the session document");
    assert.deepEqual(entryWithoutStart, {
      sessionId: request.sessionId,
      durationSeconds: 60,
      studyMode: "Rated",
      voiceAnswering: true,
      sourceType: "Quick",
      categoryId: "cat-1",
      categoryName: "Category One",
      subcategoryIds: ["sub-1", "sub-2"],
      subcategoryNames: ["Subcategory One", "Subcategory Two"],
      cardCount: sessionDoc.data()?.cardCount,
      xpTotal: result.breakdown.xpTotal,
    });
    assert.equal(entryWithoutStart.cardCount, 2);
  });

  it("each new session adds one entry, and a negative xpTotal is stored as is", async () => {
    const uid = randomUUID();
    const firstSessionId = await submitRatedSessionAt(uid, 0);
    // card-1 is Mastered after the first session; failing it now de-masters it. Abandoned, under a
    // minute and on the same day, nothing else earns XP, so the session's total is the penalty alone.
    const request = validateSubmitStudySessionRequest(
      rawRatedRequest({
        startedAtEpochMillis: DEFAULT_STARTED_AT_EPOCH_MILLIS + MINUTE_MILLIS,
        durationSeconds: 0,
        abandoned: true,
        cardResults: [{ cardId: "card-1", subcategoryId: "sub-1", state: "Failed", attemptsUsed: 3, wasPreviouslyMastered: true }],
      }),
    );

    const result = await submitStudySession(uid, request);

    const entries = (await recentsStateDoc(uid)).data()?.entries;
    assert.deepEqual(Object.keys(entries).sort(), [firstSessionId, request.sessionId].sort());
    assert.equal(result.breakdown.xpTotal, DEFAULT_XP_CONFIG.cardDemastered);
    assert.equal(entries[request.sessionId].xpTotal, DEFAULT_XP_CONFIG.cardDemastered);
  });

  it("a Rated session stores sourceType and voiceAnswering on its session document and its entry, never readAloud", async () => {
    const uid = randomUUID();
    const request = validateSubmitStudySessionRequest(rawRatedRequest({ sourceType: "Custom", voiceAnswering: true }));

    await submitStudySession(uid, request);

    const sessionData = (await admin.firestore().doc(`users/${uid}/sessions/${request.sessionId}`).get()).data() ?? {};
    const entry = (await recentsStateDoc(uid)).data()?.entries?.[request.sessionId];
    for (const stored of [sessionData, entry]) {
      assert.equal(stored.sourceType, "Custom");
      assert.equal(stored.voiceAnswering, true);
      assert.equal("readAloud" in stored, false);
    }
  });

  it("a Fast session stores sourceType and readAloud on its session document and its entry, never voiceAnswering", async () => {
    const uid = randomUUID();
    const request = validateSubmitStudySessionRequest(rawFastRequest({ sourceType: "Quick", readAloud: true }));

    await submitStudySession(uid, request);

    const sessionData = (await admin.firestore().doc(`users/${uid}/sessions/${request.sessionId}`).get()).data() ?? {};
    const entry = (await recentsStateDoc(uid)).data()?.entries?.[request.sessionId];
    for (const stored of [sessionData, entry]) {
      assert.equal(stored.sourceType, "Quick");
      assert.equal(stored.readAloud, true);
      assert.equal("voiceAnswering" in stored, false);
    }
    assert.equal(entry.studyMode, "Fast");
  });

  it("a retried submission leaves recents/state untouched", async () => {
    const uid = randomUUID();
    const request = validateSubmitStudySessionRequest(rawRatedRequest());
    await submitStudySession(uid, request);
    const before = await recentsStateDoc(uid);

    await submitStudySession(uid, request);

    const after = await recentsStateDoc(uid);
    assert.ok(after.updateTime?.isEqual(before.updateTime!), "the document must not be rewritten");
    assert.deepEqual(after.data(), before.data());
  });

  it("two concurrent submissions of the same session produce exactly one entry", async () => {
    const uid = randomUUID();
    const request = validateSubmitStudySessionRequest(rawRatedRequest());

    await Promise.all([submitStudySession(uid, request), submitStudySession(uid, request)]);

    assert.deepEqual(Object.keys((await recentsStateDoc(uid)).data()?.entries), [request.sessionId]);
  });

  it(`the ${MAX_RECENT_ENTRIES + 1}th session evicts the oldest by startTimestamp`, async () => {
    const uid = randomUUID();
    const [oldestSessionId, ...keptSessionIds] = await fillRecents(uid);

    const newestSessionId = await submitRatedSessionAt(uid, 100);

    const entries = (await recentsStateDoc(uid)).data()?.entries;
    assert.deepEqual(Object.keys(entries).sort(), [...keptSessionIds, newestSessionId].sort());
    assert.equal(oldestSessionId in entries, false);
  });

  it(`a late-delivered session older than all ${MAX_RECENT_ENTRIES} entries is recorded but leaves recents/state unchanged`, async () => {
    const uid = randomUUID();
    await fillRecents(uid);
    const before = await recentsStateDoc(uid);

    const lateSessionId = await submitRatedSessionAt(uid, 0);

    const after = await recentsStateDoc(uid);
    assert.ok((await admin.firestore().doc(`users/${uid}/sessions/${lateSessionId}`).get()).exists, "the session itself is still recorded");
    assert.ok(after.updateTime?.isEqual(before.updateTime!), "the document must not be rewritten");
    assert.deepEqual(after.data(), before.data());
  });

  it("a late-delivered session in the middle is kept and the oldest evicted", async () => {
    const uid = randomUUID();
    const [oldestSessionId, ...keptSessionIds] = await fillRecents(uid);

    // Starts between the 2nd and 3rd oldest sessions (minutes 11 and 12).
    const lateSessionId = await submitRatedSessionAt(uid, 11.5);

    const entries = (await recentsStateDoc(uid)).data()?.entries;
    assert.deepEqual(Object.keys(entries).sort(), [...keptSessionIds, lateSessionId].sort());
    assert.equal(oldestSessionId in entries, false);
  });

  /** The same three steps, in the same order, as the `submitStudySession` `onCall` handler in index.ts. */
  async function submitAsCaller(callerUid: string, rawRequest: Record<string, unknown>): Promise<void> {
    const request: ValidatedSubmitStudySessionRequest = validateSubmitStudySessionRequest(rawRequest);
    requireOwnerMatchesCaller(callerUid, request);
    await submitStudySession(callerUid, request);
  }

  it("a rejected submission writes no recents/state, while an accepted one by the same caller does", async () => {
    const callerUid = randomUUID();

    await assert.rejects(submitAsCaller(callerUid, rawRatedRequest({ ownerUid: callerUid, sourceType: "Bogus" })), { code: "invalid-argument" });
    await assert.rejects(submitAsCaller(callerUid, rawRatedRequest({ ownerUid: "other-uid" })), { code: "unauthenticated" });
    assert.equal((await recentsStateDoc(callerUid)).exists, false);

    await submitAsCaller(callerUid, rawRatedRequest({ ownerUid: callerUid }));
    assert.equal((await recentsStateDoc(callerUid)).exists, true);
  });
});

describe("submitStudySession — streak and daily goal", () => {
  it("a second same-day submission does not re-fire the streak or goal award", async () => {
    const uid = randomUUID();
    const first = validateSubmitStudySessionRequest(
      rawRatedRequest({
        cardResults: [{ cardId: "card-1", subcategoryId: "sub-1", state: "Mastered", attemptsUsed: 1, wasPreviouslyMastered: false }],
        durationSeconds: 60,
        dailyGoalMinutes: 1,
      }),
    );
    const firstResult = await submitStudySession(uid, first);
    assert.equal(firstResult.breakdown.streakBonus, DEFAULT_STREAK_BONUS, "the account's first-ever submission");
    assert.equal(firstResult.breakdown.dailyGoalBonus, 1000, "1 minute studied meets a 1-minute goal");

    const second = validateSubmitStudySessionRequest(
      rawRatedRequest({
        cardResults: [{ cardId: "card-2", subcategoryId: "sub-1", state: "Mastered", attemptsUsed: 1, wasPreviouslyMastered: false }],
        durationSeconds: 60,
        dailyGoalMinutes: 1,
      }),
    );
    const secondResult = await submitStudySession(uid, second);
    assert.equal(secondResult.breakdown.streakBonus, 0, "same studyDate as the stored lastStudyDate — no second advance");
    assert.equal(secondResult.breakdown.dailyGoalBonus, 0, "goalMetDate already stamped for this studyDate — no second award");

    const scoringDoc = await admin.firestore().doc(`users/${uid}/progress/user-stats`).get();
    assert.equal(scoringDoc.data()?.currentStreak, 1);
    assert.equal(scoringDoc.data()?.goalMetDate, DEFAULT_STUDY_DATE);
  });

  it(`the "today's minutes" query sums multiple same-day sessions before this one, including an abandoned session`, async () => {
    const uid = randomUUID();
    const sessionA = validateSubmitStudySessionRequest(
      rawRatedRequest({
        cardResults: [{ cardId: "card-a", subcategoryId: "sub-1", state: "Failed", attemptsUsed: 1, wasPreviouslyMastered: false }],
        durationSeconds: 300, // 5 minutes
        abandoned: true,
        dailyGoalMinutes: 10,
      }),
    );
    const resultA = await submitStudySession(uid, sessionA);
    assert.equal(resultA.breakdown.dailyGoalBonus, 0, "5 minutes alone does not meet a 10-minute goal");

    const sessionB = validateSubmitStudySessionRequest(
      rawRatedRequest({
        cardResults: [{ cardId: "card-b", subcategoryId: "sub-1", state: "Mastered", attemptsUsed: 1, wasPreviouslyMastered: false }],
        durationSeconds: 300, // 5 more minutes — 10 total with sessionA's, abandoned or not
        dailyGoalMinutes: 10,
      }),
    );
    const resultB = await submitStudySession(uid, sessionB);
    assert.equal(resultB.breakdown.dailyGoalBonus, 1000, "sessionA's 5 abandoned minutes plus this session's own 5 reach the 10-minute goal");

    const sessionBDoc = await admin.firestore().doc(`users/${uid}/sessions/${sessionB.sessionId}`).get();
    assert.equal(sessionBDoc.data()?.studyDate, DEFAULT_STUDY_DATE);

    const scoringDoc = await admin.firestore().doc(`users/${uid}/progress/user-stats`).get();
    assert.equal(scoringDoc.data()?.studiedSecondsOnLastStudyDate, 600, "both same-day sessions' seconds are stored");
  });
});

// The only tests in the suite that write `config/xp`. Every other test above relies on that document
// being absent (scoring with DEFAULT_XP_CONFIG), so each test here deletes it again afterwards.
describe("submitStudySession — server-owned XP configuration", () => {
  // Every award distinct and none equal to its default, so a total can only come out right if every
  // line read this configuration.
  const CUSTOM_XP_CONFIG: XpConfig = {
    newCardStudied: 3,
    cardMastered: 7,
    cardPartial: 11,
    masteryDefended: 13,
    cardDemastered: -17,
    sessionCompleted: 19,
    dailyGoalMet: 29,
    streakPerDay: 31,
    streakMaxPerDay: 37,
    minuteStudied: 23,
    levelCurveBase: 2000,
    levelCurveExponent: 1,
  };

  afterEach(async () => {
    await xpConfigDocRef(admin.firestore()).delete();
  });

  it("scores with the document's values when the document is valid", async () => {
    await xpConfigDocRef(admin.firestore()).set(CUSTOM_XP_CONFIG);
    const request = validateSubmitStudySessionRequest(rawRatedRequest());

    const result = await submitStudySession(randomUUID(), request);

    assert.equal(result.breakdown.newCards, CUSTOM_XP_CONFIG.newCardStudied);
    assert.equal(result.breakdown.mastered, CUSTOM_XP_CONFIG.cardMastered);
    assert.equal(result.breakdown.timeStudied, CUSTOM_XP_CONFIG.minuteStudied);
    assert.equal(result.breakdown.sessionCompletionBonus, CUSTOM_XP_CONFIG.sessionCompleted);
    assert.equal(result.breakdown.streakBonus, CUSTOM_XP_CONFIG.streakPerDay);
    assert.equal(result.breakdown.xpTotal, 3 + 7 + 23 + 19 + 31);
    assert.equal(result.xpForNextLevel, 2000, "the level curve also comes from the document: ceil(2000 * 1^1 / 1000) * 1000");
    assert.equal(result.rates?.cardMastered, CUSTOM_XP_CONFIG.cardMastered, "the returned rates are the document's, not the default");
    assert.equal(result.rates?.minuteStudied, CUSTOM_XP_CONFIG.minuteStudied);
  });

  it("scores with the bundled default when the document is missing, and still succeeds", async () => {
    const request = validateSubmitStudySessionRequest(rawRatedRequest());

    const result = await submitStudySession(randomUUID(), request);

    assert.equal(result.breakdown.xpTotal, 10 + 100 + 10 + 500 + DEFAULT_STREAK_BONUS);
    assert.equal(result.xpForNextLevel, 1000);
  });

  it("scores with the bundled default when the document is invalid, and still succeeds", async () => {
    await xpConfigDocRef(admin.firestore()).set({ ...CUSTOM_XP_CONFIG, cardDemastered: 17 });
    const request = validateSubmitStudySessionRequest(rawRatedRequest());

    const result = await submitStudySession(randomUUID(), request);

    assert.equal(result.breakdown.xpTotal, 10 + 100 + 10 + 500 + DEFAULT_STREAK_BONUS);
    assert.equal(result.xpForNextLevel, 1000);
  });
});

describe("loadXpConfig", () => {
  afterEach(async () => {
    await xpConfigDocRef(admin.firestore()).delete();
  });

  it("returns the document's values with no fallback reason", async () => {
    const custom = { ...DEFAULT_XP_CONFIG, cardMastered: 200 };
    await xpConfigDocRef(admin.firestore()).set(custom);

    assert.deepEqual(await loadXpConfig(admin.firestore()), { config: custom });
  });

  it("falls back to the bundled default with a reason when the document is missing", async () => {
    const loaded = await loadXpConfig(admin.firestore());

    assert.deepEqual(loaded.config, DEFAULT_XP_CONFIG);
    assert.match(loaded.fallbackReason ?? "", /does not exist/);
  });

  it("falls back to the bundled default with a reason when the document is invalid", async () => {
    const { levelCurveBase, ...withoutLevelCurveBase } = DEFAULT_XP_CONFIG;
    await xpConfigDocRef(admin.firestore()).set(withoutLevelCurveBase);

    const loaded = await loadXpConfig(admin.firestore());

    assert.deepEqual(loaded.config, DEFAULT_XP_CONFIG);
    assert.match(loaded.fallbackReason ?? "", /invalid: levelCurveBase/);
  });
});
