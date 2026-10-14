import * as admin from "firebase-admin";

const USERS_COLLECTION = "users";
const ENTITLEMENT_COLLECTION = "entitlement";
const ENTITLEMENT_DOCUMENT = "premium";
const FIELD_IS_PREMIUM = "isPremium";

/**
 * Server-side entitlement source of truth. Play Billing → Firestore sync (RTDN) is not
 * built yet (docs/design/planned/voice-answering.md); until it exists, this doc is
 * populated manually for test accounts. A client-side "is premium" flag is never trusted
 * (ADR-0024).
 */
export async function isPremiumUser(uid: string): Promise<boolean> {
  const snapshot = await admin
    .firestore()
    .collection(USERS_COLLECTION)
    .doc(uid)
    .collection(ENTITLEMENT_COLLECTION)
    .doc(ENTITLEMENT_DOCUMENT)
    .get();
  return snapshot.exists && snapshot.get(FIELD_IS_PREMIUM) === true;
}
