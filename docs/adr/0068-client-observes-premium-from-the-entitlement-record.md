# The app observes Premium from the entitlement record; the `entitlement` callable is deleted

> Status: accepted. Supersedes [ADR-0029](0029-voice-grading-two-callables-payload-inferred-debug-mode.md) §1 and §6 (the `entitlement` callable) and its rejected alternative "read `users/{uid}/entitlement/premium` from Firestore directly on the client".

## Context

The app must know whether a User is **Premium** before offering a Premium feature, starting with Voice Answering, and must react when that changes, the way it already reacts to sign-in. The server already checks Premium itself before every paid call. ADR-0029 kept an `entitlement` callable as the seam for a proactive gate and rejected a direct client read of the record.

## Decision

- The entitlement record becomes readable, never writable, by its owner in `firestore.rules`. The app holds a snapshot listener on it, keyed on the signed-in uid, and exposes Premium as an observable flow app-wide, alongside the signed-in User.
- The `entitlement` callable and its client path are deleted. Server functions keep reading the record directly through `isPremiumUser`; none calls another function to learn Premium.
- The record stays server-authoritative: only the Admin SDK writes it, and the server check before each paid call remains the enforcement. The app's view only decides what to offer.

## Alternatives considered

- **Keep polling the `entitlement` callable.** Rejected: it needs the network for every read and never pushes a revocation or a grant.
- **A custom claim on the ID token.** Rejected: a change reaches the app only when the token refreshes, up to an hour later, unless the app forces a refresh it has no signal to time.

## Consequences

- The app depends on the record's field names. Changing them is a coordinated client and server change, which ADR-0029 wanted to avoid.
- A grant or revocation shows in the app as soon as Firestore delivers the snapshot.
