// Runs the Card Progress merge cases shared with the Kotlin client (`testdata/card-progress-merge/` at
// the repo root) against this module. The Kotlin `mergeSessionIntoCardProgress` runs the same file, so
// the two implementations are checked against one set of expectations. Add new scenarios to that
// file, not here.
import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { describe, it } from "node:test";
import { CardProgressUpdate, CardState, MergeCardResult, StudyMode, SummaryDelta, mergeSessionIntoCardProgress } from "./cardProgressMerge";

interface MergeCase {
  name: string;
  input: {
    priorCards: Record<string, Record<string, CardState>>;
    session: { studyMode: StudyMode; cardResults: MergeCardResult[] };
  };
  expected: {
    cardUpdates: Record<string, Record<string, CardProgressUpdate>>;
    summaryDeltas: Record<string, SummaryDelta>;
    newCardsStudied: number;
    previouslyMastered: Record<string, boolean>;
  };
}

// Resolved from the functions package root, found by walking up from this file, so the path does not
// depend on where the compiled output lands.
function testdataDir(): string {
  let directory = __dirname;
  while (!existsSync(join(directory, "package.json"))) {
    const parent = dirname(directory);
    if (parent === directory) throw new Error("functions package root not found");
    directory = parent;
  }
  return join(directory, "..", "testdata", "card-progress-merge");
}

const mergeCases = (JSON.parse(readFileSync(join(testdataDir(), "merge-cases.json"), "utf8")) as { cases: MergeCase[] }).cases;

function toNestedMap(priorCards: Record<string, Record<string, CardState>>): Map<string, Map<string, CardState>> {
  return new Map(Object.entries(priorCards).map(([subcategoryId, cards]) => [subcategoryId, new Map(Object.entries(cards))]));
}

function run(mergeCase: MergeCase): MergeCase["expected"] {
  const { priorCards, session } = mergeCase.input;
  const merge = mergeSessionIntoCardProgress(toNestedMap(priorCards), session.studyMode, session.cardResults);
  return {
    cardUpdates: Object.fromEntries([...merge.progressWrites].map(([subcategoryId, cards]) => [subcategoryId, Object.fromEntries(cards)])),
    summaryDeltas: Object.fromEntries(merge.summaryDeltas),
    newCardsStudied: merge.newCardsStudied,
    previouslyMastered: Object.fromEntries(
      merge.authoritativeCardResults
        .filter((entry) => entry.wasPreviouslyMastered !== undefined)
        .map((entry) => [entry.cardId, entry.wasPreviouslyMastered as boolean]),
    ),
  };
}

describe("Card Progress merge cases shared with the Kotlin client", () => {
  for (const mergeCase of mergeCases) {
    it(mergeCase.name, () => {
      assert.deepEqual(run(mergeCase), mergeCase.expected);
    });
  }
});
