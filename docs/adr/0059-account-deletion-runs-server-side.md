# Account Deletion runs server-side

> Status: accepted

## Context

Account Deletion removes a User and everything written for them from Firebase. The public privacy
policy and account deletion page promise that nothing linked to a deleted account is kept. A User's
data lives in two places: every document under `users/{uid}` (progress, sessions, Recents, favorites,
entitlement, private flashcards, Curation Requests), and their Bug Reports in the top-level
`bugReports` collection, which carry the uid as a field.

The client cannot do this alone:

- **Recursive deletes.** The client SDK cannot delete a document together with its subcollections,
  and security rules deny client writes on most of `users/{uid}` and all of `bugReports`.
- **Recent sign-in.** The client's `user.delete()` fails unless the User signed in recently, which
  would force a re-authentication step into the flow.
- **Racing submissions.** Study submissions go through `submitStudySession` (see
  [ADR-0014](0014-session-stats-written-at-summary-screen.md)). One in flight while the data is deleted
  could write it back, and nothing on the client can stop that.

## Decision

A `deleteAccount` callable deletes the caller's account. It takes no payload, acts only on the
caller's uid and returns nothing. It runs, in order:

1. The revoked-token guard of
   [ADR-0057](0057-callables-that-write-user-data-verify-token-revocation.md) (`unauthenticated`). A
   token revoked from the console, or one whose User is already deleted, deletes nothing.
2. Delete every Bug Report whose `uid` is the caller's, queried in pages. Bug Reports are top-level,
   so the recursive delete in step 3 does not reach them.
3. Recursively delete `users/{uid}`.
4. Revoke the caller's refresh tokens. From here on the guard rejects every submission made with a
   token issued before this point.
5. Repeat steps 2 and 3. This sweeps anything a submission that passed the guard just before step 4
   wrote while steps 2 and 3 ran. When there is nothing to sweep it costs about two reads.
6. Delete the Auth user. A user that is already gone counts as success.

Every step is idempotent. Any failure ends the call with `internal`, logged with the uid and the step
that failed.

- **Revocation comes after the main deletes**, so a failure in steps 2 to 4 leaves the token valid and a
  plain retry passes the guard.
- **The Auth user is deleted last**, so a run that fails at any step leaves an account to delete the
  rest through.
- **There is no transaction.** Auth cannot join a Firestore transaction, and a User's documents can
  exceed a transaction's write limit. The ordering and idempotency stand in for atomicity.
- **There is no recent-sign-in requirement.** The guard proves the session is still active, and the
  confirmation dialog is the only gate before deletion.

## Consequences

- A failure in step 5 or 6 leaves the token revoked, so the same session cannot retry. The client's
  next token refresh fails and signs the User out. Signing in again gives a fresh token for the same
  uid, and a second deletion then completes. The failure log is the developer's fallback for cleaning
  up by hand.
- A submission that passes the guard just before step 4 and commits only after step 5's sweep leaves
  documents behind once the Auth user is gone, with no one able to delete them. The window is a few
  hundred milliseconds and needs a submission in flight at that moment. It is accepted: closing it
  would cost every submission an extra read of a deletion marker.
- A successful deletion is not logged.
- Each deletion costs one Auth read, the reads and deletes of every document the User has, the Bug
  Report queries, and two Auth writes.
