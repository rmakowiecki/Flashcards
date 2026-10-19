// Validation of the server-owned XP configuration document. Pure: no Firestore. Reading the document
// and scoring with it run against the emulator in submitStudySession.test.ts, the one test file that
// writes `config/xp`, so no two test processes race on that shared document.
import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseXpConfig } from "./xpConfig";
import { DEFAULT_XP_CONFIG } from "./xpScoring";

function problemOf(data: unknown): string | undefined {
  const parsed = parseXpConfig(data);
  return "problem" in parsed ? parsed.problem : undefined;
}

describe("parseXpConfig", () => {
  it("accepts a complete configuration and returns its values", () => {
    const custom = { ...DEFAULT_XP_CONFIG, cardMastered: 200, levelCostSteps: [100, 300], levelsPerCostStep: 2 };

    assert.deepEqual(parseXpConfig(custom), { config: custom });
  });

  it("ignores fields it does not know", () => {
    assert.deepEqual(parseXpConfig({ ...DEFAULT_XP_CONFIG, retiredField: 3 }), { config: DEFAULT_XP_CONFIG });
  });

  it("rejects a document with no data", () => {
    assert.match(problemOf(undefined) ?? "", /no data/);
  });

  it("rejects a missing field", () => {
    const { cardPartial, ...withoutCardPartial } = DEFAULT_XP_CONFIG;

    assert.match(problemOf(withoutCardPartial) ?? "", /cardPartial/);
  });

  it("rejects a non-numeric field", () => {
    assert.match(problemOf({ ...DEFAULT_XP_CONFIG, cardPartial: "25" }) ?? "", /cardPartial/);
  });

  it("rejects a non-finite field", () => {
    assert.match(problemOf({ ...DEFAULT_XP_CONFIG, levelBaseCost: Number.POSITIVE_INFINITY }) ?? "", /levelBaseCost/);
    assert.match(problemOf({ ...DEFAULT_XP_CONFIG, newCardStudied: Number.NaN }) ?? "", /newCardStudied/);
  });

  it("rejects a fractional award", () => {
    assert.match(problemOf({ ...DEFAULT_XP_CONFIG, sessionCompleted: 100.5 }) ?? "", /sessionCompleted must be an integer/);
  });

  it("rejects a fractional level-curve value", () => {
    assert.match(problemOf({ ...DEFAULT_XP_CONFIG, levelBaseCost: 1000.5 }) ?? "", /levelBaseCost must be an integer/);
    assert.match(problemOf({ ...DEFAULT_XP_CONFIG, levelsPerCostStep: 2.5 }) ?? "", /levelsPerCostStep must be an integer/);
    assert.match(problemOf({ ...DEFAULT_XP_CONFIG, levelCostSteps: [250, 500.5] }) ?? "", /levelCostSteps/);
  });

  it("rejects a positive de-mastery penalty and accepts a zero one", () => {
    assert.match(problemOf({ ...DEFAULT_XP_CONFIG, cardDemastered: 80 }) ?? "", /cardDemastered/);
    assert.equal(problemOf({ ...DEFAULT_XP_CONFIG, cardDemastered: 0 }), undefined);
  });

  it("rejects an award outside the Android Int range and accepts its bounds", () => {
    assert.match(problemOf({ ...DEFAULT_XP_CONFIG, cardMastered: 2_147_483_648 }) ?? "", /cardMastered/);
    assert.match(problemOf({ ...DEFAULT_XP_CONFIG, cardDemastered: -2_147_483_649 }) ?? "", /cardDemastered/);
    assert.equal(problemOf({ ...DEFAULT_XP_CONFIG, cardMastered: 2_147_483_647, cardDemastered: -2_147_483_648 }), undefined);
  });

  it("rejects a level base cost that is not positive", () => {
    assert.match(problemOf({ ...DEFAULT_XP_CONFIG, levelBaseCost: 0 }) ?? "", /levelBaseCost/);
    assert.match(problemOf({ ...DEFAULT_XP_CONFIG, levelBaseCost: -1000 }) ?? "", /levelBaseCost/);
  });

  it("rejects a levels-per-step count that is not positive", () => {
    assert.match(problemOf({ ...DEFAULT_XP_CONFIG, levelsPerCostStep: 0 }) ?? "", /levelsPerCostStep/);
    assert.match(problemOf({ ...DEFAULT_XP_CONFIG, levelsPerCostStep: -5 }) ?? "", /levelsPerCostStep/);
  });

  it("rejects a step list that is missing, empty or not a list", () => {
    const { levelCostSteps, ...withoutSteps } = DEFAULT_XP_CONFIG;

    assert.match(problemOf(withoutSteps) ?? "", /levelCostSteps/);
    assert.match(problemOf({ ...DEFAULT_XP_CONFIG, levelCostSteps: [] }) ?? "", /levelCostSteps/);
    assert.match(problemOf({ ...DEFAULT_XP_CONFIG, levelCostSteps: 250 }) ?? "", /levelCostSteps/);
  });

  it("rejects a step that is not a positive integer and accepts a one-entry list", () => {
    assert.match(problemOf({ ...DEFAULT_XP_CONFIG, levelCostSteps: [250, 0] }) ?? "", /levelCostSteps/);
    assert.match(problemOf({ ...DEFAULT_XP_CONFIG, levelCostSteps: [250, -500] }) ?? "", /levelCostSteps/);
    assert.match(problemOf({ ...DEFAULT_XP_CONFIG, levelCostSteps: [250, "500"] }) ?? "", /levelCostSteps/);
    assert.equal(problemOf({ ...DEFAULT_XP_CONFIG, levelCostSteps: [250] }), undefined);
  });
});
