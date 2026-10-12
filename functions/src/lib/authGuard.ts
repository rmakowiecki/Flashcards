import { Auth } from "firebase-admin/auth";
import { CallableRequest, HttpsError } from "firebase-functions/v2/https";

const USER_NOT_FOUND_CODE = "auth/user-not-found";

/**
 * Rejects a caller whose session is no longer active, and returns the caller's uid otherwise.
 * **Every callable that writes user data must call this before its first write.**
 *
 * `onCall` verifies an ID token's signature and expiry but not revocation, so a token stays usable
 * for up to an hour after its User signs out everywhere or is deleted, and the Admin SDK bypasses
 * security rules. This is the check `verifyIdToken(token, true)` performs, done on the token
 * `onCall` already decoded: the User must still exist and not be disabled, and the token's
 * `auth_time` must not predate the User's `tokensValidAfterTime`. Every rejection is
 * `unauthenticated`, so a client that queues writes keeps them queued rather than dropping them.
 * See ADR-0057.
 *
 * Takes the Auth instance as a parameter so tests can pass one bound to the Auth emulator.
 */
export async function requireActiveSession(auth: Auth, request: Pick<CallableRequest<unknown>, "auth">): Promise<string> {
  const callerAuth = request.auth;
  if (!callerAuth?.uid) throw new HttpsError("unauthenticated", "Missing Firebase ID token");

  const user = await auth.getUser(callerAuth.uid).catch((error: unknown) => {
    if ((error as { code?: string }).code === USER_NOT_FOUND_CODE) {
      throw new HttpsError("unauthenticated", "The signed-in user no longer exists");
    }
    throw error;
  });
  if (user.disabled) throw new HttpsError("unauthenticated", "The signed-in user is disabled");

  if (user.tokensValidAfterTime) {
    const authTimeMillis = callerAuth.token.auth_time * 1000;
    const validSinceMillis = new Date(user.tokensValidAfterTime).getTime();
    if (authTimeMillis < validSinceMillis) {
      throw new HttpsError("unauthenticated", "The ID token has been revoked");
    }
  }
  return callerAuth.uid;
}
