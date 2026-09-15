# Launcher Shortcuts

## Purpose

Home screen shortcuts (both user-pinned and long-press dynamic) that deep-link directly into a specific Category or Subcategory within the app.

## Shortcut types

| Type | Label | Deep-link destination | Created by |
|---|---|---|---|
| Category shortcut | Category name | Category Details screen | User (pinned) or auto (dynamic) |
| Subcategory shortcut | Subcategory name | Subcategory Details screen | User (pinned) or auto (dynamic) |

Both **pinned** and **dynamic** shortcuts are in scope for MVP (revised from the original pinned-only plan).

Future: destinations may change to Preview Study Session Screen (with the Category/Subcategory pre-selected) to reduce taps to session start. Not in current scope.

## Creation surfaces

**Pinned** — the dedicated top app bar icon (`Icons.AutoMirrored.Filled.AddToHomeScreen`, currently stubbed) already shipped on:
- **Category Details screen** → `ShortcutManagerCompat.requestPinShortcut` for a Category shortcut
- **Subcategory Details screen** → `ShortcutManagerCompat.requestPinShortcut` for a Subcategory shortcut

(Supersedes the original overflow-menu (⋮) plan — the dedicated icon is what shipped and is the surface going forward.) The system shows its own placement-confirmation dialog; this is Android-native behavior and cannot be bypassed.

**Dynamic** — no user-facing creation action. The dynamic shortcut set is derived automatically from the user's favorited categories/subcategories (existing `onFavoriteToggle`), ranked most-recently-favorited first, truncated to `ShortcutManagerCompat.maxShortcutCountPerActivity` (queried at runtime, never hardcoded). Rebuilt from a fresh favorites read on every app start in `AppStartViewModel`, so removed/renamed favorites self-heal without any explicit cleanup step.

No dedicated shortcut management screen for either type.

## Deep-link routing

No URI scheme, App Links, or `NavDeepLink` infrastructure — there is exactly one internal producer (this feature) and one internal consumer (app startup), so a minimal mechanism is used instead: a custom Intent action plus a typed extra carrying the existing tab-prefixed route string (ADR-0003), read by `MainActivity`/`AppStartViewModel` and resolved to a Compose navigation destination.

- Category: `/study/category/{categoryId}`
- Subcategory: `/study/category/{categoryId}/subcategory/{subcategoryId}`

These routes resolve to the same `CategoryDetails` and `SubcategoryDetails` composables used in normal navigation.

**Stale target:** if the id no longer resolves (category/subcategory deleted or renamed server-side), fail silently and land on BrowseScreen — no error toast. The dynamic set self-heals on the next rebuild; pinned shortcuts to deleted content are left dangling until the OS/user removes them (no programmatic cleanup planned — content deletion is an admin-driven, rare event).

**Auth gate:** if the user is logged out when a shortcut is tapped, the pending route is carried through Splash → Login, and applied once login succeeds. If the user backs out of Login without completing it, the pending route is discarded — no retry, no persistence across later sessions.

## Shortcut metadata

- **Label:** Category name or Subcategory name (displayed under the shortcut icon on the launcher)
- **Short label:** Truncated version (≤10 chars) for launchers with limited space
- **Icon:** the owning Category's `iconSvg` + `color` (Subcategory has no icon fields of its own — its shortcut inherits its parent Category's glyph/color unchanged). Rasterized headlessly via Coil's `ImageLoader.execute()` (same decoder used for on-screen rendering) since dynamic-set rebuild happens in a ViewModel with no Composable on screen; `color` is baked into the bitmap by compositing before handing it to `IconCompat.createWithBitmap()`, since launcher-rendered shortcut icons are static and get no runtime tint from the OS.

## Android API

`ShortcutManagerCompat` (API 26+ throughout the app, so no version gating needed): `requestPinShortcut` for user-pinned shortcuts, `setDynamicShortcuts` for the auto-derived favorites-based set.
