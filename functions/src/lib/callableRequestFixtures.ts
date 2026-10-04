// Test-only fixtures for callables' lib-level handlers. Builds the request `onCall` would hand a
// handler after verifying an ID token, so tests can drive the revoked-token guard against the Auth
// emulator without minting real tokens.
import { DecodedIdToken } from "firebase-admin/auth";
import { CallableRequest } from "firebase-functions/v2/https";

export function nowEpochSeconds(): number {
  return Math.floor(Date.now() / 1000);
}

/** A callable request from `uid`, whose ID token records a sign-in at `authTimeEpochSeconds`. */
export function callableRequestSignedInAt<T>(uid: string, authTimeEpochSeconds: number, data: T): Pick<CallableRequest<T>, "auth" | "data"> {
  const token = { uid, sub: uid, auth_time: authTimeEpochSeconds } as DecodedIdToken;
  return { auth: { uid, token, rawToken: "" }, data };
}
