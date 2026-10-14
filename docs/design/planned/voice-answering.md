# Voice Answering: entitlement sync

The unbuilt remainder of Voice Answering: filling the premium entitlement record from real purchases. The built feature is in [../voice-answering.md](../voice-answering.md).

## Why it is needed

Voice grading costs money on every answer, for transcription and two model calls. The server therefore grades only for a User whose entitlement record says they are premium, and it never trusts a premium flag sent by the app ([ADR-0024](../../adr/0024-voice-grading-cloud-function-proxy.md)). Today that record is set by hand for test accounts, so no real User can use Voice Answering. Shipping the feature needs the record kept in step with the User's subscription.

## What it must do

- Keep the entitlement record current from Google Play Billing purchase state, including renewals, cancellations, expiry and refunds, on the server side, for example from Play's real-time developer notifications.
- Write the record only from the server. The app keeps no write access to it.
- Leave the per-call check unchanged: the grading function reads the record on every call.

## Open questions

- Which Play subscription products grant the entitlement.
- Whether the app should check entitlement before offering Voice Answering on the Preview Study Session Screen, and what it shows a User without it. Today it offers it to everyone, and every answer from a User without entitlement fails.
- How a User who loses entitlement mid-session is treated.

## Related

- [../voice-answering.md](../voice-answering.md): the built feature, its backend and the entitlement check.
- [../firestore-schema.md](../firestore-schema.md): the entitlement record.
