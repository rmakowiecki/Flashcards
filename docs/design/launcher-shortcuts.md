# Launcher Shortcuts

Launcher shortcuts that open a Category or a Subcategory straight from the device's home screen. A User pins one by hand from the Details screens, and the app keeps a dynamic set, shown on a long press of the app icon, that mirrors the User's Favorites.

## Targets

A shortcut targets either a Category, opening Category Details, or a Subcategory, opening Subcategory Details. These are the same screens normal navigation reaches.

- **Label**: the Category or Subcategory name, used as both the long and the short label.
- **Icon**: the owning Category's icon and color. A Subcategory has neither of its own, so its shortcut uses its parent Category's. The color is baked into the image because the launcher does not tint shortcut icons. If either value is missing, or rendering fails, the shortcut shows the app icon. See [category-icon-color.md](category-icon-color.md).
- **Identity**: each target has one fixed identity shared by pinned and dynamic shortcuts, so a target that is both pinned and a Favorite is one shortcut, and the dynamic sync refreshes the pinned copy too.

## Pinned shortcuts

Category Details and Subcategory Details each carry a top app bar action that pins a shortcut to that screen's Category or Subcategory ([browse.md](browse.md)). The tap first re-reads the target, so the shortcut gets the current name and icon. The system then shows its own placement confirmation, which the app cannot bypass.

- If the target no longer resolves, a snackbar says the shortcut could not be created.
- If the launcher does not support pinning, a snackbar says so instead of silently doing nothing.

A pinned shortcut keeps the label it was pinned with. Routing is by id, so a renamed target still opens correctly, but the label is only refreshed when the target is also a Favorite and the dynamic sync rewrites it. There is no screen for managing shortcuts; the User removes them from the launcher.

## Dynamic shortcuts

The dynamic set has no creation action. It mirrors the signed-in User's Favorites, most recently favorited first, cut to the most the launcher allows per app, a limit read from the platform at run time.

- **Live**: the set follows the Favorites for as long as a User is signed in, so favoriting or unfavoriting updates it straight away, and removed or renamed targets heal on the next update.
- **Signed out**: signing out clears the set at once. A Guest is not signed in for this purpose and has no dynamic shortcuts.
- **Failures**: if the Favorites cannot be resolved, the last set stays in place instead of being wiped. If the platform refuses an update, it is logged and the next update supersedes it.

## Opening a shortcut

A shortcut carries an id-based route to its target, read by the app at launch. It is resolved at Splash, and only when Splash is going to Main. If the device has not finished onboarding or no User is signed in, the route is dropped and the launch continues through onboarding or Login as usual; it is not applied afterwards. Nothing could check it before sign-in anyway, because Categories and Subcategories are not readable signed out. Where the target opens in the back stack is described in [navigation.md](navigation.md).

A route that no longer resolves lands on Main as an ordinary launch, with no error. This covers a deleted target, a Subcategory that now sits under a different Category than the route names, and a lookup that does not answer in time. A pinned shortcut to deleted content stays on the launcher until the User removes it, because content deletion is a rare admin action.

A shortcut tapped while the app is already running never discards what the User is doing, a running Study Session included: the target opens on top of the current screen.

## Related

- [navigation.md](navigation.md): launch routing through Splash and where a shortcut's target opens.
- [browse.md](browse.md): the pin action on the Details screens.
- [home.md](home.md): the Favorites the dynamic set mirrors.
- [category-icon-color.md](category-icon-color.md): shortcut icons.
- [ADR-0003](../adr/0003-category-and-subcategory-details-are-root-routes-above-the-tab-shell.md): why Details screens sit above the tab shell.
