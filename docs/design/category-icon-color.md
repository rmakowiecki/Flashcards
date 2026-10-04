# Category icon + color

Design basis for giving each Category its own icon glyph and accent color, matching the updated
visual design for the categories list (Study tab Browse). First designed 2026-08-01, implemented
2026-08-08, substantially revised 2026-08-18 after the original Storage+Coil-SVG approach was
reconsidered, then revised again same-day switching the icon format from Android VectorDrawable
XML to plain SVG rendered via `androidsvg` — see "Implementation history" at the bottom.

## Scope

Category level only. Subcategory keeps no icon/color field — no change to `Subcategory`/
`SubcategoryDto`. Subcategory rows may later reuse the parent Category's color for visual
continuity (e.g. a tinted `CategoryDetailsScreen` top app bar), but that consumption is future
work and requires no new field now.

## Domain model

`core/domain/.../model/Category.kt`

```kotlin
data class Category(
    val id: String,
    val name: String,
    val order: Int,
    val subcategoryCount: Int,
    val iconSvg: String?,
    val color: String?,
)
```

- Both `iconSvg` and `color` are **nullable**. `null` means an admin hasn't curated that field yet
  — a normal, expected transient state, not an error: the seed pipeline can legitimately
  auto-create a category (from inbox folder slugs) before its icon/color art exists. Rendering
  always degrades gracefully to a default rather than crashing — see Rendering.
- `iconSvg` holds a **plain SVG document** as text (not a URL, not Android VectorDrawable XML) —
  see "Icon format" below for why.
- Both fields stay plain `String?` in the domain layer — no `androidx.compose.ui.graphics.Color`/
  `androidsvg` dependency in `core/domain`. Parsing happens only at the UI edge (see Rendering).

## Firestore / DTO

`core/data/.../model/CategoryDto.kt` mirrors the domain shape:

```kotlin
data class CategoryDto(
    val id: String = "",
    val name: String = "",
    val order: Int = 0,
    val subcategoryCount: Int = 0,
    val iconSvg: String? = null,
    val color: String? = null,
)
```

Document shape (`categories/{categoryId}`, per ADR-0007):

```
{ name, order, subcategoryCount, iconSvg, color }
```

`FlashcardMapper.kt` is a straight nullable-to-nullable passthrough for both fields — no filtering
at the mapper layer. A half-curated category is a fully valid `Category`, just one that renders
with defaults (unlike, say, `Flashcard.difficulty`, which *does* filter out the whole card when
absent — that precedent was considered and deliberately not followed here, since dropping an
entire category from Browse over a missing icon is worse UX than showing it with a placeholder).

## Icon format: plain SVG, rendered via `androidsvg`, not Storage

The icon is a **monochrome glyph** (single-color silhouette), tinted at render time using
`Category.color` so icon color and container background color always derive from the same source
of truth.

Format is a **plain SVG document** (`<svg>` / `<path d=.../>`, real SVG semantics including
`transform`, nested `<g>`, fill rules), stored inline as text directly on the Firestore document —
not a Storage URL, not Android VectorDrawable XML. This is the result of three rounds of revision:

1. **Storage was dropped entirely.** The original design hosted SVG files in Firebase Storage,
   loaded via Coil's SVG decoder. Reconsidered because: (a) this project has no `storage.rules`,
   no Firebase App Check, and a public-read rule would have been a genuinely undefended
   Storage-billing-abuse surface for data that's tiny and rarely changes; auth-gating it required
   a custom OkHttp interceptor attaching a Firebase ID token to Storage requests — real
   infrastructure for a problem that (b) doesn't need to exist at all once the icon data can live
   directly on the `Category` document, arriving with the same authenticated Firestore read as
   every other field, cached offline the same way, with no separate network fetch, no "loading"
   state, no image-cache invalidation story to design.
2. **Raw SVG (as inline text) was considered and dropped in favor of Android VectorDrawable XML,
   then switched back to plain SVG.** The original objection to raw SVG was that real exported
   icons carry a `transform="translate(...) scale(...)"` attribute — a compound mini-language a
   first-party parser would have to tokenize itself — whereas VectorDrawable XML expresses the same
   thing as flat numeric attributes. That reasoning assumed a **hand-rolled parser** either way. It
   stopped applying once the project adopted `androidsvg` (below), a mature third-party SVG
   renderer that implements the full SVG transform/group/fill-rule model itself — there is no
   longer any first-party tokenizing to avoid, so the VD-XML detour's entire justification
   evaporated. Net effect: plain SVG is *simpler* to author (no Android Studio conversion step —
   paste an exporter's SVG output directly) and the on-device parser is now a two-line library call
   instead of ~130 lines of hand-rolled `XmlPullParser`/`ImageVector.Builder` tree-walking.
3. **`androidsvg` was evaluated, initially rejected in favor of a first-party parser, then
   adopted after actually living with the first-party version.** The first-party VD-XML parser hit
   two real, device-only bugs in its first day of use (`Xml.newPullParser()` silently enables
   `FEATURE_PROCESS_NAMESPACES`, breaking attribute lookups; `ImageVector.Builder.addPath`'s `fill`
   defaults to `null`, drawing nothing) — neither caught by compiling or by JVM unit tests, only by
   running the app on a real device. That cost changed the calculus: `androidsvg`'s CSS-override
   tinting mechanism is real and documented
   ([change_colours.html](https://bigbadaboom.github.io/androidsvg/change_colours.html)), though
   this codebase doesn't use it — see Rendering for why a manual `BlendMode.SrcIn` layer was used
   instead, matching how `Icon(tint = ...)` already tints everywhere else in this app.

Also rejected, independent of icon format: pre-colored icon images (couples icon color to
container color by convention only, easy to drift); local drawable/`ImageVector` lookup keyed by
category (blocks adding new categories without an app release).

Authoring: paste a source SVG export directly — no Android Studio Vector Asset conversion step
needed (that step existed only to produce VectorDrawable XML, which is no longer the target
format).

## Parsing (`core/ui`)

Two small, independent, **strict** (throw-on-malformed) pure functions — failure-handling policy
lives one layer up, in the rendering composable, not baked into the parsers:

- **`String.toCategoryColor(): Color`** — `#RRGGBB` only (no alpha channel — category tint is
  always opaque). Implemented as a plain-Kotlin regex + `require()` parse, *not*
  `android.graphics.Color.parseColor()` — the latter is a stubbed Android-framework call that
  throws `"not mocked"` in this project's plain-JVM, no-Robolectric unit tests (`TESTING.md`),
  which would make this function's own contract untestable. Fully unit-tested (valid hex, and each
  malformed shape throws). Unchanged by the SVG switch — this function has nothing to do with icon
  format.
- **`String.toCategorySvg(): SVG`** — a thin wrapper around `androidsvg`'s
  `com.caverock.androidsvg.SVG.getFromString(String)`. No first-party XML walking at all: transform
  tokenizing, nested groups, and fill-rule handling are the library's problem now, not this
  project's. **Not JVM-unit-tested**: rendering the parsed `SVG` to a `Picture` requires
  `android.graphics.Picture`, an Android-framework class stubbed on this project's plain-JVM
  test stack (`TESTING.md`) — the same boundary the previous VD-XML parser hit, just moved from
  parse-time to render-time, since parsing itself no longer touches any Android-framework class.
  Correctness is verified by compiling + a Compose preview/Showkase visual check, same as every
  other rendering composable, plus an on-device check given the format switch's own history of
  device-only bugs.
- `fill`/`fill-rule` values in the source SVG are irrelevant and ignored — tint always fully
  overrides them at render time (see Rendering). A genuinely multi-color source icon would
  silently flatten to one tint color; acceptable given the "simple monochrome glyph" constraint.

## Rendering (`core/ui`)

`FlashcardsVectorIconTile` — the packaged, boxed tile used today in Browse. Sibling to
`FlashcardsIconTile`, not a modification of it (`FlashcardsIconTile` stays a simple
`ImageVector`-based tile for its existing non-SVG callers). Named `Vector`, not `Remote`/`Svg` —
nothing is fetched separately, the data arrives embedded in the `Category` object; the name
predates the SVG-format switch and wasn't worth re-churning a second time.

```kotlin
@Composable
fun FlashcardsVectorIconTile(
    iconSvg: String?,
    color: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
)
```

- `color?.let { runCatching { it.toCategoryColor() }.getOrNull() } ?:
  MaterialTheme.colorScheme.onSecondaryContainer` — the same default `FlashcardsIconTile` already
  falls back to when no color is specified; not a new convention. An absent or malformed `color`
  land on the same fallback, no distinction, no crash.
- Icon resolution: `iconSvg?.let { runCatching { it.toCategorySvg() }.getOrNull() }` — `null`
  (either absent or malformed) renders `Icons.Default.Folder` via the plain `FlashcardsIconTile`
  path instead. An absent `iconSvg` (not yet curated) and a malformed one (invalid SVG) land on the
  same fallback glyph, with no distinction between the two — no crash either way. **This supersedes
  the original "no fallback, fail loud" stance** the first version of this doc took for these two
  fields (rollout/crash-semantics were designed around a narrow one-time backfill window; the seed
  pipeline's current behavior — auto-creating categories with these fields legitimately absent —
  made "fail loud" the wrong default for an expected, ongoing state rather than a rare edge case).
- Call site (`BrowseScreen.kt`) passes `category.color`/`category.iconSvg` straight through as
  nullable strings — all null/malformed-handling lives inside the tile, not the call site.
- **Tinting mechanism**: a parsed `SVG` isn't an `ImageVector`, so it can't just go through
  `Icon(tint = ...)`. Instead: `svg.renderToPicture(widthPx, heightPx)` rasterizes into an
  `android.graphics.Picture` sized to the tile's icon content box, drawn inside a Compose `Canvas`
  via a manual `canvas.saveLayer(bounds, paint)` / `canvas.nativeCanvas.drawPicture(picture)` /
  `canvas.restore()`, where `paint.colorFilter = ColorFilter.tint(tintColor, BlendMode.SrcIn)`.
  This is the *exact same* `SrcIn` compositing `Icon(tint = ...)` uses internally — chosen
  deliberately over `androidsvg`'s own `RenderOptions.css("path { fill: ... }")` mechanism (real,
  documented, and simpler on paper) specifically so every tinted glyph in the app — `Icons.Default
  .*` via `FlashcardsIconTile` and category SVGs via `FlashcardsVectorIconTile` — recolors through
  the identical code path, and so no `Color → CSS hex string` round-trip is needed.
  `svg.renderToPicture()` is called fresh per composition (cheap; not `remember`ed) since it must
  be sized to the current layout pass's pixel dimensions; the parsed `SVG` object itself *is*
  `remember`ed, keyed on the svg string, since parsing is the expensive, tint/size-independent part.

## Seed pipeline

`build_fixture.py` derives Category `id`/`name`/`order`/`subcategoryCount` automatically from
inbox folder slugs. `color`/`iconSvg` cannot be derived from a slug, and — unlike the first design
pass — are **not** hand-maintained Python dict literals either:

- `scripts/seed/assets/category-icons/{slug}.svg` (plain SVG) and
  `scripts/seed/assets/category-icons/{slug}.color` (plain-text hex string) — real files, one pair
  per category slug, versioned in git.
- `build_fixture.py`: for each category slug, look for a matching `.svg`/`.color` file. If found,
  include its contents in the fixture. If missing, **omit the key entirely** (never write `""`)
  and print a warning — this no longer hard-fails the build (the earlier fail-fast validation for
  these two fields is dropped, since absence is now an expected, non-error state).
- `seed_firestore.py`: the `categories` collection gets its own bespoke write path, separate from
  the generic `upsert()` shared by `subcategories`/`cards`. For each category, read its **existing**
  Firestore doc first, and only include `color`/`iconSvg` in the write payload if the fixture
  supplied a value **and** Firestore doesn't already hold a non-empty value for that field —
  Firestore, once populated (including by a manual console edit), is sticky and wins over the
  checked-in file from then on. Write via `set(payload, merge=True)` so any field omitted from the
  payload (because Firestore already has it, or because no local file exists) is never touched.
  This also fixes a real bug the first implementation pass had: a plain `set()` (full replace)
  would have silently wiped a category's already-curated `color`/`iconSvg` on every re-seed once
  those fields stopped being part of the Python-derived payload.
- Backfilling live categories (android/kotlin/python/data-science-python/machine-learning) is now just: add the
  `.svg`/`.color` files under `scripts/seed/assets/category-icons/`, run the seed pipeline — no
  manual Firestore console editing needed. The 4 checked-in `.svg` files were mechanically
  converted 1:1 from the earlier VD-XML assets (`android:pathData` → `d`, `android:fillType` →
  `fill-rule`, `viewportWidth`/`viewportHeight` → `viewBox`) since none of them used `<group>`
  nesting or `transform` — a manual re-export would be needed for any future icon that does.

## No Firebase Storage

Dropped entirely from this feature — no `storage.rules`, no `upload_category_icons.py`, no
Storage bucket, no auth-gating-vs-public-read tradeoff (moot: there's no network fetch of icon
data at all anymore). Deleted from the codebase; see "Implementation history" below.

Coil (`coil-compose`/`coil-network-okhttp`) stays in the project only where it was originally
provisioned — an as-yet-unused dependency in `feature/home`, forward-provisioned for a future
profile-avatar feature (`AuthUser.photoUrl`, not built yet). Untouched by this feature.

## Out of scope (for now)

- Subcategory-level icon/color.
- Reusing the parsed `SVG`/rendered `Picture` as background art in `CategoryDetailsScreen`'s top
  app bar or any other secondary context — anticipated per current product direction, not designed
  or built here.
- Any in-app category-management UI — icon/color are seeded via the seed pipeline / manual
  Firestore edits only.

## Implementation history

- **2026-08-08**: first implementation — SVG in Firebase Storage, loaded via Coil's SVG decoder,
  `iconUrl`/`color` mandatory non-null, "fail loud" on malformed/missing data, Storage reads
  auth-gated via a custom OkHttp interceptor attaching a Firebase ID token.
- **2026-08-18**: fully superseded by this document, after reconsidering the Storage+Coil approach
  from first principles (see "Icon format" above). The 2026-08-08 code was reverted rather than
  incrementally migrated, since nothing had been committed yet. Implemented same-day: `Category
  .iconXml` (Android VectorDrawable XML), first-party `XmlPullParser`/`ImageVector.Builder` parser,
  `FlashcardsVectorIconTile` via `Icon(tint = ...)`.
- **2026-08-19**: two real device-only bugs surfaced in the 08-18 VD-XML parser within its first
  day of use — `android.util.Xml.newPullParser()` silently enables
  `FEATURE_PROCESS_NAMESPACES` (breaking every `getAttributeValue(null, "android:...")` lookup),
  and `ImageVector.Builder.addPath`'s `fill` parameter defaults to `null` (drawing nothing at all,
  regardless of tint) — neither caught by compiling or JVM tests, only by running the app.
  Switched to plain SVG + `androidsvg` (`com.caverock:androidsvg-aar`): `Category.iconXml` renamed
  to `iconSvg`, the ~130-line hand-rolled parser deleted outright in favor of `SVG.getFromString()`,
  and `FlashcardsVectorIconTile` rewritten to rasterize via `Picture` + manual `BlendMode.SrcIn`
  `saveLayer` tinting (see Rendering). The 4 checked-in category `.xml` assets were mechanically
  converted to `.svg` (no `<group>`/`transform` usage to hand-migrate). Existing Firestore category
  docs were **not** migrated by this change — `iconSvg` values are assumed to be supplied manually
  going forward, per the same seed-pipeline sticky-field rule that already treats Firestore as the
  source of truth once populated.
