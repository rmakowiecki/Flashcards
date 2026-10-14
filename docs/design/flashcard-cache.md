# Flashcard Cache

How Flashcard reads are served from the device cache, and when that cache is thrown away. Two parts work together: a cache-first read policy in the Flashcard repository ([ADR-0038](../adr/0038-one-sort-order-and-flashcard-selection-seam.md)), and a check at app start that decides when to start over ([ADR-0039](../adr/0039-seed-versioned-flashcard-cache-invalidation.md)).

## Cache-first reads

Firestore's default read is server-first: it ignores the cached copy whenever it believes the device is online. Without a policy of its own, every read of a Subcategory's Flashcards would cost a network round trip, and browsing, the Preview Study Session Screen and every filter or sort change would each pay it again.

The repository in `core:data` therefore keeps a **cache generation**, an in-memory counter that lives as long as the process. The first read of a Subcategory's Flashcards in a generation goes to the server. Every later read of that Subcategory in the same generation is served from the on-device Firestore cache. Starting a new generation re-arms the server read for every Subcategory at once.

Three edge cases shape the policy:

- **An empty cache result counts as a miss** and falls through to the server. Firestore reports an evicted or never-cached Subcategory as an empty list, not an error. This is sound only because a Subcategory always contains at least one Flashcard (see **Subcategory** in [CONTEXT.md](../../CONTEXT.md)), so an empty result can never mean a genuinely empty Subcategory.
- **A failed server read falls back to the cache.** When the server read fails, most often because the device is offline, the cached Flashcards are returned if there are any, so a Study Session still loads offline, even after the process was killed. The Subcategory is not marked as read in this generation, so its next read tries the server again. With nothing cached, the server's failure is reported.
- **A server read that overlaps a new generation is not trusted as current.** If the generation changes while a server read is in flight, the response still goes back to the screen that asked for it, but the Subcategory is not marked as read in the new generation. Its next read goes to the server again, rather than treating a response fetched before the change as fresh.

Only Flashcards are cached this way. Category and Subcategory reads use Firestore's default behavior and have no freshness policy of their own.

## Seed-versioned invalidation

One server-side seed value is the freshness signal for the whole knowledge base: one value for everything, not one per Subcategory ([firestore-schema.md](firestore-schema.md)). The seed pipeline increments it as the last write of every import run, after every content write has landed, so a run that fails partway never moves it.

At app start, the device reads the seed straight from the server, never from the cache, and compares it with the copy it stored last time. That copy lives in the device's own preferences and is not tied to a signed-in User, because the Flashcard content it guards has no owner either. A device that has never checked has no stored copy, which always counts as a mismatch.

- **Mismatch**: the device starts a new cache generation, then stores the new seed value.
- **Match**: nothing happens.
- **The seed cannot be read**, most often because the device is offline at launch: nothing happens. This is ordinary behavior, not an error state; the cache-first reads above already tolerate stale content.
- **The new value cannot be stored**: the new generation has already started, so the Flashcards are still refreshed. The mismatch simply shows up again on the next launch, which starts one more generation.

The whole check is best-effort: a stale cache recovers on the next launch, while a failed app start would not.

The check runs alongside the startup sign-in check and never holds it up. Nothing waits for its result, so a slow or missing network delays neither the first screen nor sign-in.

## Related

- [ADR-0038](../adr/0038-one-sort-order-and-flashcard-selection-seam.md): why the repository owns caching and how the cache generation works.
- [ADR-0039](../adr/0039-seed-versioned-flashcard-cache-invalidation.md): why one global seed value drives the generation.
- [firestore-schema.md](firestore-schema.md): the seed document and the Flashcard shards.
- [flashcard-selection.md](flashcard-selection.md): the session card draw that reads through this cache.
