# Shared XP scoring cases

XP scoring is implemented twice: authoritatively in the `submitStudySession` Cloud Function
(`functions/src/lib/xpScoring.ts`), and on the client in `calculateSessionXp`
(`:core:domain`, `core.domain.scoring`), which scores the Session Summary's fallback preview and the
projection of Pending Sessions. Both test suites run every case in this directory, so a change to one
implementation that the other does not match fails a test. Add a scenario here, not as a hand-written
test in either suite.

- TypeScript runner: `functions/src/lib/xpScoring.test.ts` (`npm test` in `functions/`).
- Kotlin runner: `XpScoringCasesTest` and `XpConfigTest` in `:core:domain`, loading the
  files through `XpScoringCases` in the module's test fixtures, which carry this directory as
  resources.

## `default-xp-config.json`

Every `XpConfig` field with its bundled default value. Both bundled defaults (`DEFAULT_XP_CONFIG` in
TypeScript, the `XpConfig()` constructor defaults in Kotlin) are tested for equality with this file.
Change a default here and in both implementations together.

## `scoring-cases.json`

```jsonc
{
  "configs": { "<name>": { /* a full XpConfig */ } },
  "cases": [
    {
      "name": "…",               // becomes the test name on both sides; keep it unique
      "kind": "sessionXp",       // or "levelThreshold", "streakAndGoal"
      "skip": { "kotlin": "why" }, // optional: implementations that do not run this case yet
      "input": { … },
      "expected": { … }
    }
  ]
}
```

Common input fields:

- `config`: a key of `configs`, or `"default"` for `default-xp-config.json`.
- `configOverrides` (optional): fields replacing the named configuration's values.
- `priorState` (optional): the scoring state before the calculation; missing fields take the
  starting state (`xp` 0, `level` 1, `xpIntoCurrentLevel` 0, streaks 0, dates `""`,
  `studiedSecondsOnLastStudyDate` 0).

Per kind:

| Kind | Input | Expected |
|---|---|---|
| `sessionXp` | `session` (`studyMode` `Rated`/`Fast`, `durationSeconds`, `abandoned`, `cardResults` of `{ state, wasPreviouslyMastered? }`), `newCardsStudied`, `priorState`, `streakAndGoal` (optional, as below) | `breakdown` (every line and `xpTotal`), `newScoringState`, `levelsCrossed` |
| `levelThreshold` | `level` | `threshold` |
| `streakAndGoal` | `streakAndGoal` (`studyDate`, `dailyGoalMinutes`, `todayTotalSeconds`), `priorState` | `streakBonus`, `dailyGoalBonus`, `currentStreak`, `bestStreak`, `lastStudyDate`, `goalMetDate`, `studiedSecondsOnLastStudyDate` |

A `sessionXp` case without a `streakAndGoal` input scores with no Streak or Daily Goal award (an
empty study date that never advances either). A runner fails on a kind it does not know, unless the case skips that runner.
