import { Auth } from "firebase-admin/auth";
import { Firestore } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { CallableRequest, HttpsError } from "firebase-functions/v2/https";
import { requireActiveSession } from "./authGuard";

/**
 * Account Deletion: erases a User and everything written for them from Firebase (see ADR-0059).
 *
 * Kept out of `index.ts` so the step order, the retry after a failed run and the sweep of a raced
 * write are tested directly against the emulators, without an `onCall` round trip.
 */

const USERS_COLLECTION = "users";
const BUG_REPORTS_COLLECTION = "bugReports";
const FIELD_UID = "uid";

const USER_NOT_FOUND_CODE = "auth/user-not-found";

// A batched write's operation limit. Bug Reports are capped per day, so one page is the normal case.
const BUG_REPORT_DELETE_PAGE_SIZE = 500;

/** Deletes every Bug Report filed by `uid`. They are top-level, so a recursive delete of `users/{uid}` misses them. */
async function deleteBugReports(db: Firestore, uid: string): Promise<void> {
  const query = db.collection(BUG_REPORTS_COLLECTION).where(FIELD_UID, "==", uid).limit(BUG_REPORT_DELETE_PAGE_SIZE);
  for (;;) {
    const page = await query.get();
    if (page.empty) return;
    const batch = db.batch();
    page.docs.forEach((reportDoc) => batch.delete(reportDoc.ref));
    await batch.commit();
  }
}

/** Deletes `users/{uid}` with every subcollection under it, whether or not the parent document exists. */
async function deleteUsersDocument(db: Firestore, uid: string): Promise<void> {
  await db.recursiveDelete(db.collection(USERS_COLLECTION).doc(uid));
}

/** Deletes the Auth user; one that is already gone, from an earlier run, counts as deleted. */
async function deleteAuthUser(auth: Auth, uid: string): Promise<void> {
  await auth.deleteUser(uid).catch((error: unknown) => {
    if ((error as { code?: string }).code !== USER_NOT_FOUND_CODE) throw error;
  });
}

/** Runs one deletion step; any failure is logged with the uid and the step, then ends the call as `internal`. */
async function runStep(uid: string, step: string, action: () => Promise<void>): Promise<void> {
  try {
    await action();
  } catch (error) {
    logger.error("deleteAccount step failed", { uid, step, error });
    throw new HttpsError("internal", "Account deletion failed");
  }
}

/**
 * Deletes everything held for `uid`, then the Auth user. Every step is idempotent, so a failed run
 * can be retried.
 *
 * Refresh tokens are revoked only after the main deletes. A failure before that leaves the caller's
 * token valid, so a retry still passes the revoked-token guard. Revoking makes the guard reject any
 * later submission; the second sweep then removes what a submission that passed the guard earlier
 * wrote meanwhile. The Auth user goes last, so until then a failed run leaves an account the User can
 * sign back into and delete again.
 */
export async function deleteAccount(auth: Auth, db: Firestore, uid: string): Promise<void> {
  await runStep(uid, "deleteBugReports", () => deleteBugReports(db, uid));
  await runStep(uid, "deleteUsersDocument", () => deleteUsersDocument(db, uid));
  await runStep(uid, "revokeRefreshTokens", () => auth.revokeRefreshTokens(uid));
  await runStep(uid, "sweepBugReports", () => deleteBugReports(db, uid));
  await runStep(uid, "sweepUsersDocument", () => deleteUsersDocument(db, uid));
  await runStep(uid, "deleteAuthUser", () => deleteAuthUser(auth, uid));
}

/**
 * The whole `deleteAccount` callable, minus the `onCall` wrapper: rejects an inactive caller
 * ([requireActiveSession]) before deleting anything, then deletes the caller's account. The request
 * carries no payload; any data sent is ignored.
 */
export async function handleDeleteAccountCall(auth: Auth, db: Firestore, request: Pick<CallableRequest<unknown>, "auth">): Promise<void> {
  const uid = await requireActiveSession(auth, request);
  await deleteAccount(auth, db, uid);
}
