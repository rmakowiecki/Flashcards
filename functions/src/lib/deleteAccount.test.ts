// Runs against the Firestore and Auth emulators `npm test` starts (see package.json): deletion is a
// recursive delete, a query and Auth calls, which mocks would only restate. Interrupted and raced runs
// are simulated by wrapping the Auth instance the handler is given, so the real handler runs every
// step. Every test uses fresh uids, so one test's documents never reach another's assertions.
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { after, before, describe, it } from "node:test";
import * as admin from "firebase-admin";
import { Auth } from "firebase-admin/auth";
import { callableRequestSignedInAt, nowEpochSeconds } from "./callableRequestFixtures";
import { handleDeleteAccountCall } from "./deleteAccount";
import { handleSubmitStudySessionCall, validateSubmitStudySessionRequest } from "./submitStudySession";

const TEST_PROJECT_ID = "flashcards-functions-test";
const BUG_REPORTS_COLLECTION = "bugReports";
const FIELD_UID = "uid";
const SIGNED_IN_SECONDS_AGO = 60;

// One document in every place Firestore holds data for a User, relative to `users/{uid}`.
const USER_DOCUMENT_PATHS = [
  "",
  "/progress/summary",
  "/progress/user-stats",
  "/progress/details/subcategories/sub-1",
  "/sessions/session-1",
  "/favorites/state",
  "/recents/state",
  "/entitlement/premium",
  "/privateCards/sub-1/flashcards/card-1",
  "/curationRequests/card-1",
];

before(() => {
  admin.initializeApp({ projectId: TEST_PROJECT_ID });
});

after(async () => {
  await Promise.all(admin.apps.map((app) => app?.delete()));
});

async function createActiveUser(): Promise<string> {
  return (await admin.auth().createUser({ uid: randomUUID() })).uid;
}

function signedInRequest(uid: string) {
  return callableRequestSignedInAt(uid, nowEpochSeconds() - SIGNED_IN_SECONDS_AGO, null);
}

/** `users/{uid}` followed by `path`, one of [USER_DOCUMENT_PATHS] or a path of the same form. */
function userDocRef(uid: string, path: string): FirebaseFirestore.DocumentReference {
  return admin.firestore().doc(`users/${uid}${path}`);
}

async function seedBugReport(uid: string): Promise<void> {
  await admin.firestore().collection(BUG_REPORTS_COLLECTION).add({ [FIELD_UID]: uid, description: "The study session froze after the third card." });
}

async function seedAccountData(uid: string): Promise<void> {
  await Promise.all(USER_DOCUMENT_PATHS.map((path) => userDocRef(uid, path).set({ seeded: true })));
  await seedBugReport(uid);
  await seedBugReport(uid);
}

async function existingUserDocumentPaths(uid: string): Promise<string[]> {
  const snapshots = await Promise.all(USER_DOCUMENT_PATHS.map((path) => userDocRef(uid, path).get()));
  return USER_DOCUMENT_PATHS.filter((_, index) => snapshots[index].exists);
}

async function bugReportCount(uid: string): Promise<number> {
  const snapshot = await admin.firestore().collection(BUG_REPORTS_COLLECTION).where(FIELD_UID, "==", uid).count().get();
  return snapshot.data().count;
}

async function authUserExists(uid: string): Promise<boolean> {
  return admin
    .auth()
    .getUser(uid)
    .then(
      () => true,
      () => false,
    );
}

async function assertNothingLeftFor(uid: string): Promise<void> {
  assert.deepEqual(await existingUserDocumentPaths(uid), []);
  assert.deepEqual(await userDocRef(uid, "").listCollections(), []);
  assert.equal(await bugReportCount(uid), 0);
  assert.equal(await authUserExists(uid), false);
}

async function assertAccountIntact(uid: string): Promise<void> {
  assert.deepEqual(await existingUserDocumentPaths(uid), USER_DOCUMENT_PATHS);
  assert.equal(await bugReportCount(uid), 2);
  assert.equal(await authUserExists(uid), true);
}

/** The emulator-bound Auth instance with `revokeRefreshTokens` replaced, to interrupt or race the step order. */
function authWithRevokeRefreshTokens(revokeRefreshTokens: (uid: string) => Promise<void>): Auth {
  return new Proxy(admin.auth(), {
    get(target, property) {
      if (property === "revokeRefreshTokens") return revokeRefreshTokens;
      const value = Reflect.get(target, property);
      return typeof value === "function" ? value.bind(target) : value;
    },
  });
}

describe("handleDeleteAccountCall", () => {
  it("deletes every document and Bug Report of the caller and their Auth user, leaving other users untouched", async () => {
    const uid = await createActiveUser();
    const otherUid = await createActiveUser();
    await seedAccountData(uid);
    await seedAccountData(otherUid);

    await handleDeleteAccountCall(admin.auth(), admin.firestore(), signedInRequest(uid));

    await assertNothingLeftFor(uid);
    await assertAccountIntact(otherUid);
  });

  it("completes on retry after a run that failed before revoking tokens", async () => {
    const uid = await createActiveUser();
    await seedAccountData(uid);
    const request = signedInRequest(uid);
    const failingAuth = authWithRevokeRefreshTokens(() => Promise.reject(new Error("Auth unavailable")));

    await assert.rejects(handleDeleteAccountCall(failingAuth, admin.firestore(), request), { code: "internal" });
    assert.equal(await authUserExists(uid), true);

    await handleDeleteAccountCall(admin.auth(), admin.firestore(), request);

    await assertNothingLeftFor(uid);
  });

  it("sweeps documents and Bug Reports written after the main delete but before revocation", async () => {
    const uid = await createActiveUser();
    await seedAccountData(uid);
    const racingAuth = authWithRevokeRefreshTokens(async (revokedUid) => {
      await userDocRef(revokedUid, "/sessions/raced-session").set({ seeded: true });
      await seedBugReport(revokedUid);
      await admin.auth().revokeRefreshTokens(revokedUid);
    });

    await handleDeleteAccountCall(racingAuth, admin.firestore(), signedInRequest(uid));

    await assertNothingLeftFor(uid);
  });

  it("rejects a caller whose tokens were revoked and deletes nothing", async () => {
    const uid = await createActiveUser();
    await seedAccountData(uid);
    const request = signedInRequest(uid);
    await admin.auth().revokeRefreshTokens(uid);

    await assert.rejects(handleDeleteAccountCall(admin.auth(), admin.firestore(), request), { code: "unauthenticated" });

    await assertAccountIntact(uid);
  });

  it("rejects a second call after the account was deleted", async () => {
    const uid = await createActiveUser();
    const request = signedInRequest(uid);
    await handleDeleteAccountCall(admin.auth(), admin.firestore(), request);

    await assert.rejects(handleDeleteAccountCall(admin.auth(), admin.firestore(), request), { code: "unauthenticated" });
  });

  it("makes a later study submission with the deleted caller's token write nothing", async () => {
    const uid = await createActiveUser();
    const request = signedInRequest(uid);
    // A request that passes validation, so only the revoked-token guard can be what rejects it.
    const sessionData = {
      ownerUid: uid,
      sessionId: randomUUID(),
      studyMode: "Fast",
      startedAtEpochMillis: Date.now(),
      durationSeconds: 60,
      abandoned: false,
      categoryId: "cat-1",
      categoryName: "Category One",
      subcategoryIds: ["sub-1"],
      subcategoryNames: ["Subcategory One"],
      sourceType: "SingleSubcategory",
      readAloud: false,
      cardResults: [{ cardId: "card-1", subcategoryId: "sub-1", state: "Seen" }],
      studyDateUtcOffsetMinutes: 0,
      dailyGoalMinutes: 20,
    };
    validateSubmitStudySessionRequest(sessionData);
    await handleDeleteAccountCall(admin.auth(), admin.firestore(), request);

    await assert.rejects(handleSubmitStudySessionCall(admin.auth(), admin.firestore(), { ...request, data: sessionData }), { code: "unauthenticated" });

    assert.deepEqual(await userDocRef(uid, "").listCollections(), []);
  });
});
