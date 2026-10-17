# Premium is auto-granted when an Auth user is created; paid functions refuse anonymous callers

> Status: accepted. Temporary: the auto-grant goes away once Premium comes from purchases ([planned/voice-answering.md](../design/planned/voice-answering.md)). The refusal of anonymous callers stays.

## Context

For the first release every User gets **Premium**, but that must stay a backend decision the app knows nothing about, so it can be withdrawn without an app release. The app never writes a `users/{uid}` root document, so no Firestore trigger fires for a new User. Linking a Guest to Google or GitHub keeps the Guest's uid and creates no Auth user, so an Auth creation trigger only sees the Guest, never the moment it links. Most new Users arrive as linked Guests.

## Decision

- A 1st-gen Auth `onCreate` function grants Premium to every new Auth user, a Guest included: it creates `users/{uid}/entitlement/premium` with `isPremium: true` and `source: "auto-grant"`. It uses `create()`, so it never overwrites an existing record. No app code calls or names it.
- Every paid function refuses an anonymous caller before its Premium check, whatever the record says. The provider is read from the Auth user record (`getUser`), not the ID token, so a token minted before the Guest linked cannot misreport it. A Guest's grant therefore unlocks nothing until it links.
- The app applies the same rule when it decides what to offer: a Guest is never shown as Premium.
- Admins grant and revoke by editing the record in the Firebase console. Revoking sets `isPremium: false` and never deletes the record. Users created before this function are not backfilled.

## Alternatives considered

- **A Firestore trigger on the User's root document.** Rejected: no such document is ever written.
- **A blocking `beforeUserSignedIn` function**, which also fires on linking. Rejected: it requires upgrading the project to Identity Platform, a paid tier with no documented way back, and an error in it would block sign-in.
- **A scheduled sweep granting Users without a record.** Rejected: a polling job for a one-time event, and up to a minute's delay after sign-up.
- **Granting only non-anonymous users at creation.** Rejected: a linked Guest would never be granted.

## Consequences

- Every anonymous sign-up costs one function run and one write, including Guests discarded at Login. Firebase Auth's own anonymous sign-up rate limits bound the abuse.
- A revoked User who deletes their account and signs in again is a new User and gets Premium again. Accepted while the auto-grant exists.
- Removing the auto-grant is deleting one function. Grants already written stay until an admin changes them.
