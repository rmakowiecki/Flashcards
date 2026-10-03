# User Flow — Home

Browsing recent sessions and bookmarked topics.

`CategoryDetails` and `SubcategoryDetails` are full-screen shared screens (no bottom nav). From the Home tab they use `HomeCategoryDetails` / `HomeSubcategoryDetails` route types (ADR-0003). Their internal flows are documented in [study.md](study.md).

```mermaid
flowchart TD

    %% Legend: (Screen)  [/Action/]  {Decision}  ([Entry/Exit])

    Home(HOME SCREEN\nGreeting · Favorites carousel · Recently studied list)

    %% ── Empty state ───────────────────────────────────────────────
    Home -->|no Favorites and no Recents| EmptyCTA[/Tap 'Start your first session'/]
    EmptyCTA -.->|tab switch · no stack push| StudyTab[📚 Study tab]

    %% ── Recents ───────────────────────────────────────────────────
    Home --> TapRecent[/Tap Recent row/]
    TapRecent -->|replay: same Subcategory, fresh Quick sample,\nor Custom Subcategories that still resolve| Preview(PREVIEW STUDY SESSION\npast Study Mode and delivery preselected)

    %% ── Favorites ─────────────────────────────────────────────────
    Home --> TapFavorite[/Tap Favorite card\nshows Subcategory + Category name/]
    TapFavorite --> SubcatDetails(SUBCATEGORY DETAILS\nsee study.md)
```
