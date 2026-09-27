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
    const custom = { ...DEFAULT_XP_CONFIG, cardMastered: 200, levelCurveExponent: 2.0 };

    assert.deepEqual(parseXpConfig(custom), { config: custom });
  });

  it("ignores fields it does not know", () => {
    assert.deepEqual(parseXpConfig({ ...DEFAULT_XP_CONFIG, retiredField: 3 }), { config: DEFAULT_XP_CONFIG });
  });

  it("rejects a document with no data", () => {
    assert.match(problemOf(undefined) ?? "", /no data/);
  });

  it("rejects a missing field", () => {
    const { minuteStudied, ...withoutMinuteStudied } = DEFAULT_XP_CONFIG;

    assert.match(problemOf(withoutMinuteStudied) ?? "", /minuteStudied/);
  });

  it("rejects a non-numeric field", () => {
    assert.match(problemOf({ ...DEFAULT_XP_CONFIG, cardPartial: "25" }) ?? "", /cardPartial/);
  });

  it("rejects a non-finite field", () => {
    assert.match(problemOf({ ...DEFAULT_XP_CONFIG, levelCurveExponent: Number.POSITIVE_INFINITY }) ?? "", /levelCurveExponent/);
    assert.match(problemOf({ ...DEFAULT_XP_CONFIG, newCardStudied: Number.NaN }) ?? "", /newCardStudied/);
  });

  it("rejects a fractional award", () => {
    assert.match(problemOf({ ...DEFAULT_XP_CONFIG, sessionCompleted: 500.5 }) ?? "", /sessionCompleted must be an integer/);
  });

  it("accepts a fractional level-curve value", () => {
    const custom = { ...DEFAULT_XP_CONFIG, levelCurveBase: 1234.5, levelCurveExponent: 1.75 };

    assert.deepEqual(parseXpConfig(custom), { config: custom });
  });

  it("rejects a positive de-mastery penalty and accepts a zero one", () => {
    assert.match(problemOf({ ...DEFAULT_XP_CONFIG, cardDemastered: 80 }) ?? "", /cardDemastered/);
    assert.equal(problemOf({ ...DEFAULT_XP_CONFIG, cardDemastered: 0 }), undefined);
  });

  it("rejects a level curve base that is not positive", () => {
    assert.match(problemOf({ ...DEFAULT_XP_CONFIG, levelCurveBase: 0 }) ?? "", /levelCurveBase/);
    assert.match(problemOf({ ...DEFAULT_XP_CONFIG, levelCurveBase: -1000 }) ?? "", /levelCurveBase/);
  });
});
