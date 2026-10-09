# Account Deletion completes on the device

> Status: accepted

## Context

[ADR-0059](0059-account-deletion-runs-server-side.md) deletes the account on the server. The device
still holds the deleted User's state: Firebase Auth's cached user, the Pending Session queue and the
dead-letter record (whose entries carry the deleted uid), Firestore's persistent cache, and Credential
Manager's state.

A complete device reset is expensive. Clearing Firestore's persistence needs a terminated client, and
a terminated `FirebaseFirestore` singleton throws on every later call, so it needs a process restart.
Settling a call whose answer was lost needs a probe of the account. Each of these brings its own edge
cases.

## Decision

The device handles the green path and the failures a User can act on, nothing more.
`DefaultAccountRepository.deleteAccount()`:

1. Fails with `NoConnection` if `NetworkAvailability` reports no internet. Nothing is touched.
2. Records the uid in a small marker file, then calls `deleteAccount`, with the Functions SDK's default
   70 s timeout. If the marker cannot be saved, it fails with `ServiceError` without calling the server.
   The server's shared function timeout is 60 s, so the SDK does not give up while the function still
   runs.
3. On success, removes the deleted uid's Pending Sessions and dead letters, then signs out. A purge
   that fails is logged and never stops the sign-out. The Account screen then navigates to Login, the
   same way Sign out does.
   The marker is cleared last.
4. On any failure, reports `NoConnection` or `ServiceError` and changes nothing else on the device. A
   failure whose answer is unknown keeps the marker, since the server may still delete or may already
   have deleted: a dropped connection, a timeout (`DEADLINE_EXCEEDED`), an unavailable server
   (`UNAVAILABLE`) or a cancelled call. Any other failure clears it.

If the process dies mid-deletion, or the answer is unknown, the server may still finish. At the next start,
`InterruptedAccountDeletionCompleter` runs in `FlashcardsApplication.onCreate`, before any signed-in
work starts and before the start screen is chosen. Finding a marker, it signs out the marked User if
they are still signed in, then removes their Pending Sessions and dead letters and clears the marker in
the background. It does not ask the server whether the account is gone: if the deletion failed, the
User signs in again and loses nothing. Another signed-in User is never signed out, so a marker that
cannot be deleted does no harm.

Not handled, by choice: settling a lost answer before the next start, Firestore writes still pending when the deletion starts, and a
reset of Firestore's cache and Credential Manager's state.

## Consequences

- If the app dies while the server deletes, the next start opens Login. If the deletion actually
  failed, the User is signed out of an account that still exists.
- Signing in again with the same Google account before the server finishes signs in to the old
  account, which the server then deletes.
- If the server deleted the account but the answer was lost, the User sees a failure and stays signed
  in until the next start or Firebase's next token refresh, within an hour, whichever comes first. A
  retry fails, since the server rejects the revoked token.
- If the answer was unknown and the deletion actually failed, the next start signs out an account that
  still exists and drops the Pending Sessions queued since.
- A device that cannot save the marker, such as one with a full disk, cannot delete the account until
  it can.
- A Firestore write still pending when the deletion starts, such as an offline Favorite, can
  re-create a document under `users/{uid}` once it reaches the server.
- The deleted User's cached Firestore documents stay on the device, as after an ordinary sign-out.
  They are out of reach of another User's queries, and Firestore's cache eviction removes them over
  time.
- Credential Manager may offer the deleted Google account first at the next sign-in. Signing in with
  it creates a fresh User.
- Device-wide preferences, such as study settings and the Daily Goal, are kept.
