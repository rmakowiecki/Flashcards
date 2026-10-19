# Account

The Account screen holds everything about the signed-in User that is not a study preference: who they are signed in as, signing out, support and bug reporting, legal and version information, and **Account Deletion**. It opens from the avatar in the Settings top app bar ([settings.md](settings.md)), full-screen above the tab shell, and leads to two screens of its own: Report a bug and Open-source licenses. All three live in `feature:account`.

## Header

The top of the screen is a collapsing header built like a large app bar.

- **Expanded**, it shows the User's avatar (their profile photo or, without one, their initials) beside their name and email. Under both is a line naming the sign-in provider, Google or GitHub, with a Manage link that opens that provider's account page in the browser. For Google, the page opens on the signed-in account even when the device holds several.
- **Collapsed**, a smaller avatar and the name sit in the pinned row between the back arrow and Sign out. The email and the provider line fade out and stop being announced on the way, which also takes the Manage link out of reach.
- The header collapses as the list scrolls, and it can also be dragged directly. Released part-way, it settles on whichever end is nearer.
- A GitHub User's name is their GitHub Name, or their GitHub username when they have not set one ([ADR-0066](../adr/0066-github-sign-in-runs-as-a-firebase-provider-flow-in-the-data-layer.md)).
- A name, email or provider the User does not have leaves its line out rather than showing a blank. While the screen leaves after signing out, the header keeps showing the last User instead of emptying.
- Sign out sits in the pinned row in the error color. Expanded it has a label; collapsed only its icon remains, and a screen reader announces it the same way in both states.

## Sections

Below the header the screen is one scrolling list in three groups.

- **Support**: Contact support and Report a bug.
- **About**: Privacy policy, Open-source licenses and App version.
- **Danger zone**: Delete account, in the error color.

A link that no app on the device can open (no browser, no email app) shows a snackbar instead of doing nothing.

### Contact support

Opens an email draft to the support address in the User's email app. The body starts with the support details: app version, device model, Android version and the User's account id. Two empty lines follow, so the User starts typing below the details.

### Privacy policy and App version

- **Privacy policy** opens the hosted policy in an in-app browser tab that follows the app's light or dark theme.
- **App version** shows the version name and build number. Tapping the row copies them to the clipboard.

## Sign out

Sign out asks for confirmation first. On confirm the confirmation stays open and takes no further input until Login appears. The User is signed out and the app goes to Login, clearing everything above it ([navigation.md](navigation.md)). The app goes to Login even if signing out fails, so a User is never stuck signed in.

Signing out removes nothing from the device:

- The User's **Pending Sessions** stay queued and are delivered the next time that same User signs in on this device ([session-submission.md](session-submission.md)).
- Study preferences belong to the device and carry over to the next User ([settings.md](settings.md)).
- The dynamic launcher shortcuts are cleared ([launcher-shortcuts.md](launcher-shortcuts.md)).

## Account Deletion

Delete account opens a destructive confirmation dialog. There is no re-authentication step: the dialog is the only gate. The reasons are in [ADR-0059](../adr/0059-account-deletion-runs-server-side.md).

1. **Offline**: if the device has no internet, a snackbar says so and nothing is touched.
2. **Deleting**: a blocking progress overlay covers the screen. Back, the header's buttons and every row take no input until the deletion ends. The server deletes the User's data, their Bug Reports and their sign-in account ([ADR-0059](../adr/0059-account-deletion-runs-server-side.md)). What that covers in Firestore is in [firestore-schema.md](firestore-schema.md).
3. **Deleted**: the device drops the User's Pending Sessions, signs out and goes to Login, the same way Sign out does. The overlay stays until Login appears.
4. **Failed**: the overlay goes away and a snackbar reports either no connection or a general failure. The User is still signed in and can try again.

If the app dies during the deletion, or the answer is lost to a dropped connection or a timeout, the server may still have finished. At the next start the device treats the User as deleted without asking the server. It signs them out if they are still signed in and drops their Pending Sessions. If the deletion had actually failed, the User signs in again and has lost nothing ([ADR-0060](../adr/0060-account-deletion-completes-on-the-device.md)).

Signing in again after a deletion, even with the same provider account, starts a fresh User with no history. Device preferences are kept.

## Report a bug

Report a bug is a full-screen form for writing a **Bug Report**. Close sits at the start of the top app bar and Send at the end.

- **Severity**: a field that opens a dialog with three choices, each with a short description: Blocker, Minor and Cosmetic. The dialog's Confirm stays disabled until a choice is made, and cancelling keeps the earlier choice.
- **Description**: a multi-line text field under three guiding questions: what the User was doing, what happened against what they expected, and how reliably it reproduces. Enter adds a new line rather than sending. A counter under the field shows the length against the minimum until the text reaches it, then against the maximum, and flags text that is too long. Length is counted on the trimmed text.
- An info banner says that app version, device, Android version and the User's account are attached automatically.

Send is enabled only with a severity chosen and a description within the length bounds. The server enforces the same bounds.

- **Sending**: the form locks, Send shows a spinner, and Close and system back are ignored.
- **Sent**: a confirmation snackbar shows and the screen closes on its own a moment later. Close leaves straight away.
- **Failed**: the form unlocks with the text kept, and a snackbar says either that there was no connection or that the report could not be sent. A send that takes too long counts as no connection. A retry after a lost answer can send the same report twice.
- The server limits how many reports one User can send in a day. Going over that limit shows the same general failure as any other error ([ADR-0058](../adr/0058-bug-reports-written-only-by-a-callable.md)).

Close or system back with text in the description asks whether to discard the report; with no text it just closes.

## Open-source licenses

A list of every library the release build ships, generated at build time, with each library's version and license. Tapping one opens a detail sheet with what the library is, its license text when the build bundled it, or a link to the license's web page when it did not, plus links to the library's website and source. The open sheet survives process death. A link no app can open shows a snackbar.

## Related

- [settings.md](settings.md): the avatar that opens this screen, and the device preferences that survive sign-out and deletion.
- [navigation.md](navigation.md): leaving for Login.
- [session-submission.md](session-submission.md): Pending Sessions and whose sessions a delivery run sends.
- [launcher-shortcuts.md](launcher-shortcuts.md): dynamic shortcuts cleared on sign-out.
- [firestore-schema.md](firestore-schema.md): the Bug Report document and what Account Deletion removes.
- [ADR-0058](../adr/0058-bug-reports-written-only-by-a-callable.md): Bug Reports are written only by a server function.
- [ADR-0059](../adr/0059-account-deletion-runs-server-side.md): Account Deletion runs on the server.
- [ADR-0060](../adr/0060-account-deletion-completes-on-the-device.md): what the device does before and after it.
