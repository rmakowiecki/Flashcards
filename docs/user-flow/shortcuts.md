# User Flow — Launcher Shortcuts

Pinning a shortcut, keeping shortcuts in sync with favorites, and opening the app via a shortcut tap. Grilled 2026-09-16 (tickets 01–05 ready-for-agent; two edge cases below are NYI, design pending).

## Pinning a shortcut

```mermaid
flowchart TD
    CatDetails(CATEGORY DETAILS SCREEN) --> TapAddCat[/Add to home screen/]
    SubcatDetails(SUBCATEGORY DETAILS SCREEN) --> TapAddSubcat[/Add to home screen/]

    TapAddCat --> Resolve[App resolves name, icon, color\nfor the shortcut]
    TapAddSubcat --> Resolve

    Resolve --> Supported{Launcher supports\npinning shortcuts?}
    Supported -->|no| Snackbar[Snackbar: can't pin a shortcut here]
    Supported -->|yes| PinDialog(Android system\nshortcut pin dialog)
    PinDialog -->|user confirms| Pinned([Shortcut placed\non home screen])
    PinDialog -->|user cancels, from category| CatDetails
    PinDialog -->|user cancels, from subcategory| SubcatDetails
```

## Keeping shortcuts in sync

> Runs continuously in the background while signed in — favoriting or unfavoriting updates the home screen shortcuts without restarting the app.

```mermaid
flowchart TD
    AppOpen([App opens]) --> SignedIn{Signed in?}
    SignedIn -->|no| NoSync[No dynamic shortcuts synced\nuntil signed in]
    SignedIn -->|yes| WatchFavorites[Watch favorited categories/subcategories\nmost-recently-favorited first]

    WatchFavorites --> FavoriteChange[/User favorites or unfavorites\na category/subcategory/]
    FavoriteChange --> UpdateShortcuts[Dynamic shortcuts\nupdated to match]
    UpdateShortcuts -.-> WatchFavorites

    SignedIn -->|user signs out| StaleShortcuts[Dynamic shortcuts stay as-is\nuntil next sign-in]
```

## Opening the app from a shortcut

```mermaid
flowchart TD
    ShortcutTap([Tap a pinned or dynamic shortcut]) --> Splash(SPLASH SCREEN)
Splash --> StillValid{Shortcut's topic\nstill exists?}

StillValid -->|no, e.g. deleted| Home(HOME SCREEN\nnormal landing)
StillValid -->|yes| SignedIn2{Signed in?}

SignedIn2 -->|yes| DirectOpen([Opens directly at\nCategory/Subcategory Details])
SignedIn2 -->|no| LoginScreen(LOGIN SCREEN)
LoginScreen -->|signs in| Home2(HOME SCREEN\npendingRoute not resolved after sign-in NYI)


ShortcutTap -->|"app already running\n(warm start)"| Forward[Running app comes forward]
Forward --> InSession{Study session\nrunning?}
InSession -->|yes| Session(STUDY SESSION\nshortcut dropped)
InSession -->|no| WarmCheck{Signed in, past Login,\ntarget resolves and\nis not already on top?}
WarmCheck -->|no| Unchanged([Current screen stays,\nshortcut dropped])
WarmCheck -->|yes| WarmOpen([Category/Subcategory Details\nopens on top, back returns])
```
