# Shared Card Progress merge cases

The Card Progress merge rules are implemented twice: authoritatively in the `submitStudySession`
Cloud Function (`functions/src/lib/cardProgressMerge.ts`), and in `mergeSessionIntoCardProgress`
(`:core:domain`, package `core.domain.scoring`), which the client uses to project Pending Sessions
onto cached Card Progress. Both test suites run every case in this directory, so a change to one
implementation that the other does not match fails a test. Add a scenario here, not as a hand-written
test in either suite.

- TypeScript runner: `functions/src/lib/cardProgressMerge.test.ts` (`npm test` in `functions/`).
- Kotlin runner: `CardProgressMergeTest` in `:core:domain`, loading the file through
  `CardProgressMergeCases` in the module's test fixtures, which carry this directory as resources.

Cases carry no timestamps. An expected update says which stamps the merge sets
(`stampFirstStudied`, `stampMastered`); each suite tests the stamping itself separately.

## `merge-cases.json`

```jsonc
{
  "cases": [
    {
      "name": "…",   // becomes the test name on both sides; keep it unique
      "input": {
        // Subcategory id -> card id -> prior state ("Seen", "Failed", "Partial", "Mastered").
        // A missing Subcategory or card has no Card Progress record.
        "priorCards": { "sub-1": { "card-1": "Mastered" } },
        "session": {
          "studyMode": "Rated",   // or "Fast"
          // Rated entries carry the flag the client sent, which the merge replaces;
          // Fast entries have state "Seen" and no flag.
          "cardResults": [
            { "cardId": "card-1", "subcategoryId": "sub-1", "state": "Failed", "wasPreviouslyMastered": false }
          ]
        }
      },
      "expected": {
        // Subcategory id -> card id -> entry to write. Cards with nothing to write are absent.
        "cardUpdates": { "sub-1": { "card-1": { "state": "Failed", "stampFirstStudied": false, "stampMastered": false } } },
        // Only Subcategories whose Studied or Mastered count changes.
        "summaryDeltas": { "sub-1": { "masteredDelta": -1, "studiedDelta": 0 } },
        "newCardsStudied": 0,
        // Rated cards only: whether the prior Card Progress had the card Mastered.
        "previouslyMastered": { "card-1": true }
      }
    }
  ]
}
```
