# One flashcard sort order, and the flashcard selection seam

## Decision

### Sort order is one notion, not two

There is a single user-facing concept of "how my flashcards are ordered", stored once as
`StudySessionPreference.SortOrder`. Subcategory Details, the Preview Study Session screen and
Settings are three edit points onto that one value, not three independent orderings.

Subcategory Details seeds its list order from `StudySessionPreferences.sortOrder` on load, via a
`.first()` snapshot rather than a live collect — the same treatment, and for the same reason, that
the Preview screen already documents: a live collect would let this screen's own "keep as my
default" write feed back and clobber a session-scoped change the user just made. A settings change
made elsewhere therefore does not retroactively re-sort a browse list already on the back stack.

Subcategory Details' Sort dialog is a peer of the Preview screen's: the edit is session-scoped by
default and carries forward, and a "keep as my default" checkbox promotes it to the stored
preference. Settings keeps its existing write-immediately behavior.

`PreviewStudySessionRoute` gains `sortOrder: FlashcardSortOrder? = null`. Null means "no explicit
choice was made upstream, use my saved default"; a non-null value wins over the preference. The
nullability is load-bearing: a non-null field could not distinguish *the user deliberately chose
Default* from *nothing was chosen*, and the Category Details entry point (Quick Session, no browse
list to inherit from) genuinely has nothing to pass.

`FlashcardSortOrder.Default` has two realizations, and this is accepted rather than modelled away.
It means "no explicit difficulty ordering applied", so each context falls back to its own natural
order: source order when browsing a Subcategory, draw order inside a Study Session (where the
shuffle has already happened). Both realizations are documented on the enum.

The Sort icon on the Subcategory Details bottom toolbar carries **no badge**. This reverses
[ADR-0022](0022-subcategory-details-filter-sort-toolbar.md), which specified badges on both the
Filter and Sort icons. Under the seeding rule above, a "non-default" badge would either be
permanently lit for any user whose saved preference is not `Default` — a dot they can never clear
without changing their preference, so it stops carrying information — or it would need to mean
"differs from your saved default", a second, subtler rule for a one-pixel affordance. The Filter
badge is unaffected and stays.

### Filtering is one shared predicate

The tag-OR + difficulty-AND filter is extracted into a single pure domain unit, used by both
Subcategory Details and the study-session selection path. It returns the filtered cards, the tag
vocabulary of the **whole** pool, and the unfiltered total count.

Tag vocabulary must come from the unfiltered pool: derived from the filtered result, a tag would
disappear from the picker precisely because the user filtered it out. `SelectSessionFlashcardsUseCase`
already discovered this and documents it on `poolTags`; sharing the unit means the trap is solved
once and covered by one set of domain tests. The total count is what the "FILTERED TO 4 OF 80 CARDS"
overline needs.

### `SelectSessionFlashcardsUseCase` becomes a composite, and stops owning the cache

It no longer talks to `FlashcardRepository` directly and no longer implements filtering. It fans
out over `subcategoryIds` calling `GetFlashcardsUseCase` per id — keeping its existing parallel
`async`/`awaitAll` load and first-failure propagation — delegates filtering to the shared unit, then
draws and orders. It remains Preview-only; browse never touches it. `GetFlashcardsUseCase` is
unchanged: one id, one action.

Its in-memory pool cache (`cacheMutex` + `cachedCardsBySubcategoryId`) is deleted rather than
relocated into the composite. Caching moves down into `FlashcardRepository`, transparent to every
use case above it.

### The repository owns caching, cache-first, invalidated by generation

`DefaultFlashcardRepository` reads from the server on the first read of a Subcategory in a given
cache generation, then serves every read after that from the on-device cache — with an empty cache
result falling through to the server. That fallback is only sound because of a domain invariant now
recorded in `CONTEXT.md`: **a Subcategory always contains at least one Flashcard.** Without it, a
cache miss and a genuinely empty Subcategory are the same value, and the repository would surface
both as `Result.success(emptyList())`. The reverse fallback holds too: a server read that fails, most
often offline, is answered from the cache when the cache has cards, and the next read tries the
server again.

Firestore's disk cache outlives the process and the SDK offers no cache-if-fresh-else-server mode
and no TTL, so freshness policy lives in app code. It is a **cache generation** the repository
consults: the first read of a Subcategory in a given generation goes to the server, every read after
that is served from cache. What bumps that generation — a server-side knowledge-base seed value
compared at app start against a locally stored copy — is a separate effort; this ADR fixes only the
seam the repository exposes. See `docs/temp/seed-versioned-cache-invalidation-context.md`.

## Context

Subcategory Details was a placeholder: it fetched a Subcategory's flashcards and listed them, with
an unwired filter icon, sort icon and add button. Implementing [ADR-0022](0022-subcategory-details-filter-sort-toolbar.md)'s
filtering and sorting forced three questions the existing code had never had to answer.

**Is a browse list's order the same thing as a study session's order?** Both offer the identical
three options through the identical `FlashcardSortOrderDialog`, and both would read and write the
same stored preference — but a browse list is not a session draw, and nothing had decided whether
they were one concept or two coincidentally-similar ones.

**Where does filtering live, now that two screens filter?** `SelectSessionFlashcardsUseCase`
filtered internally. Adding a second filtering screen meant either duplicating the AND/OR semantics
that ADR-0022 pins down, or extracting them.

**What happens to the pool cache?** That use case owned a cache whose documented purpose was making
"re-run selection on every dialog confirm" affordable. Splitting the use case apart would have
destroyed it silently, turning every Preview dialog confirm back into a network round trip.

Primary-source research into the Firestore Android SDK (`docs/temp/firestore-offline-cache-research.md`)
supplied the facts behind the caching half. The load-bearing ones: `Source.DEFAULT` is server-*first*,
not cache-first — the SDK suppresses the cached snapshot whenever it believes it is online — so every
read in the app today is a server round trip. There is no per-collection cache policy in the API at
all. `Source.CACHE` misses return an empty list rather than an error. And because flashcards are
shard-packed (ADR-0037) at roughly one document read per Subcategory against a 50k/day free tier, the
motivation for caching is latency and bandwidth, not billing.

## Alternatives considered

**Two independent sort notions — browse sort as a display-only scanning aid** — rejected. It reads
well in isolation (a file-manager column sort changes nothing about what you then do with the files)
but breaks the promise the design makes: the CTA reads "Start session · 4", so the list you are
looking at is the set you are about to study. Filters already carry across that boundary; order
crossing it too is the same principle.

**Browse starts at `Default` and only an explicit change carries** — rejected as internally
inconsistent. A user with `HardestFirst` saved would open the screen, change nothing, press Start,
and study in a different order from the list they had just been reading.

**Splitting `FlashcardSortOrder.Default` into distinct values** (`SourceOrder` for browsing,
`Shuffled` for a draw) — rejected. It is the more honest naming, but the enum is `@Serializable` and
persisted as a preference, so it needs a stored-value migration, and the shared dialog would have to
offer different option sets per screen to keep the values meaningful.

**A non-null `sortOrder` on the route** — rejected. Cannot distinguish a deliberate `Default` from
an absent choice, which forces Category Details to read preferences purely to forward them, or
silently gives Quick Session users `Default` regardless of what they saved.

**Badge on Sort meaning "differs from your saved default"** — rejected along with the badge itself.
It is the only rule that stays informative under seeding, but it requires holding the saved value in
state alongside the active one to support one dot, and the Sort dialog already shows the current
selection explicitly.

**Stripping fetch out of `SelectSessionFlashcardsUseCase` entirely**, leaving a pure draw-and-order
function with the ViewModel composing load → filter → select — rejected. It is the cleanest single
responsibility, but it pushes the multi-subcategory fan-out and its first-failure propagation into a
ViewModel where the domain tests do not reach it, and it discards that use case's documented design
intent as the shape a future server-side or AI-driven selector would be POSTed.

**Two filter implementations, one per screen** — rejected. The AND/OR semantics are exactly what
ADR-0022 pins down and what both screens must agree on; two copies can drift.

**Relying on Firestore's own cache with no app-level policy** — rejected. It is correct once the
never-empty invariant holds, and it survives process death, but a cached Subcategory would then be
frozen on-device indefinitely: imported or edited cards would never appear, because the SDK has no
freshness mechanism to appeal to.

**`clearPersistence()` for invalidation** — rejected, and recorded here because it is the obvious
first instinct. It must run while the client is not started, so reading a seed from Firestore makes
it fail with `FAILED_PRECONDITION`; it wipes pending writes, and this app has them
(`CurationRemoteDataSource` does `set(merge)`); and it is all-or-nothing, where a generation flag
refetches only what the user actually visits.

**Snapshot listeners with a `Flow` repository API** — rejected as out of proportion. It is what the
Firestore documentation recommends and it solves freshness and latency together, but it turns
`fetchFlashcards` into a `Flow` across every use case, ViewModel and test fake, and it makes
selection re-run on emissions the Preview screen does not want.

## Consequences

- `FlashcardSortOrder` gains KDoc covering both realizations of `Default`.
- `PreviewStudySessionRoute.sortOrder` is nullable; the Preview ViewModel's `init` seeds sort from
  preferences **only** when the route carries null. Every other seeded field is unaffected.
- ADR-0022's badge specification is amended: Filter keeps its badge, Sort loses it.
- `SelectSessionFlashcardsUseCase` loses its repository dependency, its filtering, and its cache; it
  gains a dependency on `GetFlashcardsUseCase` and the shared filter unit. Its existing tests need
  reworking against the new collaborators.
- `FlashcardRepository` gains cache-generation awareness and, for the write path that does not exist
  yet, an invalidation seam. The interface change reaches every implementation and test fake.
- The never-empty Subcategory invariant becomes load-bearing rather than incidental: the cache-first
  read path is unsound without it. It is recorded in `CONTEXT.md` and any future ability to create an
  empty Subcategory would have to revisit this ADR.
- Subcategory Details' content state has four cases, not five — a genuinely empty Subcategory is
  unreachable under the invariant, leaving `Loading`, `Error`, `Cards` and `NoMatches`.
- Until the seed-versioned invalidation effort lands, the cache generation is never bumped, so a
  curation update is invisible on-device for the lifetime of the cache entry. The two efforts must
  land in that order.
