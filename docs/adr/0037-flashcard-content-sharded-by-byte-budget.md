# Global flashcard content: byte-budgeted shard docs instead of one doc per card

## Decision

The global admin-curated flashcard pool is no longer stored as one Firestore document per card. Each Subcategory's cards are packed into a small number of **shard documents**:

```
subcategories/{categoryId-subSlug}/shards/{n} → { flashcards: { "<cardId>": { id, question, answer, tags[],
                                                             difficulty, createdAt, questionCode?,
                                                             answerCode?, extendedContext?,
                                                             questionSpoken?, answerSpoken? }, ... } }
```

`flashcards` is a **map keyed by card id**, not an array. `id` is also kept inside each value (redundant with the key) purely as a data-integrity cross-check — `build_fixture.py` asserts the two agree — and so `FlashcardDto` needs no special-cased id-from-map-key plumbing on the client. The key is what's addressed for any targeted access; the embedded `id` field is never treated as authoritative on its own.

`build_fixture.py` bin-packs cards into shards **by serialized byte size**, not by card count: it appends cards to the current shard until adding the next one would cross a fixed budget (target ~700KB, safely under Firestore's 1MiB/doc hard cap), then starts a new shard. A Subcategory small enough to fit its whole pool under the budget gets exactly one shard. Shard membership is pure derived data, fully recomputed on every seed run — never hand-curated, never sticky (same category as `featuredSubcategoryNames`, unlike `iconSvg`/`color`).

`subcategoryId-subSlug/shards/` replaces `subcategoryId-subSlug/flashcards/` as the child subcollection name. The client fetches it exactly as it fetches the old `flashcards` subcollection today — a plain `.get()`, no WHERE, no index — then flattens every shard's `flashcards` map values into one in-memory list before handing it to `SelectSessionFlashcardsUseCase`. No new field (e.g. a stored shard count) is needed on the Subcategory doc: the subcollection query itself returns however many shard docs exist.

Card ids must never contain `.` or `/`, and must never start with `__` — Firestore treats `.` as a nesting separator in a dot-path field reference (`"flashcards.<id>.answer"`), `/` is invalid in a map key, and `__`-prefixed names collide with Firestore's own reserved field convention. `build_fixture.py` asserts every id against this before packing. The existing id format (timestamp + short hash, e.g. `2026-08-22T18-53-16-0e5bd8`) already satisfies it.

Private Flashcards (`users/{uid}/privateCards/{subcategoryId}/flashcards/{cardId}`) are **explicitly excluded** — they stay one doc per card. They are appended one at a time by a single user via the Add action; packing them into shared shard docs would mean rewriting a whole shard on every single add, trading a real problem (broadcast-read cost) for a fake one (rare, tiny, per-user writes).

Migration is big-bang: `build_fixture.py`/`seed_firestore.py` are rewritten to emit shards, `purge_firestore.py` removes every existing `flashcards` subcollection, then a full reseed writes shards fresh. This requires a coordinated app release — an old client reading the now-purged `flashcards` subcollection sees nothing — accepted because the app has no live userbase yet.

## Context

Every Flashcard document returned by a query is a billed Firestore read. `subcategories/{id}/flashcards` held one doc per card, so fetching a Subcategory's pool cost N reads — 180 for `swift-concurrency`, up to 244 for `object-model`. `SelectSessionFlashcardsUseCase` (`core:domain`) always operates over the **entire** fetched pool — it filters by difficulty range and tag set, shuffles, then samples a session-length subset (default 20) — so the full pool is genuinely needed client-side for the Preview Study Session Screen's live filter/re-randomize interaction. Nothing is fetched and thrown away unread; the cost is structural, not a bug in a specific call site.

This content is also **identical for every user** — `categories`/`subcategories`/`flashcards` carry no per-user field; per-user mutable state (`progress`, `state`, `favorites`, `sessions`, `privateCards`, `curationRequests`) already lives isolated under `users/{uid}/...` per the existing schema (see [ADR-0007](0007-firestore-collection-structure.md)). At thousands of users, the app was billing full per-card read cost, per user, per subcategory-open, for a payload that never varies between users and only ever changes via the admin seed pipeline.

The obvious "shrink to one doc per subcategory" idea does not hold uniformly: `android/compose` is the largest Subcategory in the corpus at 1180 cards, ~1.34KB average per card, ~1.5MB raw JSON — well over Firestore's 1MiB single-document limit even before Firestore's own per-field overhead. Any fixed rule had to account for that outlier rather than being sized off a typical Subcategory.

Sharding is not just a client-read concern — the app already has an admin write path aimed at individual cards. `users/{uid}/curationRequests/{cardId}` (ADR-0017) exists specifically so a user can flag a problem with one card (wrong tags, needs a code example, difficulty miscalibrated, etc.), for an admin to act on later. That mechanism presumes admins can eventually reach into Firestore and fix *that one card* — a real per-card write requirement, distinct from (and initially conflated with) the earlier question of whether the client ever needs to *read* a single card directly.

## Alternatives considered

**Flat `cards/{cardId}` with a `WHERE subcategoryId ==` query** — already rejected in ADR-0007 for requiring a composite index per read; still true here and not revisited.

**Keep one doc per card, lean on client-side caching (Firestore's local persistence, or explicit `Source.CACHE`)** — rejected as insufficient on its own. `getFlashcardsBySubcategoryId()` uses the SDK default `.get()`, which contacts the backend (and is billed) whenever the device is online, regardless of whether the data changed since last fetch. Every *distinct* user still bills the full N-read cost on their first open of a given Subcategory no matter how aggressively a single device caches thereafter — caching helps repeat-visit cost for one user, not the per-user multiplier that dominates at scale.

**Firestore Bundles or a static CDN-hosted JSON blob per Subcategory, bypassing Firestore reads for global content entirely** — considered and shelved, not rejected outright. It would drive marginal per-user cost for global content to near zero (a CDN GET instead of any Firestore read) but requires new hosting infrastructure, a bundle-generation step, cache-busting on content updates, and a parallel data path alongside the Firestore SDK the rest of the app uses. Kept as the natural next escalation if shard-doc read counts still prove too high once real usage data exists — this ADR does not close that door.

**Fixed-size shards by card count (e.g. always 25 cards/doc)** — rejected in favor of byte-budget packing. Average card size varies a lot by content richness (`extendedContext` grows with difficulty per [ADR-0007](0007-firestore-collection-structure.md)'s difficulty-tiered design) and by source language/category. A flat card-count budget either wastes headroom under the 1MiB cap for lightweight Subcategories or risks crossing it for content-heavy ones like `compose` — byte-budget packing adapts to actual payload size instead of guessing a safe constant.

**Always exactly one shard per Subcategory** — rejected outright once `compose`'s 1180-card, ~1.5MB pool was checked against the 1MiB cap. The byte-budget scheme subsumes this as its common case: any Subcategory that fits under budget naturally gets one shard, with no special-casing needed.

**Dual-write/dual-read transition period during migration** — rejected in favor of big-bang purge + reseed. The app has no live userbase yet, so there is no forced-update-lag risk to hedge against; the extra pipeline and client complexity of supporting both storage shapes simultaneously buys nothing right now.

**`flashcards` as an array of card objects, addressed by index** — considered first, then rejected once the curation admin-write path was accounted for. An array shrinks the payload slightly (no need to repeat each card's id as a map key) but Firestore's dot-path partial update (`update({"flashcards.<key>.field": value})`) only addresses into a **map** field — there is no equivalent for "the array element whose `id` field equals X." Fixing one flagged card in an array-shaped shard means reading the whole shard, finding and mutating the matching element in application code, then writing the **entire array back** — which risks silently clobbering a concurrent edit to a *different* card in the same shard, made in the gap between that read and that write. A map keyed by card id patches one field of one card atomically, touching no other entry, with no read-modify-write race. The ~3% payload saving from omitting the redundant `id` field (measured on `android-compose`: 42KB of 1.4MB) was not enough to give up that guarantee.

## Consequences

- Reads per Subcategory-open drop from N (card count) to `ceil(pool bytes / shard budget)` — 1 for the large majority of Subcategories, ~3 for `compose` today, instead of 1180.
- `FlashcardRemoteDataSource.getFlashcardsBySubcategoryId()` (renamed or re-pointed) queries `subcategories/{id}/shards` instead of `subcategories/{id}/flashcards`; the repository/domain layer above it is unaffected — it already consumes a flattened `List<Flashcard>` and knows nothing about document granularity.
- `difficulty`-mandatory filtering (ADR-0010) and `extendedContext` nullability are unaffected — they're still per-card fields, just nested one level deeper inside each shard's `flashcards` map values instead of being top-level document fields.
- Direct per-card-by-ID *read* from the client is still not possible without first loading the Subcategory's full shard set — accepted, since no client code path does this today and every current screen already needs the full pool regardless. This is unchanged from the array design; only the *write*-side conclusion changed.
- A future admin curation-fix tool can patch one card without touching any other: given a `curationRequests` entry's `subcategoryId` and `cardId`, it lists that Subcategory's `shards` (1 doc for 95 of 96 Subcategories today, 3 for `compose`), finds which shard's map has that key, and calls `update({f"flashcards.{cardId}.<field>": newValue})` on that shard doc alone. Still not built — this ADR only ensures the storage shape doesn't foreclose it.
- `cardCount` on the Subcategory doc is unchanged and still denormalized independently of shard count — it describes card totals for UI, not storage layout.
- `curationRequests/{cardId}` (ADR-0017) and per-user card progress (ADR-0016) are unaffected — both are keyed by `cardId`, and neither ever depended on where the card document itself lives.
- Every card id in the corpus must avoid `.`, `/`, and a leading `__` (see Decision) — a new constraint on the seed pipeline's id generation that didn't exist under one-doc-per-card, where the id was just an opaque Firestore document id with looser rules.
- Requires a one-time coordinated migration: reseed pipeline rewrite, `purge_firestore.py` run against every existing `flashcards` subcollection, full reseed, and an app release that reads the new `shards` shape before (or in lockstep with) that purge.
- Shard boundaries can shift on any reseed as content is added/edited — acceptable since nothing addresses a shard by index from outside the seed pipeline, and card identity (`id`) is stable regardless of which shard currently holds it.
