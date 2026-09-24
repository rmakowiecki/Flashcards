# Runtime permissions go through one app-wide launcher and a PermissionGateway

> Status: accepted

## Decision

Every runtime-permission check and request in product code goes through one shared layer:

- **One launcher.** `PermissionLauncherHost` (`app/.../ui/permission/PermissionLauncherHost.kt`) is
  an Activity-scoped composable called once from `NavGraph.kt`. It is the only place in product code
  that launches `ActivityResultContracts.RequestPermission`. It collects
  `PermissionGateway.permissionRequests`, launches the system prompt, reads
  `shouldShowRequestPermissionRationale` right after the result, and reports the raw outcome back
  through `onPermissionResult`. The internal `VoiceDebugScreen` tool keeps its own inline launcher
  and is the one exemption.
- **Two use cases.** Screens never touch the platform permission APIs. A ViewModel observes a status
  with `ObservePermissionStatusUseCase` and asks for one with `RequestPermissionUseCase`, both thin
  pass-throughs over `PermissionGateway` (`core:domain`), implemented by
  `DefaultPermissionGateway` (`core:data/permission`), layered per
  [ADR-0051](0051-gateway-seams-are-repositories.md) and its Gateway-naming amendment.
- **Three statuses.** `PermissionStatus` is `Granted`, `Denied` or `PermanentlyDenied`. There is no
  "not requested yet" state: a never-asked permission reads as `Denied`, which the UI treats the
  same as a soft refusal (ask on the next tap).
- **Cold, no polling.** `observeStatus` reads the current status on every new collection and
  re-emits after each request result. It never polls. Each ViewModel re-collects it from an
  `onResume()` that its screen calls through `LifecycleEventEffect(ON_RESUME)`, which picks up
  changes made in system Settings and the end of a system prompt.
- **Status at rest needs no Activity.** OS-granted reads as `Granted` (and clears the flag below).
  Otherwise a persisted per-permission `permission_permanently_denied_*` boolean in the
  device-scoped `user_preferences` DataStore decides between `PermanentlyDenied` and `Denied`.
  **Only request results write that flag**: granted clears it, refused with rationale clears it
  (`Denied`), refused without rationale sets it (`PermanentlyDenied`).
- **Requests are serialized.** `request()` runs one at a time and returns `Granted` without
  prompting when the permission is already held.
- **Denied UI keeps a way to ask again.** Android 11+ reports a first prompt dismissed without an
  answer exactly like a permanent refusal, so that dismissal reads as `PermanentlyDenied`. We accept
  this false positive because it heals on the next request: the system still shows its prompt, and
  any answer other than a permanent refusal clears the flag. The consequence is binding: every UI
  that renders `PermanentlyDenied` must keep a re-request action next to its "Open settings" path.
  When a re-request comes back `PermanentlyDenied` from `PermanentlyDenied` (no prompt appeared),
  the screen shows a one-shot "still off" snackbar. A soft denial stays silent.
- **`AppPermission` grows only on real need.** The enum lists the permissions the product actually
  requests today (`RecordAudio`). A new entry arrives with the feature that needs it, together with
  its manifest mapping.

## Context

The microphone was first requested by an inline `rememberLauncherForActivityResult` in each screen
that needed it, with that screen's own rationale check and its own resume observer. Each copy had to
re-solve the same problems: telling a soft refusal from a permanent one, noticing a grant made in
system Settings, and surviving rotation mid-prompt. None of the resulting state was visible to any
other screen, so a permanent refusal on one screen went unnoticed on the next. Moving the mechanics
into one launcher and one gateway makes every screen a thin consumer of a shared, persisted
status.

## Alternatives considered

- **Per-screen `rememberLauncherForActivityResult` launchers** — rejected: duplicated mechanics in
  every screen, and the permission state stays invisible across screens.
- **Polling the permission status** — rejected: burns work while nothing changes; re-collecting on
  resume catches every change that can happen outside the app.
- **Reading status at rest through an Activity-bound API (`shouldShowRequestPermissionRationale`)** —
  rejected: needs an Activity in the data layer, and its value is only meaningful right after a
  prompt result, not at an arbitrary read.

## Consequences

- A new permission-using screen adds `ObservePermissionStatusUseCase` and `RequestPermissionUseCase`
  to its ViewModel, re-collects the status in `onResume()`, and never builds its own launcher.
- The launcher host must stay mounted for the whole Activity; a request made while it is not
  collecting waits in the request channel until it is.
- A `PermissionStatus` shown in UI is always derived from the observed flow, never latched from a
  request result, so it cannot go stale when the user changes the permission outside the app.
- The Android 11+ false positive means a user who merely dismissed the first prompt sees the
  "Open settings" state once. The mandatory re-request action is what keeps that recoverable
  without a trip to Settings.
