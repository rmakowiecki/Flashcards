# App Check on the callables

> Status: accepted

## Context

Every callable checks the caller's Firebase ID token, but anyone can get one: the Firebase config ships
inside the APK, and a script can sign in as a Guest or with a real Google account and call
`transcribeAndGradeSpokenAnswer`, which spends ElevenLabs and Vertex AI money on every call. Auth proves
who is calling, not that the call comes from this app.

## Decision

**Firebase App Check on the four callables.** Every call carries an App Check token next to the ID
token; once enforced, a call without a valid one is rejected as `unauthenticated`.

**Play Integrity in release builds.** A release build attests through `PlayIntegrityAppCheckProviderFactory`.
App Check requires the `PLAY_RECOGNIZED` app verdict, its default, and the `MEETS_DEVICE_INTEGRITY`
device verdict, which it does not check by default. Only a copy Google Play recognizes, signed with the
Play app-signing key, on a device that meets device integrity, gets a token. Rooted devices, custom
ROMs, emulators and devices without Google Play services lose the callables. The `LICENSED` verdict is
not required: the app is free, so anyone can hold a license.

**Debug tokens in debug and profiling builds.** Both use `DebugAppCheckProviderFactory`; each
install's secret is registered by hand under the `.debug` app and is lost on uninstall or "clear
data". Only the developer's own devices run these builds; anyone else installs from a Play testing
track.

**The provider is chosen by source set.** `src/debug` and `src/release` each hold one factory function;
profiling compiles `src/debug`. The debug provider's library never ships in a release build.

**Installed first in `Application.onCreate`.** The provider is installed before anything can call a
callable, since `SignedInWorkRunner` may deliver Pending Sessions right away.

**Enforcement follows the client.** The client ships first while the callables only record App Check
verdicts. Enforcement turns on once the logs show valid tokens, so no deployed server ever rejects a
client that has not yet learned to send them.

**Firestore: monitored, enforced later.** Firestore's App Check metrics are watched during the closed
test, and enforcement turns on once unverified requests stay near zero. Sign-in itself does not
require App Check.

**No replay protection.** A token is a bearer credential reused for its lifetime (1 hour, refreshed
automatically): a leaked one works from any client until it expires, but still needs a valid ID token
beside it. Consuming one per call would cost an attestation per callable request, against a
10,000-a-day quota.

## Alternatives considered

- **No App Check in debug builds.** The server cannot tell a debug build from a script, so the
  callables would have to accept calls without a token, which leaves them open.
- **A uid allowlist for debug builds.** Any Guest uid can be created by a script, and the list would
  need maintaining by hand like the debug tokens, with less protection.
- **A separate development Firebase project.** Would keep debug builds off the production callables,
  but doubles every deploy, secret and seed for one developer.

## Consequences

- A release build Google Play does not recognize (`installRelease`, internal app sharing) fails every
  callable. Release builds are tested only through a Play track. A copy of the Play build sideloaded
  onto another device that meets device integrity still gets a token.
- A new developer device fails every callable until its debug token is registered: Pending Sessions
  stay queued, and Voice Answering, Bug Reports and Account Deletion fail.
- An App Check rejection reaches the User through the same error paths as any other
  `unauthenticated` failure, with no message of its own.
- Play Integrity's standard tier allows 10,000 attestations a day; each install attests about twice an
  hour while in use, since the SDK refreshes a token at about half its 1-hour lifetime.
