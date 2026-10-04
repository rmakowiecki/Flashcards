# Bug Reports are written only by a callable

> Status: accepted

## Context

A Bug Report is a User's free-text description of a problem, a severity they pick, and the app's
diagnostics (app version, device model, Android version). Reports are stored for the developer, and
later an investigation agent, to triage. They live in a top-level `bugReports` collection, with the
reporter's uid as a field, so they can be browsed and queried in one place.

Other User-written documents, such as Favorites and Curation Requests, are written by the client
directly under security rules. Bug Reports could follow that pattern with a `create` rule that
checks the shape. It would not be enough:

- **Rate limiting.** Free text from any signed-in User is an abuse surface. Rules cannot count a
  User's earlier documents, so they cannot cap how many reports one User sends.
- **The revoked-token guard.** A client write is accepted for as long as the ID token is valid,
  even after its User was revoked or deleted. The callable runs the guard of
  [ADR-0057](0057-callables-that-write-user-data-verify-token-revocation.md) first.
- **Server-set fields.** `uid`, `createdAt` and `status` must not be chosen by the client. Rules can
  check them, but a callable sets them and the client never sends them.
- **Validation without an app release.** Bounds checked on the server can change with a functions
  deploy. Bounds in rules can too, but every installed client also writes the document shape, so a
  shape change in rules breaks older clients.

## Decision

The `submitBugReport` callable is the only writer of `bugReports`. Security rules deny every client
read and write on the collection, the reporter's own included. Only the Admin SDK reads it and moves
`status` from `new` to `triaged` or `fixed`.

The callable runs, in order, and writes nothing if any step fails:

1. The revoked-token guard (`unauthenticated`).
2. Validation (`invalid-argument`). The request has exactly the keys `description`, `severity`,
   `appVersionName`, `appVersionCode`, `deviceModel` and `androidVersion`:
   - `description` is trimmed, then must be 20 to 1000 characters; the trimmed text is stored;
   - `severity` is `blocker`, `minor` or `cosmetic`;
   - `appVersionName` is 1 to 50 characters, `deviceModel` 1 to 100;
   - `appVersionCode` is an integer above 0, `androidVersion` an integer of at least 24, the app's
     minimum SDK.

   The description limits duplicate the constants on the client's `BugReport` domain model, so the
   client can tell the User before sending. Both change together.
3. The rate limit (`resource-exhausted`): at most 10 reports per uid with a `createdAt` in the last
   rolling 24 hours, counted with an aggregate `count()` query over a composite index on `uid` and
   `createdAt`.
4. One document with an auto id, adding `uid` from the verified token, `createdAt` as a server
   timestamp and `status: "new"`.

It returns nothing on success.

## Consequences

- The count and the write are not one transaction, so two concurrent submissions at 9 reports can
  both pass and leave 11. The limit stops abuse, not an exact quota, so the slack is accepted.
- The client shows the same generic error for `resource-exhausted` as for any other failure.
- Each report costs one Auth read, one aggregate query and one write.
- New reports are checked in the Firestore console. There is no notification.
- Deleting a User's account has to delete their Bug Reports too, since they live outside
  `users/{uid}`.
