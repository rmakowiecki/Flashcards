import { Auth } from "firebase-admin/auth";
import { FieldValue, Firestore } from "firebase-admin/firestore";
import { CallableRequest, HttpsError } from "firebase-functions/v2/https";
import { requireActiveUser } from "./authGuard";

const USERS_COLLECTION = "users";
const ENTITLEMENT_COLLECTION = "entitlement";
const ENTITLEMENT_DOCUMENT = "premium";
const FIELD_IS_PREMIUM = "isPremium";
const FIELD_SOURCE = "source";
const FIELD_GRANTED_AT = "grantedAt";
const SOURCE_AUTO_GRANT = "auto-grant";
const ALREADY_EXISTS_CODE = 6;
const USER_NOT_FOUND_CODE = "auth/user-not-found";

function entitlementDocument(firestore: Firestore, uid: string) {
  return firestore.collection(USERS_COLLECTION).doc(uid).collection(ENTITLEMENT_COLLECTION).doc(ENTITLEMENT_DOCUMENT);
}

/**
 * Server-side Premium source of truth: the User's entitlement record, written only by the Admin SDK
 * (this module's auto-grant, or an admin editing it in the console). The app reads the same record to
 * decide what to offer, but a client-side "is premium" flag is never trusted (ADR-0024, ADR-0070).
 */
export async function isPremiumUser(firestore: Firestore, uid: string): Promise<boolean> {
  const snapshot = await entitlementDocument(firestore, uid).get();
  return snapshot.exists && snapshot.get(FIELD_IS_PREMIUM) === true;
}

/**
 * Rejects a caller who may not use a Premium feature, and returns the caller's uid otherwise. **Every
 * paid callable must call this before its first paid call.**
 *
 * A Guest is refused whatever its record says: the auto-grant gives every new Auth user Premium, a
 * Guest included, so the grant survives the Guest linking Google or GitHub, which creates no new Auth
 * user (ADR-0071). The provider is read from the Auth user record, not the ID token, so a token minted
 * before the Guest linked cannot misreport it. A Guest's user record has no provider data. A caller
 * whose session is no longer active is refused as `unauthenticated` (`requireActiveUser`); the Guest
 * and Premium refusals are `permission-denied`.
 */
export async function requirePremiumCaller(auth: Auth, firestore: Firestore, request: Pick<CallableRequest<unknown>, "auth">): Promise<string> {
  const user = await requireActiveUser(auth, request);
  if (user.providerData.length === 0) {
    throw new HttpsError("permission-denied", "A Guest cannot use Premium features");
  }
  if (!(await isPremiumUser(firestore, user.uid))) {
    throw new HttpsError("permission-denied", "No active premium entitlement");
  }
  return user.uid;
}

/** Whether `uid` still has an Auth user. */
async function authUserExists(auth: Auth, uid: string): Promise<boolean> {
  return auth.getUser(uid).then(
    () => true,
    (error: unknown) => {
      if ((error as { code?: string }).code === USER_NOT_FOUND_CODE) return false;
      throw error;
    },
  );
}

/**
 * The temporary auto-grant (ADR-0071): gives a newly created Auth user Premium. Creates the record only
 * when none exists, so a record an admin already wrote is never overwritten. Safe to run twice.
 *
 * Skips a user whose Auth user is already gone: the trigger runs in the background and is retried on
 * failure, so it can run after Account Deletion, which must leave nothing under `users/{uid}`.
 */
export async function grantPremiumToNewUser(auth: Auth, firestore: Firestore, uid: string): Promise<void> {
  if (!(await authUserExists(auth, uid))) return;
  try {
    await entitlementDocument(firestore, uid).create({
      [FIELD_IS_PREMIUM]: true,
      [FIELD_SOURCE]: SOURCE_AUTO_GRANT,
      [FIELD_GRANTED_AT]: FieldValue.serverTimestamp(),
    });
  } catch (error) {
    if ((error as { code?: number }).code === ALREADY_EXISTS_CODE) return;
    throw error;
  }
}
