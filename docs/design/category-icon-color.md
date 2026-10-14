# Category icon and color

Each Category carries its own icon glyph and accent color, so the app can tell Categories apart at a glance wherever one appears. Both are curated content stored on the Category itself, not shipped with the app, so a new Category gets its look without an app release.

## The data

A Category has two optional fields: an icon, held as a plain SVG document in text, and a color, held as an opaque `#RRGGBB` hex string. Both live on the Category document and arrive with the same read as its name and order, cached offline the same way, with no separate download and no loading state of their own. The exact fields are in [firestore-schema.md](firestore-schema.md).

Either field may be missing. The seed pipeline can create a Category before its art exists, so a missing icon or color is an expected, ongoing state rather than an error. A Category with neither is still a complete Category and is never dropped from any list.

The icon is a single-color silhouette. Any colors inside the SVG are ignored: the glyph is always recolored when it is drawn, so a multi-colored source flattens to one color.

The domain layer keeps both values as plain text. Parsing the color and the SVG happens only in the UI layer, which keeps graphics libraries out of the domain.

Subcategories have no icon or color of their own. A surface that shows a Subcategory uses its parent Category's glyph, and its color where the surface is colored at all.

## Rendering

**Fallbacks.** The icon and color fall back independently, and a missing value and a malformed one are treated the same way, without crashing:

- A missing or malformed color falls back to a themed default chosen by each surface.
- A missing or malformed icon falls back to a generic folder glyph, drawn in the same tint the real glyph would have had.

**Tinting.** The glyph is drawn through the same tint mechanism the app's built-in icons use, so every tinted icon in the app recolors the same way. Each surface picks the tint. Most use the Category's color:

- The icon tile on Category rows in Browse and on Favorite Subcategory cards on Home: the glyph in the Category color on a faint container of the same color.
- The small inline glyph that leads the Category line of a Recent on Home and of a Topic search result.
- The large faded watermark on a Favorite Category card on Home, whose title also takes the Category color.

Other surfaces show only the glyph and ignore the Category color, drawing it in their own tint instead. For example, the onboarding Favorite pick card tints the glyph with its own selected or unselected treatment, and the Preview hero draws it in white on its circle.

**Caching.** An SVG is parsed and rasterized once per size into an untinted mask, off the main thread. The mask is held in a cache shared across the whole process, and the tint is applied when the mask is drawn. One mask therefore serves every tint, so a selection change or a light/dark switch never re-renders it. Returning to Home or Browse draws every glyph already seen in the first frame. While a glyph that is not yet cached loads, a tile shows only its tinted container and an inline glyph keeps its space empty, so the fallback glyph never flashes in its place.

**Launcher shortcuts.** A shortcut icon is rendered from the same SVG with the same library, with the color baked into the image, because the launcher does not tint shortcut icons. It needs both values: the glyph sits on a tonal circle derived from the color. If either value is missing, or rendering fails, the shortcut shows the app icon. See [launcher-shortcuts.md](launcher-shortcuts.md).

## Seed pipeline

Icon and color cannot be derived from a Category's folder name, so they are versioned files in the seed assets: one SVG file and one plain-text color file per Category. An exporter's SVG output is used as is, with no conversion step.

When the fixture is built, each Category takes whichever of its two files exist. A missing or empty file leaves that field out entirely and prints a warning, but never fails the build.

When the fixture is seeded, Firestore wins once it holds a value. An icon or color that is already present on the Category document, whether seeded earlier or edited by hand in the console, is never overwritten by the checked-in file. Every other Category field is rewritten on each run, and a field left out of the write is never touched. A forced overwrite run replaces whichever of the two the checked-in files supply, and still leaves a field with no file alone. Adding missing art to a live Category is therefore just a matter of adding its files and running the seed pipeline. Replacing art the Category already holds needs the forced overwrite run (`--overwrite`).

There is no in-app way to edit a Category's icon or color.

## Related

- [firestore-schema.md](firestore-schema.md): the Category document fields.
- [ADR-0007](../adr/0007-firestore-collection-structure.md): the collection structure, including the optional icon and color fields.
- [ADR-0005](../adr/0005-branded-m3-design-system.md): why Category colors sit outside the app's fixed brand palette.
- [browse.md](browse.md) and [category-search.md](category-search.md): the Category rows and search results that show the icon.
- [home.md](home.md): the Favorite cards and Recents that show the icon and color.
- [launcher-shortcuts.md](launcher-shortcuts.md): shortcut icons.
