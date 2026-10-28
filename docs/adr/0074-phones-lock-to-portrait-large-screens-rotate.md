# Phones lock to portrait, large screens rotate

> Status: accepted

## Context

The app is designed for portrait phones. Android decides separately what a declared orientation means
on a large screen (smallest width of 600dp or more, which covers tablets and unfolded foldables). For an
app targeting API 36 or higher, Android 16 and later ignore `screenOrientation` and
`setRequestedOrientation()` there, unless the user opts the app back in from the device's aspect ratio
settings. They also ignore `resizeableActivity="false"` and aspect-ratio limits there; only games are
exempt. Android 8 to 15 still honor all of them. A manifest-only lock therefore locks tablets on older Android
versions and rotates them on newer ones, so the same tablet behaves differently by OS version.

## Decision

**Phones are locked to portrait; large screens are never locked.** The rule is the same on every
Android version and does not depend on the target SDK.

**Manifest plus a runtime decision.** Every app activity declares `android:screenOrientation="portrait"`,
so a phone is locked before its first frame. Each activity's `onCreate` then calls
`lockPortraitOnPhones()` from `:core:ui`, which always sets `requestedOrientation`: `portrait` when the
smallest screen width is under 600dp, `fullUser` otherwise. The width is the whole screen's, not the app
window's, so a large screen showing the app in a small split or free-floating window is still never
locked. The helper decides on its own, so an activity
that forgets the manifest line is still locked on phones, at worst after one relaunch. A fold, an unfold
or a window resize recreates the activity, because none declares `configChanges`, so the decision runs
again for the new screen.

A tablet on Android 15 or older that launches while held in landscape starts in portrait and is then
unlocked. That relaunch is accepted: tablets are the minority and Android 16 and later never lock them.

**`fullUser` when unlocked.** It allows all four rotations and respects the user's rotation lock, which
`unspecified` leaves to each device's own policy.

**All app activities, one helper.** `MainActivity` and the debug-only showcase and card-animation
activities all call it. A Konsist rule fails when an activity's `onCreate` does not call it after
`super.onCreate`. Third-party activities, such as the sign-in pages and debug tools, keep their own
orientation.

**Portrait means `portrait`.** Not `userPortrait` or `sensorPortrait`: reverse portrait is rare on
phones and the lock stays simple.

## Considered options

- **Manifest lock only.** Rejected: tablets are locked on Android 15 and older and free on Android 16
  and later.
- **Manifest lock plus the API 36 opt-out property.** Rejected: it expires once the app targets API 37,
  and a portrait-locked tablet is letterboxed and ranked lower on Play.
- **Excluding tablets from the Play listing.** Rejected: it removes users instead of handling them.
- **Runtime decision only, with no manifest lock** (Google's large-screen cookbook recipe). Rejected: a
  phone launched while held in landscape may start in landscape and be relaunched into portrait,
  flashing the screen. The phone is the target device.
- **A resource bool qualified by `sw600dp`.** Rejected: the same 600dp rule reads more plainly as one
  comparison next to the decision it drives, and needs no resource files.
- **One `ActivityLifecycleCallbacks` for every activity in the process.** Rejected: it would also touch
  third-party activities, and a translucent one that requests an orientation crashes on API 26, the
  app's `minSdk`.

## Consequences

Tablets and unfolded foldables rotate on every Android version, and the app has no landscape layout.
Large-screen landscape is a separate design task. An activity that starts handling `configChanges`
itself must also re-run the helper when the screen size changes.
