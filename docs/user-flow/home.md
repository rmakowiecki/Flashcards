# User Flow — Home

Leaving the Home tab: Favorites, Recents and the first-session prompt. Behavior and states are in [docs/design/home.md](../design/home.md).

Category Details and Subcategory Details are full-screen screens above Main (no bottom nav), the same screens whether opened from Home, the Study tab or a launcher shortcut. Their internal flows are in [study.md](study.md).

```mermaid
flowchart TD

    %% Legend: (Screen)  [/Action/]  {Decision}  ([Entry/Exit])

    Home(HOME SCREEN\nLevel card · Streak and Daily Goal · Favorites carousel · Recently studied list)

    %% ── Empty state ───────────────────────────────────────────────
    Home -->|no Favorites and no Recents| EmptyCTA[/Tap first-session button/]
    EmptyCTA -.->|tab switch · no stack push| StudyTab[📚 Study tab]

    %% ── Favorites ─────────────────────────────────────────────────
    Home --> TapCategoryCard[/Tap Favorite Category card body/]
    TapCategoryCard --> CatDetails(CATEGORY DETAILS\nsee study.md)
    Home --> TapQuick[/Tap Quick session on a Favorite Category card/]
    TapQuick -->|Quick Session, Category only| Preview
    Home --> TapSubcategoryCard[/Tap Favorite Subcategory card body/]
    TapSubcategoryCard --> SubcatDetails(SUBCATEGORY DETAILS\nsee study.md)
    Home --> TapPlay[/Tap play on a Favorite Subcategory card/]
    TapPlay -->|single-subcategory session| Preview

    %% ── Recents ───────────────────────────────────────────────────
    Home --> TapRecent[/Tap Recent row/]
    TapRecent -->|replay: same Subcategory, fresh Quick sample,\nor the stored Custom Subcategories| Preview(PREVIEW STUDY SESSION\npast Study Mode and delivery preselected)
```
