// Runs the XP scoring cases shared with the Kotlin client (`testdata/xp-scoring/` at the repo root)
// against this module. The Kotlin `CalculateSessionXpUseCase` runs the same file, so the two
// implementations are checked against one set of expectations. Add new scenarios to that file, not here.
import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { describe, it } from "node:test";
import {
  DEFAULT_SCORING_STATE,
  DEFAULT_XP_CONFIG,
  ScoredSession,
  ScoringState,
  StreakAndGoalInput,
  XpConfig,
  computeSessionXp,
  computeStreakAndGoalAwards,
  levelThreshold,
} from "./xpScoring";

const RUNNER = "typescript";

interface ScoringCaseInput {
  config: string;
  configOverrides?: Partial<XpConfig>;
  priorState?: Partial<ScoringState>;
  session?: ScoredSession;
  newCardsStudied?: number;
  level?: number;
  streakAndGoal?: StreakAndGoalInput;
}

interface ScoringCase {
  name: string;
  kind: string;
  skip?: Record<string, string>;
  input: ScoringCaseInput;
  expected: unknown;
}

interface ScoringCaseFile {
  configs: Record<string, XpConfig>;
  cases: ScoringCase[];
}

// Scores without either award: an empty study date is never later than a stored one.
const NO_STREAK_OR_GOAL: StreakAndGoalInput = { studyDate: "", dailyGoalMinutes: 0, todayTotalMinutes: 0 };

// Resolved from the functions package root, found by walking up from this file, so the path does not
// depend on where the compiled output lands.
function testdataDir(): string {
  let directory = __dirname;
  while (!existsSync(join(directory, "package.json"))) {
    const parent = dirname(directory);
    if (parent === directory) throw new Error("functions package root not found");
    directory = parent;
  }
  return join(directory, "..", "testdata", "xp-scoring");
}

function readJson<T>(fileName: string): T {
  return JSON.parse(readFileSync(join(testdataDir(), fileName), "utf8")) as T;
}

const defaultConfig = readJson<XpConfig>("default-xp-config.json");
const scoringCaseFile = readJson<ScoringCaseFile>("scoring-cases.json");

function resolveConfig(input: ScoringCaseInput): XpConfig {
  const base = input.config === "default" ? defaultConfig : scoringCaseFile.configs[input.config];
  if (base === undefined) throw new Error(`unknown config "${input.config}"`);
  return { ...base, ...input.configOverrides };
}

function priorState(input: ScoringCaseInput): ScoringState {
  return { ...DEFAULT_SCORING_STATE, ...input.priorState };
}

function run(scoringCase: ScoringCase): unknown {
  const { input } = scoringCase;
  const config = resolveConfig(input);
  switch (scoringCase.kind) {
    case "sessionXp":
      return computeSessionXp(input.session!, input.newCardsStudied ?? 0, priorState(input), config, NO_STREAK_OR_GOAL);
    case "levelThreshold":
      return { threshold: levelThreshold(config, input.level!) };
    case "streakAndGoal":
      return computeStreakAndGoalAwards(priorState(input), input.streakAndGoal!, config);
    default:
      throw new Error(`unknown case kind "${scoringCase.kind}"`);
  }
}

describe("XP scoring cases shared with the Kotlin client", () => {
  for (const scoringCase of scoringCaseFile.cases) {
    it(scoringCase.name, { skip: scoringCase.skip?.[RUNNER] }, () => {
      assert.deepEqual(run(scoringCase), scoringCase.expected);
    });
  }
});

describe("DEFAULT_XP_CONFIG", () => {
  it("matches the shared default configuration file", () => {
    assert.deepEqual(DEFAULT_XP_CONFIG, defaultConfig);
  });
});
