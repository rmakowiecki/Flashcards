# Seed-versioned flashcard cache invalidation

## Decision

### A global seed document drives the cache generation

A single Firestore document, `meta/seed` (`{ value: <monotonic int> }`), is the freshness signal
for the flashcard cache `DefaultFlashcardRepository` already implements (ADR-0038). One seed for
the whole knowledge base, not one per category or per subcategory: the redundant-fetch problem the
cache solves is already near Firestore's one-read floor per subcategory (ADR-0037's shard packing),
so per-subcategory granularity would multiply documents and rules surface for a saving that does
not exist. A monotonic integer, not a timestamp or a content hash: cheapest possible compare
(`!=`), no clock-skew exposure, no hashing step in the seed script.

`firestore.rules` gets one new `match /meta/{document}` block, `allow read: if request.auth !=
null`, mirroring the existing `categories`/`subcategories` pattern — read-only to clients, written
only by the Admin SDK the seed script already runs under.

### The app checks it once per launch, tolerant of failure

At startup, the app reads `meta/seed` with `Source.SERVER` (a cache read here would defeat the
purpose) and compares it against a locally stored copy. On a mismatch, it calls
`FlashcardRepository.invalidateFlashcardCache()` — the seam ADR-0038 built and left unwired — and
persists the new value. On a match, or on any failure to read (most commonly: offline at launch),
it does nothing and the app proceeds on whatever cache generation is already active. This is not a
degraded path needing an error state: an offline launch is expected, ordinary behavior, and the
existing cache-first read path already tolerates staleness by design.

The local copy lives on `UserPreferences` (`core:domain`), device-scoped like the rest of that
model, not tied to a signed-in uid — the flashcard content it gates has no owner field either (see
Context). It is stored **nullable** (`localCacheSeed: Int?`, no default), not defaulted to `0`:
defaulting to a real integer risks a false-negative match if the server seed also happens to be
low at ship time. `null` unambiguously means "never checked," which forces a mismatch — and
therefore one `SERVER` refresh per subcategory the user actually visits — on every device's first
launch after this ships.

### The import script bumps it, in the same run it writes content

`seed_firestore.py` increments `meta/seed.value` by one as the last write of every run, in the
same script invocation that writes categories, subcategories and shards — not a separate script, a
Cloud Function trigger, or a manual step. A separate mechanism is a second thing that can be
forgotten; a bump inside the script that already writes the content it is announcing cannot be
skipped without also skipping the content write.

### Scope stays flashcards only

The generation gates `FlashcardRepository.fetchFlashcards`, nothing else. `categories` and
`subcategories` list reads (names, icons, counts) are unaffected — they change far less often than
card content and are not part of the redundant-fetch problem this scheme exists to fix (browse,
session preview and filter/sort dialogs all re-fetch *cards*, not the category list). Extending the
generation to cover them is a future, separately-decided expansion, not implied by this ADR.

### The never-empty-Subcategory invariant stays a documented convention, not enforced code

ADR-0038 recorded `CONTEXT.md`'s "a Subcategory always contains at least one Flashcard" as
load-bearing for the cache-first read's empty-means-miss fallback. `seed_firestore.py`'s
`upsert_shards()` already contains a comment acknowledging a subcategory can reach zero cards
("contributes no shards at all"), and no code anywhere — script or client — currently deletes an
emptied subcategory or filters a zero-card one out of the browse list. This ADR does not close that
gap: the team's judgment is that curation will never actually drive a subcategory to zero cards in
practice, so no enforcement is added. The invariant remains true by convention and by
`CONTEXT.md`'s statement of it, not by anything that would catch a violation.

## Context

`DefaultFlashcardRepository` (ADR-0038) already implements the cache-first read and exposes
`invalidateFlashcardCache()`, but nothing calls it — the generation counter never advances, so a
curation update stays invisible on-device for the life of a cache entry. Closing that gap was
deliberately left to a separate effort, called out explicitly in ADR-0038's consequences, because
unlike everything else in that ADR it does not stay inside the Android app: it needs a new
Firestore document, a `firestore.rules` change, a change to the Python import tooling, and a home
in app-startup code (`AppStartViewModel`/`AppStartupState` already exist and are the obvious one).

Firestore's disk cache outlives the process and the SDK offers no cache-if-fresh-else-server mode
and no TTL, so freshness policy has to live in app code — this scheme is that policy, for the "who
tells the app a curation update happened" half specifically.

Cost is explicitly **not** the motivation. Shard-packed cards (ADR-0037) already sit near the
one-read floor against a 50k/day free tier, and whether `Source.CACHE` reads are billed at all is
undocumented by Google — the research flagged it as a gap, not an asserted fact. This scheme is a
latency-and-bandwidth change; nothing about it should be cited in a future cost projection.

## Alternatives considered

**A Cloud Function trigger, firing on writes to `subcategories/*/shards`, bumping the seed
automatically** — rejected. There is no existing automated curation pipeline; `seed_firestore.py`
is a manually-run developer script against a service-account credential. A trigger adds
infrastructure to guarantee an invariant a single added line in the same script already guarantees,
for a script that is by construction always the thing making the write.

**Per-subcategory seed values** (a `seed` field per `subcategories/{id}` doc, compared
individually) — rejected. It buys precision — skip refetching subcategories the curation run did
not touch — at the cost of a rules match and a write per subcategory per run, for a redundant-fetch
problem that is already close to Firestore's one-read floor. Revisit only if curation cadence
becomes heavily skewed toward one subcategory while the rest sit untouched for long stretches.

**Persisting the repository's per-subcategory "already refreshed this generation" set to disk**
(instead of the in-memory `Map<String, Long>` `DefaultFlashcardRepository` already holds) —
rejected. The set only exists to avoid a redundant `SERVER` read within one process's lifetime;
losing it on process death costs one extra `SERVER` read per subcategory touched after relaunch,
already priced into "latency/bandwidth, not cost."

**Enforcing the never-empty invariant now** (seed script deletes a subcategory doc that reaches
zero cards; `CategoryDetailsScreen` filters `cardCount == 0` defensively) — considered and
deferred. No live data violates it today (checked against the current seed fixture: zero
zero-card subcategories across 96), and the team's call is that curation will not produce one. Left
as a documented, unenforced convention rather than built.

**Timestamp or content-hash seed values** — rejected in favor of a monotonic int. A timestamp
introduces clock-skew exposure between the machine running `seed_firestore.py` and Firestore's
server time; a content hash needs a hashing step over the fixture and a wider value type, for a
compare that only ever needs to answer "did anything change since I last checked," not "what
changed" or "when."

**`clearPersistence()` for invalidation** — already rejected in ADR-0038 (ordering conflict with
reading the seed from a started client, destroys pending curation writes, all-or-nothing). Not
re-litigated here; recorded again only because it is the alternative most likely to be proposed
again in review. A manual "clear app storage" remains the only recovery path for cache corruption
that this scheme cannot fix — documented as a fact, not built as a feature.

## Consequences

- New Firestore document `meta/seed`, `{ value: Int }`, plus one new `firestore.rules` match block.
- `FlashcardRepository` gains `suspend fun fetchCacheSeed(): Result<Int>`.
- `UserPreferences` gains `val localCacheSeed: Int? = null`; `UserPreference` gains a
  `CacheSeed(value: Int)` case. Both device-scoped, same as the rest of that model.
- A new domain use case reads the remote seed, compares it against the stored local copy via a
  `.first()` snapshot, and on mismatch calls `invalidateFlashcardCache()` and persists the new
  value. A read failure (remote or local) is a true no-op, silently swallowed — offline-at-launch
  is not an error state here. A *persist* failure is different: invalidation already ran by that
  point, so it's a partial success, not a no-op — the mismatch reappears and retries next launch.
- `AppStartViewModel` runs this alongside its existing auth check, but fully decoupled — its own
  `viewModelScope.launch`, no timeout of its own — rather than "timeout-bounded the same way" as
  originally decided here. A live check against production caught why: a cold-start Firestore
  `SERVER` read routinely outlasts the 1000ms the auth check bounds itself to, which would have
  silently defeated this scheme on exactly the launch it exists to catch. Decoupling already
  guarantees a slow or absent network can't extend startup, so no timeout is needed to promise
  that a second time — `viewModelScope` cancelling the coroutine when the ViewModel clears is
  bound enough.
- `invalidateFlashcardCache()`'s generation counter is an `AtomicLong`, and the per-Subcategory
  server-read stamp captures the generation before the network call and only commits it if the
  generation is unchanged after — not incidental: a bump landing mid-flight on an in-progress
  `readFlashcards` must not have its stale (pre-invalidation) response stamped as belonging to the
  new generation. Caught in PR review, not designed in up front; see
  `DefaultFlashcardRepository.readFromServer`'s KDoc for the mechanism.
- `seed_firestore.py` writes `meta/seed.value = previous + 1` as the last step of every run.
- First launch after this ships mismatches unconditionally (`localCacheSeed` starts `null`) — one
  extra `SERVER` read per subcategory the user visits, once, on every existing install.
- Sign-out and account switching have no interaction with this scheme: `categories`,
  `subcategories` and `shards` carry no owner field, so the cache is not user-partitioned to begin
  with.
- The never-empty-Subcategory invariant (ADR-0038) remains unenforced by any code path. A future
  change that makes an empty Subcategory representable must revisit both ADRs, not just this one.
- No pull-to-refresh is added, and this scheme does not assume none will ever exist — a future
  manual refresh would simply force a `SERVER` read regardless of generation, composing cleanly
  with the generation counter rather than requiring rework of it.
