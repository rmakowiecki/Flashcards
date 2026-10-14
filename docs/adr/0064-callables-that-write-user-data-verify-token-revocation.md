# Callables that write user data verify token revocation

> Status: accepted

## Context

`onCall` verifies the caller's Firebase ID token before any handler code runs, but only its
signature and expiry. It does not check revocation. An ID token stays valid for up to one hour after
it is issued, even if its User signs out everywhere (`revokeRefreshTokens`) or is deleted in that
hour. Cloud Functions write through the Admin SDK, which bypasses security rules, so the rules cannot
stop such a write either.

Once a User can delete their account, this window matters. A Study Session submission that reaches
`submitStudySession` during or just after the deletion would re-create documents under
`users/{uid}` for a User who no longer exists.

## Decision

### One guard, called by every writing callable

`requireActiveSession(auth, request)` in `functions/src/lib/authGuard.ts` rejects the caller with
`unauthenticated` when:

- the request has no auth context;
- the Firebase Auth user no longer exists, or is disabled;
- the token's `auth_time` is earlier than the User's `tokensValidAfterTime`, which
  `revokeRefreshTokens` moves to the time of revocation.

Otherwise it returns the caller's uid. These are the checks `verifyIdToken(token, true)` performs,
done on the token `onCall` already decoded, so the guard costs one `getUser` read and no token
round trip.

**Every callable that writes user data calls the guard before its first write.** `submitStudySession`
does so from now on. Callables that write no user data (`entitlement`,
`transcribeAndGradeSpokenAnswer`) do not call it, and do not pay for the extra read.

The guard takes the Auth instance as a parameter, so tests run it against the Auth emulator.
`submitStudySession`'s whole handler lives in `handleSubmitStudySessionCall` next to the transaction,
so a test can check that a revoked or deleted caller writes nothing.

### `unauthenticated`, not `permission-denied`

The rejection uses the same code as a missing token. The Android delivery worker already treats
`UNAUTHENTICATED` from `submitStudySession` as transient and keeps the Pending Session queued (see
`SessionSubmissionDeliveryWorker`). That is the right outcome:

- After a revocation, the User must sign in again. The new token's `auth_time` is later than the
  revocation, so the queued session is delivered then and nothing is lost.
- After an account deletion, the session belongs to a User who no longer exists. It stays queued and
  is never delivered, which is what deletion should mean.

No client change is needed.

## Consequences

- Each guarded call makes one extra Auth read before its work.
- The guard closes the one-hour window for revoked and deleted Users. It does not cover a write that
  passes the guard just before the User is deleted. A deletion flow has to handle that race itself,
  for example by deleting the User's data again after revoking their tokens.
- The Functions test suite starts the Auth emulator next to the Firestore emulator.
- This extends the server-authoritative session commit of [ADR-0014](0014-session-stats-written-at-summary-screen.md).
