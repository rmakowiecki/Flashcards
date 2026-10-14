# Category and Subcategory Details are root routes above the tab shell

## Decision

Category Details and Subcategory Details each have **one route type**, registered once in the root `NavHost` next to `Main`, not inside any tab's graph. Home, the Study tab and launcher shortcuts all navigate to the same route, and the screen opens full-screen above Main, with no bottom navigation bar.

Launcher shortcuts carry their target as an internal route string, `/study/category/{id}` or `/study/category/{id}/subcategory/{id}`. It is not a Navigation deep link: `SplashViewModel` parses it with `ShortcutRoute` and navigates to the typed route itself. The `/study/` prefix is fixed only because shortcuts already pinned on a device carry it. It does not mean the screen opens in the Study tab.

## Why

- **A route type can be registered only once per `NavHost`.** Putting Details in both the Home and Study graphs would need two route types for one screen. Registering one type at the root avoids that.
- **Details are full-screen everywhere.** Like every screen outside Main, they show no bottom navigation bar. A root route gives this without hiding the bar per destination.
- **Each tab's state stays intact.** Details sit above Main, so opening one never pushes onto a tab's stack. Back returns to Main on the tab the User left, with that tab's state restored.
- **One entry point for shortcuts.** A shortcut opens the same route on top of Main as an in-app tap would, so there is no shortcut-only copy of the screen.

## Considered and rejected

- **Tab-prefixed route types** (a Home and a Study copy of each Details route, each owned by its tab's graph). This was the original design. It doubled the route types and the navigation code for one screen, and it tied each Details visit to a tab's back stack.
