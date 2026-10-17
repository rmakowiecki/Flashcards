// Runs against the Auth and Firestore emulators `npm test` starts (see package.json): the guard reads a
// real Auth user record's provider data and a real entitlement record, which a mock would only restate.
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { after, before, describe, it } from "node:test";
import * as admin from "firebase-admin";
import { grantPremiumToNewUser, isPremiumUser, requirePremiumCaller } from "./entitlement";
import { callableRequestSignedInAt, nowEpochSeconds } from "./callableRequestFixtures";

const TEST_PROJECT_ID = "flashcards-functions-test";

before(() => {
  admin.initializeApp({ projectId: TEST_PROJECT_ID });
});

after(async () => {
  await Promise.all(admin.apps.map((app) => app?.delete()));
});

function entitlementRecord(uid: string) {
  return admin.firestore().doc(`users/${uid}/entitlement/premium`);
}

function requestFrom(uid: string) {
  return callableRequestSignedInAt(uid, nowEpochSeconds(), undefined);
}

/** An Auth user with no provider data, the shape a Guest's anonymous account has. */
async function createGuest(): Promise<string> {
  return (await admin.auth().createUser({ uid: randomUUID() })).uid;
}

/** An Auth user signed in with Google, as a direct sign-up or a linked Guest leaves it. */
async function createGoogleUser(): Promise<string> {
  const uid = randomUUID();
  const result = await admin.auth().importUsers([{ uid, providerData: [{ uid: `google-${uid}`, providerId: "google.com" }] }]);
  assert.equal(result.failureCount, 0);
  return uid;
}

describe("grantPremiumToNewUser", () => {
  it("grants Premium to a user with no record", async () => {
    const uid = await createGoogleUser();

    await grantPremiumToNewUser(admin.firestore(), uid);

    const record = await entitlementRecord(uid).get();
    assert.equal(record.get("isPremium"), true);
    assert.equal(record.get("source"), "auto-grant");
  });

  it("never overwrites an existing record, so a revocation stands", async () => {
    const uid = await createGoogleUser();
    await entitlementRecord(uid).set({ isPremium: false, source: "admin" });

    await grantPremiumToNewUser(admin.firestore(), uid);

    const record = await entitlementRecord(uid).get();
    assert.equal(record.get("isPremium"), false);
    assert.equal(record.get("source"), "admin");
  });

  it("is safe to run twice", async () => {
    const uid = await createGoogleUser();

    await grantPremiumToNewUser(admin.firestore(), uid);
    await grantPremiumToNewUser(admin.firestore(), uid);

    assert.equal(await isPremiumUser(admin.firestore(), uid), true);
  });
});

describe("requirePremiumCaller", () => {
  it("returns the uid of a signed-in user whose record grants Premium", async () => {
    const uid = await createGoogleUser();
    await grantPremiumToNewUser(admin.firestore(), uid);

    assert.equal(await requirePremiumCaller(admin.auth(), admin.firestore(), requestFrom(uid)), uid);
  });

  it("refuses a Guest even when its record grants Premium", async () => {
    const uid = await createGuest();
    await grantPremiumToNewUser(admin.firestore(), uid);

    await assert.rejects(requirePremiumCaller(admin.auth(), admin.firestore(), requestFrom(uid)), { code: "permission-denied" });
  });

  it("accepts a Guest once it has linked Google, keeping the grant it got as a Guest", async () => {
    const uid = await createGuest();
    await grantPremiumToNewUser(admin.firestore(), uid);
    await admin.auth().updateUser(uid, { providerToLink: { uid: `google-${uid}`, providerId: "google.com" } });

    assert.equal(await requirePremiumCaller(admin.auth(), admin.firestore(), requestFrom(uid)), uid);
  });

  it("refuses a signed-in user with no record", async () => {
    const uid = await createGoogleUser();

    await assert.rejects(requirePremiumCaller(admin.auth(), admin.firestore(), requestFrom(uid)), { code: "permission-denied" });
  });

  it("refuses a signed-in user whose Premium was revoked", async () => {
    const uid = await createGoogleUser();
    await entitlementRecord(uid).set({ isPremium: false, source: "admin" });

    await assert.rejects(requirePremiumCaller(admin.auth(), admin.firestore(), requestFrom(uid)), { code: "permission-denied" });
  });

  it("rejects a request with no auth context", async () => {
    await assert.rejects(requirePremiumCaller(admin.auth(), admin.firestore(), {}), { code: "unauthenticated" });
  });
});
