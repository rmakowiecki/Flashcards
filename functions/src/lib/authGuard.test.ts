// Runs against the Auth emulator `npm test` starts alongside Firestore (see package.json): the guard's
// whole job is reading a real user record's revocation state, which a mock would only restate.
// Requests come from callableRequestFixtures, the decoded-token shape `onCall` hands the wrapper, so
// no token minting or Functions emulator is needed.
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { after, before, describe, it } from "node:test";
import * as admin from "firebase-admin";
import { requireActiveSession } from "./authGuard";
import { callableRequestSignedInAt, nowEpochSeconds } from "./callableRequestFixtures";

const TEST_PROJECT_ID = "flashcards-functions-test";
const SECONDS_BEFORE_REVOCATION = 60;

before(() => {
  admin.initializeApp({ projectId: TEST_PROJECT_ID });
});

after(async () => {
  await Promise.all(admin.apps.map((app) => app?.delete()));
});

function requestSignedInAt(uid: string, authTimeEpochSeconds: number) {
  return callableRequestSignedInAt(uid, authTimeEpochSeconds, undefined);
}

async function createUser(): Promise<string> {
  const user = await admin.auth().createUser({ uid: randomUUID() });
  return user.uid;
}

describe("requireActiveSession", () => {
  it("returns the uid of an existing user whose tokens were never revoked", async () => {
    const uid = await createUser();

    assert.equal(await requireActiveSession(admin.auth(), requestSignedInAt(uid, nowEpochSeconds())), uid);
  });

  it("rejects a request with no auth context", async () => {
    await assert.rejects(requireActiveSession(admin.auth(), {}), { code: "unauthenticated" });
  });

  it("rejects a token signed in before the user's tokens were revoked", async () => {
    const uid = await createUser();
    const signedInAt = nowEpochSeconds() - SECONDS_BEFORE_REVOCATION;
    await admin.auth().revokeRefreshTokens(uid);

    await assert.rejects(requireActiveSession(admin.auth(), requestSignedInAt(uid, signedInAt)), { code: "unauthenticated" });
  });

  it("accepts a token signed in after the user's tokens were revoked", async () => {
    const uid = await createUser();
    await admin.auth().revokeRefreshTokens(uid);
    const signedInAfterRevocation = nowEpochSeconds() + 1;

    assert.equal(await requireActiveSession(admin.auth(), requestSignedInAt(uid, signedInAfterRevocation)), uid);
  });

  it("rejects a token whose user was deleted", async () => {
    const uid = await createUser();
    const signedInAt = nowEpochSeconds();
    await admin.auth().deleteUser(uid);

    await assert.rejects(requireActiveSession(admin.auth(), requestSignedInAt(uid, signedInAt)), { code: "unauthenticated" });
  });

  it("rejects a token whose user was disabled", async () => {
    const uid = await createUser();
    await admin.auth().updateUser(uid, { disabled: true });

    await assert.rejects(requireActiveSession(admin.auth(), requestSignedInAt(uid, nowEpochSeconds())), { code: "unauthenticated" });
  });
});
