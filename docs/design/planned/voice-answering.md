# Voice Answering: entitlement sync

The unbuilt remainder of Voice Answering: filling the premium entitlement record from real purchases. The built feature is in [../voice-answering.md](../voice-answering.md).

## Why it is needed

Voice grading costs money on every answer, for transcription and two model calls. The server therefore grades only for a **Premium** User, and it never trusts a premium flag sent by the app ([ADR-0024](../../adr/0024-voice-grading-cloud-function-proxy.md)). Today every new account is granted Premium when it is created, and admins revoke it by hand ([ADR-0071](../../adr/0071-premium-auto-granted-on-auth-user-creation.md)). Selling it needs the record kept in step with the User's subscription instead.

## What it must do

- Keep the entitlement record current from Google Play Billing purchase state, including renewals, cancellations, expiry and refunds, on the server side, for example from Play's real-time developer notifications.
- Write the record only from the server. The app keeps read-only access to its own record and no write access.
- Leave the per-call check unchanged: the grading function reads the record on every call.
- Retire the auto-grant, and decide what happens to the Premium it already granted.

## Open questions

- Which Play subscription products grant the entitlement.
- Where a User who is not Premium can buy Premium, and how buying it updates the record the app follows.

## Related

- [../voice-answering.md](../voice-answering.md): the built feature, its backend and the entitlement check.
- [../firestore-schema.md](../firestore-schema.md): the entitlement record.
