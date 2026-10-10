# GitHub sign-in runs as a Firebase provider flow inside the data layer

> Status: accepted

## Context

Login offers two sign-in providers, Google and GitHub. Google sign-in is split across layers: the
`GoogleSignInLauncher` in `:feature:auth` opens Credential Manager's account picker, gets an ID token,
and hands the token down through `SignInWithGoogleUseCase` to the data layer, which exchanges it with
Firebase. No Android type crosses into `core:domain`.

GitHub has no Android SDK. Firebase runs GitHub's OAuth flow itself, in a Custom Tab, through
`startActivityForSignInWithProvider` and `startActivityForLinkWithProvider`. Both need an `Activity`,
and both sign the User in directly: no token comes back first for the feature layer to pass down. So
the launcher pattern cannot be reused, and an `Activity` must not cross into `core:domain`, which is
pure Kotlin.

The flow must also link a Guest instead of signing in (see **Guest** in `CONTEXT.md`), exactly as
Google does, and that logic already lives in `FirebaseAuthRemoteDataSource`.

## Decision

A `@Singleton` `CurrentActivityHolder` in `core:data` implements
`Application.ActivityLifecycleCallbacks` and is registered from `FlashcardsApplication.onCreate()`. It
holds the resumed Activity weakly and clears it when that Activity pauses.
`FirebaseAuthRemoteDataSource.signInWithGitHub()` reads it on the main thread, before any suspension,
and starts Firebase's provider flow with it. It reuses the same Guest-link and failure mapping as
Google.

The domain sees only `AuthRepository.signInWithGitHub(): SignInResult` and `SignInWithGitHubUseCase`.
`:feature:auth` calls the use case and has no Firebase dependency.

The flow requests only the `user:email` scope, so Firebase sees the User's primary email even when it
is private on GitHub. The GitHub access token Firebase returns is never read or stored.

### Rejected

- **`:feature:auth` calls Firebase directly**, as the Google launcher calls Credential Manager. This
  would duplicate the Guest-link logic in the feature layer and bring Firebase into a module that
  otherwise has no Firebase dependency.
- **An opaque host type in `core:domain`**, passed down from the screen and cast to `Activity` in the
  data layer. The cast leaks the platform through the domain while pretending not to.

## Consequences

- The data source has a hidden dependency: some Activity must be resumed when the User taps. On the
  Login screen one always is. If none is, the sign-in fails as `Unknown`, logged, and no flow starts.
- Firebase types stay inside `core:data`.
- The holder is the only `Activity` access in the data layer. A future data-layer call that needs one
  reuses it rather than adding another path.
