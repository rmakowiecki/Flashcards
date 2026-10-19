# Firestore Schema

The current Firestore data schema, collection by collection: every path, every field, who writes it and who may read it. This is the single source of truth for persisted shapes; ADRs record why a shape was chosen but are not kept in sync with every field.

Fields marked `?` may be absent. Unless a section says otherwise, the owning User may read their own documents under `users/{uid}`, and nothing under `users/{uid}` is readable by anyone else.

## Taxonomy: categories and subcategories

```
categories/{categoryId}                         → { name, order, subcategoryCount,
                                                    featuredSubcategoryNames[], color?, iconSvg? }
subcategories/{categoryId-subSlug}              → { name, nameLower, categoryId, categoryName,
                                                    order, cardCount }
subcategories/{categoryId-subSlug}/shards/{n}   → { flashcards: { "<cardId>": {
                                                      id, question, answer, tags[], difficulty, createdAt,
                                                      questionCode[]?, answerCode[]?,
                                                      questionSpoken?, answerSpoken?,
                                                      extendedContext? }, ... } }
```

- **Two levels, two top-level collections.** Subcategory ids are namespaced `{categoryId}-{subSlug}` (for example `android-testing`), so they are unique across Categories. See [ADR-0001](../adr/0001-flat-two-level-taxonomy.md) and [ADR-0007](../adr/0007-firestore-collection-structure.md).
- **Written only by the seed pipeline** (Admin SDK). Any signed-in user, a Guest included, may read all three.
- `order` ranks Categories by card volume and Subcategories by card volume within their Category. `featuredSubcategoryNames` holds the display names of the Category's top-ranked Subcategories, recomputed on every seed run; it backs the topic summary line on the Browse screen ([category-search.md](category-search.md)). `nameLower` backs the Subcategory name prefix search and is never shown.
- **`color` and `iconSvg` may be absent**, since the seed pipeline can create a Category before either is curated. `iconSvg` is inline SVG text, not a URL. See [category-icon-color.md](category-icon-color.md).
- **Flashcards are packed into shard documents** under their Subcategory instead of one document per card, each shard filled up to a byte budget ([ADR-0037](../adr/0037-flashcard-content-sharded-by-byte-budget.md)). Reading a Subcategory's Flashcards is one unfiltered read of its `shards` collection. There is no separate `cards` collection.
- **`flashcards` is a map keyed by card id, not an array**, so one card's field can be patched in place without rewriting the rest of its shard ([ADR-0037](../adr/0037-flashcard-content-sharded-by-byte-budget.md)). Each entry also stores its own `id`, which must match its key. A Flashcard's Subcategory is not stored on the card; it is the shard's parent document.
- `difficulty` is the Flashcard's **Difficulty**; a global card without it is dropped on read and never reaches the domain. `extendedContext` is the card's **Extended Context**. Both are defined in `CONTEXT.md`; see also [ADR-0010](../adr/0010-difficulty-field-design.md).
- **Tags are flat, untyped strings** on each card. There is no `tags` collection ([ADR-0006](../adr/0006-flat-denormalized-tags.md)).
- `questionCode` and `answerCode` are lists of code blocks, each `{ language, code }`. `questionSpoken` and `answerSpoken` are the text read aloud in place of `question` and `answer` when present. `createdAt` is an ISO-8601 string.

## Onboarding's curated Subcategory picker

```
onboarding/subcategories                        → { subcategories: { "<subcategoryId>": {
                                                      order, categoryId, categoryName,
                                                      subcategoryId, subcategoryName, iconSvg
                                                    }, ... } }
```

- **One document holding an admin-picked subset of Subcategories**, so onboarding's Favorites step costs exactly one read. Each entry is trimmed to what onboarding shows, and carries its parent Category's `iconSvg`, so onboarding never reads `categories`.
- **Readable by anyone, signed in or not**, because onboarding runs before Login ([onboarding-flow.md](onboarding-flow.md)). It is a separate document rather than a looser rule on `subcategories`, so the real taxonomy stays readable only by signed-in users.
- Written only by the seed pipeline, which replaces it whole on every run. It takes no part in the cache freshness signal below: it is small, changes rarely, and the signal itself needs a signed-in reader.

## Cache freshness signal

```
meta/seed                                       → { value }
```

- `value` is an integer that every seed run increments as its last write. Clients compare it with the value their cached Flashcards were read under ([flashcard-cache.md](flashcard-cache.md), [ADR-0039](../adr/0039-seed-versioned-flashcard-cache-invalidation.md)). Readable by any signed-in user; written only by the seed pipeline.

## Entitlement

```
users/{uid}/entitlement/premium                 → { isPremium, source, grantedAt }
```

- **The User's Premium record.** `isPremium` is the only field any reader acts on. `source` says who wrote it (`auto-grant`, or `admin` for a console edit) and `grantedAt` when the auto-grant created it; both are for admins only.
- **Owner-read-only.** The User may read their own record, so the app can follow Premium as it changes ([ADR-0068](../adr/0068-client-observes-premium-from-the-entitlement-record.md)). No client may write it. The paid Cloud Functions read it on every call ([voice-answering.md](voice-answering.md#entitlement)).
- **Written only with the Admin SDK.** A function creates it when an Auth user is created, Guests included, and never overwrites an existing record ([ADR-0069](../adr/0069-premium-auto-granted-on-auth-user-creation.md)). An admin grants or revokes Premium by editing it in the console. Revoking sets `isPremium` to false and keeps the record, so the auto-grant cannot restore it.
- The `users/{uid}` document itself has no fields and no client access. Every per-User document lives in a subcollection below it: identity and entitlement are kept apart from the scoring state in `progress`, which is the only state a session commit changes.

## Favorites

```
users/{uid}/favorites/state                     → { categories:    { "<categoryId>":    Timestamp | null, ... },
                                                    subcategories: { "<subcategoryId>": Timestamp | null, ... } }
```

- **One document per User** holds both kinds of **Favorite**, as two independent maps from id to the server time it was favorited. Reading every Favorite, or changing any number of them, costs one read or one write. The two maps never cascade into each other.
- **Unfavoriting writes `null` instead of deleting the key.** The Firestore SDK's offline view loses a pending nested-key delete once a later write to the same document is queued, which made an offline unfavorite come back. An id is a Favorite only when its value is a timestamp; a `null` entry is ignored. The number of `null` entries is bounded by the number of Categories and Subcategories.
- One document keeps offline multi-adds correct: every change targets the same document, so the SDK replays them in order, where separate per-item documents could fail or race independently. Onboarding's Favorites step relies on this.
- **Written directly by the client.** Favorites are not sensitive, so no Cloud Function sits in front of them; the owner may read and write.

## Study sessions

```
users/{uid}/sessions/{sessionId}                → {
    sessionId, startTimestamp, durationSeconds, studyDate,
    studyMode: "Rated" | "Fast", isAbandoned,
    sourceType: "SingleSubcategory" | "Quick" | "Custom",
    voiceAnswering?,        // Rated only
    readAloud?,             // Fast only
    categoryId, categoryName, subcategoryIds[], subcategoryNames[],
    cardCount, newCardsStudied,
    cardsMastered?, cardsPartial?, cardsDefended?, cardsDemastered?,   // Rated only
    cardResults: { "<cardId>": {
        subcategoryId, state,
        attemptsUsed?, wasPreviouslyMastered?                           // Rated only
    }, ... },
    newCards, mastered, partial, masteryDefenseBonus, demastered,
    timeStudied, sessionCompletionBonus, dailyGoalBonus, streakBonus, xpTotal,
    levelBefore, xpIntoCurrentLevelBefore, xpForNextLevelBefore,
    levelAfter, xpIntoCurrentLevelAfter, xpForNextLevelAfter, levelsCrossed[],
    currentStreakAfter,
    xpRates: { newCardStudied, cardMastered, cardPartial, masteryDefended,
               cardDemastered, minuteStudied, sessionCompleted }
}
```

- **One document per Study Session, for both Study Modes**: the session's aggregates, its denormalized Category and Subcategory names, and its **Flashcard Results** embedded as `cardResults`. The results are embedded because no reader needs a session without them, so a subcollection would only add a write per card. Home does not read this collection; it reads [`recents/state`](#recents).
- **The shape follows `studyMode`.** Rated-only fields are absent from a Fast document, not written as `0` or `false`: the four Terminal State counters, and `attemptsUsed` and `wasPreviouslyMastered` on each result. Delivery follows the same rule: `voiceAnswering` exists only on a Rated document and `readAloud` only on a Fast one.
- `cardsMastered` counts every result ending Mastered, defended Flashcards included; `cardsDefended` counts the ones among them that were already Mastered, and `cardsDemastered` the already-Mastered ones ending Failed.
- `sourceType` is the Study Creation entry point that started the session, not its Subcategory count. A result's `state` is that session's own outcome, not the card's stored **Card Progress**.
- `studyDate` is the session's local calendar day (`yyyy-MM-dd`), derived by the server from the session's start time and the device's UTC offset, which the client submits with it ([ADR-0048](../adr/0048-streak-and-daily-goal-ride-the-session-payload.md)). The server sums the day's sessions by an exact match on it.
- **Written only by the `submitStudySession` Cloud Function**, in one transaction with every progress, scoring and Recents write the session causes ([ADR-0049](../adr/0049-server-authoritative-session-commit.md)). The owner may read; no client write of any kind is allowed.
- **The scoring fields let a retried submission answer from this document alone.** The XP lines (`newCards` through `xpTotal`) are the session's XP breakdown. `xpRates` holds the per-line rates it was scored with, so a later change to the XP configuration does not change the answer. The `…Before` fields hold the User's Level position just before the session, the `…After` fields and `levelsCrossed` the position just after, and `currentStreakAfter` the Streak just after. A retry of a session that already has a document reads everything from it and writes nothing.

## Recents

```
users/{uid}/recents/state                       → { entries: { "<sessionId>": {
                                                      sessionId, startTimestamp, durationSeconds,
                                                      studyMode, voiceAnswering?, readAloud?, sourceType,
                                                      categoryId, categoryName,
                                                      subcategoryIds[], subcategoryNames[],
                                                      cardCount, xpTotal
                                                    }, ... } }
```

- **One document per User holding their latest 15 sessions**, so Home's Recently studied list costs one read on attach and one per change, instead of a query over `sessions` ([ADR-0057](../adr/0057-recents-state-projection.md)).
- **Each entry repeats its session document's fields and values, names included.** Names never change, so a **Recent** shows and replays without reading the taxonomy. `subcategoryNames` is parallel to `subcategoryIds`; `xpTotal` may be negative.
- **Keyed by `sessionId`, so a retried submission changes nothing**: a session already present leaves the document untouched. A new entry is added, then the map is trimmed to the 15 newest by `startTimestamp`, with equal timestamps evicting the lower `sessionId` first. A late-delivered session therefore never pushes out a newer one, and one older than every kept entry is not written at all.
- **Written only by `submitStudySession`**, inside the session's own transaction. The owner may read.

## Card Progress

```
users/{uid}/progress/details/subcategories/{subcategoryId} → { categoryId,
                                                    cards: { "<cardId>": {
                                                      state: "Seen" | "Failed" | "Partial" | "Mastered",
                                                      firstStudiedAt, masteredAt?
                                                    }, ... } }
```

- **One packed document per User per Subcategory**, holding the User's **Card Progress** for every Flashcard in it they have studied ([ADR-0016](../adr/0016-card-progress-model.md)). A key exists exactly when the Flashcard is **Studied**; its `state` is `Mastered` exactly when it is in **Persistent Mastery**. Packing makes a session's progress cost one write per Subcategory and any screen's progress one read. Private Flashcards never get an entry.
- `firstStudiedAt` and `masteredAt` are server timestamps. How a session's results merge into these entries is defined under **Card Progress** in `CONTEXT.md` and in [ADR-0016](../adr/0016-card-progress-model.md).
- `progress/details` is a fixed document with no fields of its own. It exists only to host this subcollection, because Firestore paths alternate collection and document.
- **Written only by `submitStudySession`**, merged into the existing document. The owner may read.

## Progress summary

```
users/{uid}/progress/summary                    → { subcategories: { "<subcategoryId>": {
                                                      masteredCount, studiedCount
                                                    }, ... } }
```

- **One document per User holding every Subcategory's mastered and studied counts**, so a screen draws every progress ring for a Category from one read. The denominator is the Subcategory's `cardCount` from the taxonomy, which excludes Private Flashcards, so any card count shown beside a ring must exclude them too.
- **Written only by `submitStudySession`**, as increments in the session's transaction. The owner may read.

## Scoring state

```
users/{uid}/progress/user-stats                 → { xp, level, xpIntoCurrentLevel,
                                                    currentStreak, bestStreak,
                                                    lastStudyDate, goalMetDate,
                                                    studiedSecondsOnLastStudyDate,
                                                    appliedSessionIds[] }
```

- **The User's XP, Level, Streak and Daily Goal state.** `progress` holds the User's singleton documents, `summary` and `user-stats`, next to the `details` anchor, so one security rule covers them all.
- **Written only by `submitStudySession`**, overwritten whole in the session's transaction. The owner may read. The update rules for each field are in [session-submission.md](session-submission.md).
- `lastStudyDate` and `goalMetDate` are local calendar days (`yyyy-MM-dd`) and only ever move forward ([ADR-0048](../adr/0048-streak-and-daily-goal-ride-the-session-payload.md)).
- `studiedSecondsOnLastStudyDate` is the total duration of every recorded session on `lastStudyDate`. A submission sets it to that day's total when its `studyDate` is not earlier than `lastStudyDate`, and leaves it unchanged otherwise. A missing field reads as `0`. The client uses it as the day's starting point when it previews the Daily Goal for a **Pending Session** ([ADR-0055](../adr/0055-pending-sessions-and-local-progress-projection.md)).
- `appliedSessionIds` lists the ids of the latest sessions applied to this document, oldest first and capped in length. Each new session's id is appended in the same write as the scoring fields. It is not scoring state: the device reads it to skip the Pending Sessions this document already includes ([ADR-0061](../adr/0061-delivered-sessions-applied-once-in-the-projection.md)). A missing field reads as empty.

## XP configuration

```
config/xp                                       → { newCardStudied, cardMastered, cardPartial,
                                                    masteryDefended, cardDemastered,
                                                    sessionCompleted, dailyGoalMet,
                                                    streakPerDay, streakMaxPerDay,
                                                    minuteStudied,
                                                    levelCurveBase, levelCurveExponent }
```

- **The one server-owned XP configuration**: every XP amount and the level curve's parameters ([ADR-0047](../adr/0047-xp-values-behind-a-config-repository.md)). `submitStudySession` scores each session with its current copy, falling back to the bundled defaults if the document is missing or invalid. How the client keeps its own copy is in [xp-leveling-system.md](xp-leveling-system.md).
- Readable by any signed-in user, a Guest included. Written only with the Admin SDK, by a seed script or a console edit.

## Curation requests

```
users/{uid}/curationRequests/{cardId}           → { subcategoryId,
                                                    actions: { "<CurationAction>": { flaggedAt }, ... } }
```

- **One document per User per reported Flashcard**, keyed by the card's globally unique id, holding that User's **Curation Request** for it ([ADR-0017](../adr/0017-curation-report-system.md)). `actions` maps each **Curation Action** name (as listed in `CONTEXT.md`) to the server time it was flagged.
- **Written directly by the client** and readable by the owner. Each submission merges its actions into the document and never removes one, except that flagging one of the two difficulty actions deletes the other, so the pair stays mutually exclusive in storage. Admin sync scripts consume these documents; the app never shows them back to the User. See [curation-requests.md](curation-requests.md).

## Bug Reports

```
bugReports/{reportId}                           → { uid, description, severity,
                                                    appVersionName, appVersionCode,
                                                    deviceModel, androidVersion,
                                                    createdAt, status }
```

- **Top-level, with an auto id and the reporter's `uid` as a field**, so every report can be browsed and queried in one place. `severity` is `blocker`, `minor` or `cosmetic`. `androidVersion` is the Android SDK level.
- **Written only by the `submitBugReport` Cloud Function**, which validates the report, rate-limits it per `uid`, and sets `uid`, `createdAt` (server time) and `status` itself ([ADR-0058](../adr/0058-bug-reports-written-only-by-a-callable.md)). The rate limit is counted over a composite index on `uid` and `createdAt`.
- `status` starts as `new`. The developer, or an investigation agent, moves it to `triaged` or `fixed` with the Admin SDK.
- **No client access at all**: security rules deny every read and write, the reporter's own included.

## Account deletion

Account Deletion removes every document under `users/{uid}` and every Bug Report whose `uid` matches ([ADR-0059](../adr/0059-account-deletion-runs-server-side.md)).

## Offline persistence

- The Android client relies on the Firestore SDK's built-in offline persistence for every document above. There is no separate local database.
- **Pending Sessions live outside Firestore**, in a local queue on the device that is delivered through `submitStudySession`. The Card Progress, progress summary and scoring state reads replay them on top of the cached documents, so every screen counts a session as soon as it is queued ([ADR-0055](../adr/0055-pending-sessions-and-local-progress-projection.md)).

## Related

- [CONTEXT.md](../../CONTEXT.md): definitions of every domain term used above.
- [session-submission.md](session-submission.md): how a session is delivered and recorded, and the Streak and Daily Goal rules.
- [flashcard-cache.md](flashcard-cache.md): cache-first Flashcard reads.
- [planned/private-flashcards.md](planned/private-flashcards.md): planned storage for Private Flashcards.
