import { DEFAULT_XP_CONFIG, levelThreshold, STARTING_LEVEL, XpConfig } from "./xpScoring";

/**
 * The server-owned XP configuration: one Firestore document, `config/xp`, holding every `XpConfig`
 * field. `submitStudySession` scores every submission with it, and the Android client fetches the same
 * document to preview a session's XP. An admin changes XP rates by editing that document, with no app
 * release and no function deploy. `scripts/seed/seed_xp_config.py` writes it from the shared default
 * configuration file.
 *
 * `DEFAULT_XP_CONFIG` is only a fallback here: a missing, invalid or unreadable document never rejects
 * a submission, it scores with the bundled default instead and logs why.
 */

// Firestore segments, named per this repo's Firestore-constants convention.
const XP_CONFIG_COLLECTION = "config";
const XP_CONFIG_DOCUMENT = "xp";

// The Android client holds award fields as Kotlin `Int`, so an award outside this range is one it cannot load.
const INT_MIN = -2_147_483_648;
const INT_MAX = 2_147_483_647;

/** Award fields: whole points, so every one must be an integer. */
const INTEGER_FIELDS = [
  "newCardStudied",
  "cardMastered",
  "cardPartial",
  "masteryDefended",
  "cardDemastered",
  "sessionCompleted",
  "dailyGoalMet",
  "streakPerDay",
  "streakMaxPerDay",
  "minuteStudied",
] as const satisfies readonly (keyof XpConfig)[];

/** Level-curve fields: any finite number. */
const CURVE_FIELDS = ["levelCurveBase", "levelCurveExponent"] as const satisfies readonly (keyof XpConfig)[];

export type XpConfigParseResult = { config: XpConfig } | { problem: string };

/**
 * Validates a configuration document's data. Every field must be present and a finite number, every
 * award field an integer within the Android client's `Int` range, the de-mastery penalty at most zero,
 * the level curve's base positive (a zero or negative base makes every level threshold zero, and the
 * level-up loop would never end), its exponent zero or positive (a negative exponent makes each level
 * cheaper than the one before it), and the starting level's threshold above zero (a base so small it
 * rounds to a zero threshold ends the level-up loop no better). With a nonnegative exponent, every later
 * level's threshold is at least the starting level's.
 * Unknown fields are ignored.
 */
export function parseXpConfig(data: unknown): XpConfigParseResult {
  if (typeof data !== "object" || data === null) return { problem: "the document has no data" };
  const fields = data as Record<string, unknown>;
  const config: Partial<XpConfig> = {};

  for (const field of [...INTEGER_FIELDS, ...CURVE_FIELDS]) {
    const value = fields[field];
    if (typeof value !== "number" || !Number.isFinite(value)) return { problem: `${field} must be a finite number, got ${String(value)}` };
    config[field] = value;
  }
  for (const field of INTEGER_FIELDS) {
    if (!Number.isInteger(config[field])) return { problem: `${field} must be an integer, got ${config[field]}` };
    const award = config[field] as number;
    if (award < INT_MIN || award > INT_MAX) return { problem: `${field} must be between ${INT_MIN} and ${INT_MAX}, got ${award}` };
  }
  const complete = config as XpConfig;
  if (complete.cardDemastered > 0) return { problem: `cardDemastered must be zero or negative, got ${complete.cardDemastered}` };
  if (complete.levelCurveBase <= 0) return { problem: `levelCurveBase must be positive, got ${complete.levelCurveBase}` };
  if (complete.levelCurveExponent < 0) return { problem: `levelCurveExponent must be zero or positive, got ${complete.levelCurveExponent}` };
  if (levelThreshold(complete, STARTING_LEVEL) <= 0) return { problem: `levelCurveBase ${complete.levelCurveBase} is too small: the starting level's threshold rounds to zero` };
  return { config: complete };
}

export interface LoadedXpConfig {
  config: XpConfig;
  /** Why `config` is the bundled default instead of the document's values; absent when the document was used. */
  fallbackReason?: string;
}

/**
 * Reads `config/xp` and returns its validated values, or `DEFAULT_XP_CONFIG` with the reason when the
 * document is missing, invalid, or the read itself fails. Never throws.
 */
export async function loadXpConfig(db: FirebaseFirestore.Firestore): Promise<LoadedXpConfig> {
  let snapshot: FirebaseFirestore.DocumentSnapshot;
  try {
    snapshot = await xpConfigDocRef(db).get();
  } catch (error) {
    return { config: DEFAULT_XP_CONFIG, fallbackReason: `reading the document failed: ${String(error)}` };
  }
  if (!snapshot.exists) return { config: DEFAULT_XP_CONFIG, fallbackReason: "the document does not exist" };

  const parsed = parseXpConfig(snapshot.data());
  if ("problem" in parsed) return { config: DEFAULT_XP_CONFIG, fallbackReason: `the document is invalid: ${parsed.problem}` };
  return { config: parsed.config };
}

export function xpConfigDocRef(db: FirebaseFirestore.Firestore): FirebaseFirestore.DocumentReference {
  return db.collection(XP_CONFIG_COLLECTION).doc(XP_CONFIG_DOCUMENT);
}
