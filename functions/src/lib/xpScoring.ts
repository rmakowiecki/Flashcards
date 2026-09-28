/**
 * Server-authoritative XP and level scoring. A TypeScript port of
 * `core/domain/.../model/XpConfig.kt`, `ScoringState.kt`, `XpBreakdown.kt` and
 * `CalculateSessionXpUseCase.kt` — kept field-for-field and rule-for-rule identical to that Kotlin
 * source. Both test suites run the shared scoring cases in `testdata/xp-scoring/` at the repo root, and
 * both bundled defaults are checked against the default configuration file there.
 *
 * Pure: no Firestore, no Admin SDK. [submitStudySession.ts](./submitStudySession.ts) is the only
 * caller, and is where every read/write this calculation needs actually happens.
 */

/** Every tunable scoring number, mirroring `XpConfig.kt`'s field names, defaults and doc comments 1:1. */
export interface XpConfig {
  newCardStudied: number;
  cardMastered: number;
  cardPartial: number;
  masteryDefended: number;
  cardDemastered: number;
  sessionCompleted: number;
  dailyGoalMet: number;
  streakPerDay: number;
  streakMaxPerDay: number;
  minuteStudied: number;
  levelCurveBase: number;
  levelCurveExponent: number;
}

/**
 * The bundled fallback configuration, identical to `XpConfig.kt`'s constructor defaults and to the shared
 * default configuration file. Scoring normally uses the server-owned `config/xp` document (see
 * `xpConfig.ts`); this constant applies only when that document is missing, invalid or unreadable. A
 * session payload's own `xpConfig` snapshot (if the client sends one) is never read for scoring.
 */
export const DEFAULT_XP_CONFIG: XpConfig = {
  newCardStudied: 10,
  cardMastered: 100,
  cardPartial: 25,
  masteryDefended: 50,
  cardDemastered: -80,
  sessionCompleted: 500,
  dailyGoalMet: 1000,
  streakPerDay: 250,
  streakMaxPerDay: 2500,
  minuteStudied: 10,
  levelCurveBase: 1000.0,
  levelCurveExponent: 2.5,
};

/** Every account starts here: level 1, no points into it, before a session has ever committed. */
export const STARTING_LEVEL = 1;

/** Mirrors `ScoringState.kt` field-for-field. */
export interface ScoringState {
  xp: number;
  level: number;
  xpIntoCurrentLevel: number;
  currentStreak: number;
  bestStreak: number;
  lastStudyDate: string;
  goalMetDate: string;
}

export const DEFAULT_SCORING_STATE: ScoringState = {
  xp: 0,
  level: STARTING_LEVEL,
  xpIntoCurrentLevel: 0,
  currentStreak: 0,
  bestStreak: 0,
  lastStudyDate: "",
  goalMetDate: "",
};

const LEVEL_THRESHOLD_ROUNDING_UNIT = 1000;

/** Mirrors `XpConfig.levelThreshold`'s exact formula: `ceil(base * level^exponent / 1000) * 1000`. */
export function levelThreshold(config: XpConfig, level: number): number {
  const rounded = Math.ceil(
    (config.levelCurveBase * Math.pow(level, config.levelCurveExponent)) / LEVEL_THRESHOLD_ROUNDING_UNIT,
  );
  return rounded * LEVEL_THRESHOLD_ROUNDING_UNIT;
}

/** Mirrors `XpBreakdown.kt` field-for-field, [xpTotal] included rather than computed as a getter. */
export interface XpBreakdown {
  newCards: number;
  mastered: number;
  partial: number;
  masteryDefenseBonus: number;
  demastered: number;
  timeStudied: number;
  sessionCompletionBonus: number;
  dailyGoalBonus: number;
  streakBonus: number;
  xpTotal: number;
}

function xpBreakdown(fields: Omit<XpBreakdown, "xpTotal">): XpBreakdown {
  return {
    ...fields,
    xpTotal:
      fields.newCards +
      fields.mastered +
      fields.partial +
      fields.masteryDefenseBonus +
      fields.demastered +
      fields.timeStudied +
      fields.sessionCompletionBonus +
      fields.dailyGoalBonus +
      fields.streakBonus,
  };
}

/** The subset of a card result the calculation needs — deliberately narrower than the full request shape. */
export interface ScoredCardResult {
  state: "Mastered" | "Partial" | "Failed" | "Seen";
  wasPreviouslyMastered?: boolean;
}

export interface ScoredSession {
  studyMode: "Rated" | "Fast";
  durationSeconds: number;
  abandoned: boolean;
  cardResults: ScoredCardResult[];
}

const SECONDS_PER_MINUTE = 60;

interface RatedCardAwards {
  mastered: number;
  partial: number;
  masteryDefenseBonus: number;
  demastered: number;
}

/**
 * Mirrors `CalculateSessionXpUseCase.calculateRatedCardAwards`: a card ending Mastered that was
 * already Mastered earns [XpConfig.masteryDefended] **instead of** [XpConfig.cardMastered] — a
 * smaller, distinct reward, not a bonus stacked on a fresh mastery.
 */
function calculateRatedCardAwards(cardResults: ScoredCardResult[], config: XpConfig): RatedCardAwards {
  let mastered = 0;
  let partial = 0;
  let masteryDefenseBonus = 0;
  let demastered = 0;
  for (const entry of cardResults) {
    switch (entry.state) {
      case "Mastered":
        if (entry.wasPreviouslyMastered) masteryDefenseBonus += config.masteryDefended;
        else mastered += config.cardMastered;
        break;
      case "Partial":
        partial += config.cardPartial;
        break;
      case "Failed":
        if (entry.wasPreviouslyMastered) demastered += config.cardDemastered;
        break;
      case "Seen":
        throw new Error("A Rated card result can never resolve to Seen");
    }
  }
  return { mastered, partial, masteryDefenseBonus, demastered };
}

function calculateBreakdown(
  session: ScoredSession,
  newCardsStudied: number,
  config: XpConfig,
  dailyGoalBonus: number,
  streakBonus: number,
): XpBreakdown {
  const newCards = newCardsStudied * config.newCardStudied;
  const timeStudied = Math.floor(session.durationSeconds / SECONDS_PER_MINUTE) * config.minuteStudied;
  const sessionCompletionBonus = session.abandoned ? 0 : config.sessionCompleted;
  const cardAwards: RatedCardAwards =
    session.studyMode === "Rated"
      ? calculateRatedCardAwards(session.cardResults, config)
      : { mastered: 0, partial: 0, masteryDefenseBonus: 0, demastered: 0 };

  return xpBreakdown({
    newCards,
    mastered: cardAwards.mastered,
    partial: cardAwards.partial,
    masteryDefenseBonus: cardAwards.masteryDefenseBonus,
    demastered: cardAwards.demastered,
    timeStudied,
    sessionCompletionBonus,
    dailyGoalBonus,
    streakBonus,
  });
}

/**
 * The two streak/goal awards: a growing streak bonus for consecutive study days, and a flat
 * once-per-day bonus for meeting the Daily Goal. Pure — no Firestore, no clock; `state`/`input`/
 * `config` are all plain parameters, mirroring [computeSessionXp]'s own purity.
 */
export interface StreakAndGoalInput {
  /**
   * This submission's local calendar day, `yyyy-MM-dd` — server-derived from the session's
   * `startedAtEpochMillis` and the client-reported UTC offset, not trusted directly from the client.
   */
  studyDate: string;
  /** the Daily Goal (minutes/day) in effect when this session ended. */
  dailyGoalMinutes: number;
  /** every session's `durationSeconds` summed for this local day, THIS session's included, floored to whole minutes. */
  todayTotalMinutes: number;
}

export interface StreakAndGoalResult {
  streakBonus: number;
  dailyGoalBonus: number;
  currentStreak: number;
  bestStreak: number;
  lastStudyDate: string;
  goalMetDate: string;
}

/**
 * Both awards are forward-only: a submission whose `studyDate` is not strictly later than the stored
 * date never advances the streak and never regresses `lastStudyDate`/`goalMetDate` — covers same-day
 * resubmission (no double-count) and out-of-order offline delivery (an old session arriving after a
 * later one already committed) alike.
 */
export function computeStreakAndGoalAwards(state: ScoringState, input: StreakAndGoalInput, config: XpConfig): StreakAndGoalResult {
  let currentStreak = state.currentStreak;
  let bestStreak = state.bestStreak;
  let lastStudyDate = state.lastStudyDate;
  let streakBonus = 0;

  if (input.studyDate > state.lastStudyDate) {
    const gapDays = state.lastStudyDate === "" ? null : daysBetween(state.lastStudyDate, input.studyDate);
    currentStreak = gapDays === 1 ? state.currentStreak + 1 : 1;
    bestStreak = Math.max(state.bestStreak, currentStreak);
    lastStudyDate = input.studyDate;
    streakBonus = Math.min(currentStreak * config.streakPerDay, config.streakMaxPerDay);
  }

  let goalMetDate = state.goalMetDate;
  let dailyGoalBonus = 0;
  const alreadyMetToday = input.studyDate === state.goalMetDate;
  if (!alreadyMetToday && input.studyDate >= state.goalMetDate && input.todayTotalMinutes >= input.dailyGoalMinutes) {
    dailyGoalBonus = config.dailyGoalMet;
    goalMetDate = input.studyDate;
  }

  return { streakBonus, dailyGoalBonus, currentStreak, bestStreak, lastStudyDate, goalMetDate };
}

// yyyy-MM-dd strings, both anchored to UTC midnight purely as a calendar-arithmetic device — these
// are calendar days, not instants, so there is no real timezone here to get wrong.
function daysBetween(earlier: string, later: string): number {
  const [ey, em, ed] = earlier.split("-").map(Number);
  const [ly, lm, ld] = later.split("-").map(Number);
  const millisPerDay = 24 * 60 * 60 * 1000;
  return Math.round((Date.UTC(ly, lm - 1, ld) - Date.UTC(ey, em - 1, ed)) / millisPerDay);
}

/**
 * Mirrors `CalculateSessionXpUseCase.applyDelta`: a positive delta climbs [ScoringState.level] one
 * [levelThreshold] at a time, reporting each crossing in ascending order, with no burst awarded for
 * reaching one. A negative delta is clamped to `max(delta, -xpIntoCurrentLevel)` — never enough to
 * push points-into-level below zero or the level below where it already stood.
 */
function applyDelta(state: ScoringState, delta: number, config: XpConfig): { newState: ScoringState; levelsCrossed: number[] } {
  const appliedDelta = delta < 0 ? Math.max(delta, -state.xpIntoCurrentLevel) : delta;

  let xpIntoCurrentLevel = state.xpIntoCurrentLevel + appliedDelta;
  let level = state.level;
  const levelsCrossed: number[] = [];
  while (xpIntoCurrentLevel >= levelThreshold(config, level)) {
    xpIntoCurrentLevel -= levelThreshold(config, level);
    level += 1;
    levelsCrossed.push(level);
  }

  return {
    newState: { ...state, xp: state.xp + appliedDelta, level, xpIntoCurrentLevel },
    levelsCrossed,
  };
}

export interface SessionXpResult {
  breakdown: XpBreakdown;
  newScoringState: ScoringState;
  levelsCrossed: number[];
}

/**
 * Mirrors `CalculateSessionXpUseCase.invoke`: computes the breakdown, then applies its total to
 * `currentState`. The streak/goal result's `currentStreak`/`bestStreak`/`lastStudyDate`/`goalMetDate`
 * are merged onto the returned `newScoringState` after `applyDelta` — independent of whether
 * `applyDelta`'s own XP-delta clamping had anything to do with this session's XP total.
 */
export function computeSessionXp(
  session: ScoredSession,
  newCardsStudied: number,
  currentState: ScoringState,
  config: XpConfig,
  streakAndGoalInput: StreakAndGoalInput,
): SessionXpResult {
  const streakAndGoal = computeStreakAndGoalAwards(currentState, streakAndGoalInput, config);
  const breakdown = calculateBreakdown(session, newCardsStudied, config, streakAndGoal.dailyGoalBonus, streakAndGoal.streakBonus);
  const { newState, levelsCrossed } = applyDelta(currentState, breakdown.xpTotal, config);
  const newScoringState: ScoringState = {
    ...newState,
    currentStreak: streakAndGoal.currentStreak,
    bestStreak: streakAndGoal.bestStreak,
    lastStudyDate: streakAndGoal.lastStudyDate,
    goalMetDate: streakAndGoal.goalMetDate,
  };
  return { breakdown, newScoringState, levelsCrossed };
}
