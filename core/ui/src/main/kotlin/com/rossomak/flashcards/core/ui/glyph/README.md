# Category glyph cache

Renders each Category's SVG icon (`Category.iconSvg`) once per process, off the main thread, and reuses
the result on every later appearance. Readers are `FlashcardsVectorIconTile` and
`FlashcardsInlineCategoryGlyph` (`core/ui/composables/FlashcardsVectorIconTile.kt`).

## Why it exists

Parsing the SVG and rendering it to a `Picture` used to happen during composition, on the main thread, and
was cached only with `remember`. The Main screen's tab host disposes a tab on switch, so every return to
Home or Browse re-parsed every visible icon. Browse composes its whole Category list at once. Each icon was
also tinted through its own `saveLayer` + `drawPicture`, an offscreen buffer per icon per frame. Both costs
landed in the frames the bottom bar needs for its tint and indicator animations.

## Pieces

| File | Role |
|------|------|
| `GlyphCache.kt` | Generic `GlyphCache<T>`: bounded LRU, synchronous `peek`, suspending `load`, shared in-flight loads. Takes its render step as a constructor function, so it runs on the plain JVM in tests. |
| `CategoryGlyphCache.kt` | The process-wide `CategoryGlyphCache` singleton (capacity 128), the AndroidSVG render function, `toCategorySvg()`, and the `rememberCategoryGlyph` Composable read path. |

## Model

- **Key**: `GlyphKey(svgSource, sizePx)`. The same source at a different pixel size is a separate entry. **Tint is deliberately not part of the key**.
- **Value**: `CachedGlyph<T>`, either `Ready(glyph)` or `Unavailable`.
- **Eviction**: a `LinkedHashMap` in access order, which evicts the least-recently-used entry once the size
  exceeds `capacity`. Both `peek` and `load` hits count as a use. `Unavailable` entries count toward
  capacity.
- **Capacity**: must be positive. `require(capacity > 0)` guards it, because a zero capacity would cache
  nothing and would quietly stop remembering negative results.

## Read path (`rememberCategoryGlyph`)

1. Build the key from `iconSvg` and the `Dp` size converted to px at the current density. A null `iconSvg`
   resolves to `Unavailable` at once, without touching the cache.
2. `remember(key) { mutableStateOf(peek(key)) }` makes a synchronous cache read. On a hit the glyph draws
   in the same frame, so returning to a tab shows its icons in the first frame with no flicker. `peek` runs
   only when the key changes, not on every recomposition.
3. On a miss, `LaunchedEffect(key)` calls `load(key)`, which renders on `Dispatchers.Default`, then writes
   the result into the state.
4. The caller renders one of three states:
   - `null`, still loading: the tile shows only its tinted container, and the inline glyph reserves its
     size with a `Spacer` so the text line doesn't shift. The fallback Folder icon is **never** used as a
     placeholder, because it would flash the wrong icon.
   - `Unavailable`: the themed Folder fallback.
   - `Ready`: `Image(bitmap, colorFilter = ColorFilter.tint(tint))`.

## Tinting

The cache stores **untinted** bitmaps: the SVG is rendered in its source colors, and only the alpha channel
matters. At draw time the caller applies `ColorFilter.tint(tint)`, which uses `SrcIn` by default, the same
mechanism `Icon(tint = …)` uses. This recolors every opaque pixel, whatever `fill` the SVG declares, so a
multi-color source flattens to one tint. The filter is set on the paint for `drawImage`, so no offscreen
layer is created. It is remembered per tint (`rememberTintFilter`).

Why tint is not baked in: with tint in the key, any tint change misses the cache and the glyph blanks until
the new load finishes. Real cases:
- The onboarding Favorites card toggles its `contentColor` between selected and unselected.
- A light/dark switch changes the theme-derived tints (`onSecondaryContainer` for tiles with no curated
  color, `onSurfaceVariant` for search-result glyphs).

Tinting at draw time means one entry serves every tint, with fewer entries and less memory.

## Concurrency

- One `lock` guards both the LRU map and the in-flight map. Every access to the LRU map, `peek` included,
  holds the lock, because an access-order `LinkedHashMap.get` changes the map's internal order.
- `load` checks the cache and registers the in-flight load inside one `synchronized` block. Concurrent
  loads of the same key find the existing `Deferred` and await it, so there is **one render per key**,
  however many callers.
- The early `return` from inside `synchronized` is safe: `synchronized` is inline and releases the monitor
  in a `finally`.
- The render runs as `async` in the cache's own `loadScope` (`SupervisorJob` + `Dispatchers.Default`), not
  in the caller's scope. When a caller is cancelled (its composable left the composition), only its
  `await` is cancelled. The render still finishes and is cached, so the next appearance hits.
- On success the result is written to the map first, then the in-flight entry is removed, in separate
  locked sections. A `load` that runs between them finds the cached entry and returns early, so there is no
  window in which it could start a second render.
- Browse composes every Category at once, so misses render in parallel across the `Default` threads. Each
  render builds its own AndroidSVG parser and document, and the library's static state is only read. This
  is believed safe, but thread-safety across instances has not been independently verified.

## Failure handling

- **`Exception` from the render** (a malformed curated SVG, or a bad bitmap size) and a **null return**
  are cached as `Unavailable`. A broken icon is parsed once, then served from the cache as the Folder
  fallback. This is an expected, handled outcome, so the exception is intentionally dropped (detekt
  `SwallowedException` is suppressed). `:core:ui` has no dependency on `:core:common`'s `AppLog`.
  `render` does not suspend, so a `CancellationException` cannot be caught by mistake.
- **`Error` from the render** (for example `OutOfMemoryError` while allocating the bitmap) is treated as
  transient. It is **not** cached and reaches every waiting caller through `await`. The in-flight entry is
  removed in a `finally`, so a later `load` retries instead of re-awaiting a permanently failed `Deferred`.
  If the in-flight entry leaked, every later request for that key would rethrow. In practice the error
  surfaces in the reader's `LaunchedEffect` and crashes, the same as an OOM anywhere else, rather than
  permanently showing a Folder.

## Leak analysis

- **No UI or Context references.** The `async` block captures only the cache and the key. The render is a
  top-level function. The singleton holds no `Context`, `Activity` or composition. A cancelled
  `LaunchedEffect` leaves nothing behind.
- **`loadScope` is never cancelled.** It lives as long as the singleton, which is the process. That is
  intended for a process-wide cache and is not a leak.
- **The in-flight map always drains**, on success, `Exception` and `Error` alike, because of the `finally`.
  It grows only with the number of distinct keys being loaded at once.
- **Evicted bitmaps are not `recycle()`d.** A composition may still be drawing one through its remembered
  state, so the GC reclaims it once nothing references it. Recycling it would crash that draw.
- **Bounded memory.** The tile glyph is 24dp: about 84–96px square at high densities, ARGB_8888, so roughly
  28–36 KB. Inline glyphs are smaller. With 128 entries the worst case is about 4–5 MB. Keys hold the
  same `String` instance as the `Category`, not a copy, which pins at most 128 SVG documents after their
  Categories are gone.
- **Key churn is bounded.** Density or configuration changes create new `sizePx` keys, and the old ones age
  out through the LRU. Tint changes create no keys at all.

## Known limitations

- **Static previews**: `LaunchedEffect` doesn't run, so a preview given a real (uncached) SVG shows an empty
  tile. Current previews and Showkase entries pass `iconSvg = null`, which renders the fallback, so nothing
  is affected. A fix would render synchronously when `LocalInspectionMode` is true.
- **Key comparison cost**: every recomposition builds a new `GlyphKey`, and `remember(key)` compares it with
  the previous one. With the same `String` instance the comparison short-circuits on identity. With a new,
  equal instance (a Firestore refresh), it is one full comparison of a few KB. `String.hashCode` is
  computed once per instance and cached. Negligible.
- **First-draw upload**: the bitmap uploads to the GPU on its first draw. Calling `Bitmap.prepareToDraw()`
  after rendering would move that upload off the first frame; this is an optional optimization.
- **App shortcuts** (`DefaultAppShortcutsRepository` in `:core:data`) still rasterize SVGs through their
  own path. They are off the UI hot path and can't depend on `:core:ui`. Negligible, not a common user journey path.
