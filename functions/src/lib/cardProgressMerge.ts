/**
 * The Card Progress merge rules: how one session's card results change a User's packed
 * per-Subcategory Card Progress and the Studied/Mastered counts of their progress summary.
 *
 * A pure function with no Firestore access, so `submitStudySession` applies it inside its
 * transaction and the shared cases in `testdata/card-progress-merge/` exercise it directly. The
 * Kotlin client's `mergeSessionIntoCardProgress` implements the same rules to project Pending
 * Sessions, and runs the same cases.
 *
 * - Fast writes `Seen` only when the card has no record yet.
 * - Rated with no record writes the card's Terminal State.
 * - Rated with a record: `Mastered` masters, `Partial` is mastery-neutral on a Mastered card, and
 *   `Failed` de-masters.
 *
 * Timestamps are not decided here: an update only says which stamps to set, and the caller picks
 * the time.
 */

export type StudyMode = "Rated" | "Fast";
export type CardState = "Mastered" | "Partial" | "Failed" | "Seen";

export interface MergeCardResult {
  cardId: string;
  subcategoryId: string;
  state: CardState;
  /** Present on Rated entries only. */
  wasPreviouslyMastered?: boolean;
}

export interface CardProgressUpdate {
  state: CardState;
  stampFirstStudied: boolean;
  stampMastered: boolean;
}

export interface SummaryDelta {
  masteredDelta: number;
  studiedDelta: number;
}

export interface CardProgressMerge<T extends MergeCardResult> {
  /** Subcategory id to card id to the entry to write. A card with nothing to write is absent. */
  progressWrites: Map<string, Map<string, CardProgressUpdate>>;
  /** Only Subcategories whose counts change. */
  summaryDeltas: Map<string, SummaryDelta>;
  /** Cards with no Card Progress record before this session. */
  newCardsStudied: number;
  /**
   * The input card results, with each Rated entry's `wasPreviouslyMastered` replaced by whether the
   * prior Card Progress had the card Mastered. Fast entries are returned unchanged.
   */
  authoritativeCardResults: T[];
}

function resolveMasteredDelta(entry: MergeCardResult, wasMastered: boolean): number {
  switch (entry.state) {
    case "Mastered":
      return wasMastered ? 0 : 1;
    case "Failed":
      return wasMastered ? -1 : 0;
    default:
      return 0;
  }
}

/** `null` means "write nothing". */
function resolveUpdate(studyMode: StudyMode, entry: MergeCardResult, priorExists: boolean, wasMastered: boolean): CardProgressUpdate | null {
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
      // Rejected at validation time: a Rated card result can never be "Seen".
      throw new Error("unreachable: Rated card result resolved to Seen");
  }
}

/**
 * Merges one session's `cardResults` into `priorCardsBySubcategory` (Subcategory id to card id to
 * the card's prior state; a missing Subcategory or card has no record).
 *
 * Maps, not plain objects, throughout: card and Subcategory ids are client-controlled strings, and a
 * Map sidesteps the `__proto__` hazard of bracket assignment into `{}`.
 */
export function mergeSessionIntoCardProgress<T extends MergeCardResult>(
  priorCardsBySubcategory: Map<string, Map<string, CardState>>,
  studyMode: StudyMode,
  cardResults: T[],
): CardProgressMerge<T> {
  let newCardsStudied = 0;
  const progressWrites = new Map<string, Map<string, CardProgressUpdate>>();
  const summaryDeltas = new Map<string, SummaryDelta>();
  const touchedSubcategoryIds = [...new Set(cardResults.map((entry) => entry.subcategoryId))];

  for (const subcategoryId of touchedSubcategoryIds) {
    const priorCards = priorCardsBySubcategory.get(subcategoryId) ?? new Map<string, CardState>();
    const cardUpdates = new Map<string, CardProgressUpdate>();
    let masteredDelta = 0;
    let studiedDelta = 0;

    for (const entry of cardResults.filter((cardResult) => cardResult.subcategoryId === subcategoryId)) {
      const priorState = priorCards.get(entry.cardId);
      const priorExists = priorState !== undefined;
      const wasMastered = priorState === "Mastered";
      if (!priorExists) {
        newCardsStudied++;
        studiedDelta++;
      }
      masteredDelta += resolveMasteredDelta(entry, wasMastered);

      const update = resolveUpdate(studyMode, entry, priorExists, wasMastered);
      if (update) cardUpdates.set(entry.cardId, update);
    }

    if (cardUpdates.size > 0) progressWrites.set(subcategoryId, cardUpdates);
    if (masteredDelta !== 0 || studiedDelta !== 0) summaryDeltas.set(subcategoryId, { masteredDelta, studiedDelta });
  }

  const authoritativeCardResults = cardResults.map((entry) =>
    entry.wasPreviouslyMastered === undefined
      ? entry
      : { ...entry, wasPreviouslyMastered: priorCardsBySubcategory.get(entry.subcategoryId)?.get(entry.cardId) === "Mastered" },
  );

  return { progressWrites, summaryDeltas, newCardsStudied, authoritativeCardResults };
}
