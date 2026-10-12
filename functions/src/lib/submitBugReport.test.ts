// Runs against the Firestore and Auth emulators `npm test` starts (see package.json): the rate limit
// is an aggregate query and the guard reads a real user record, which mocks would only restate.
// Every test uses a fresh uid, so reports written by one test never count toward another's limit.
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { after, before, describe, it } from "node:test";
import * as admin from "firebase-admin";
import { Timestamp } from "firebase-admin/firestore";
import { callableRequestSignedInAt, nowEpochSeconds } from "./callableRequestFixtures";
import {
  MAX_APP_VERSION_NAME_LENGTH,
  MAX_DESCRIPTION_LENGTH,
  MAX_DEVICE_MODEL_LENGTH,
  MAX_REPORTS_PER_WINDOW,
  MIN_ANDROID_VERSION,
  MIN_DESCRIPTION_LENGTH,
  RATE_LIMIT_WINDOW_MILLIS,
  handleSubmitBugReportCall,
  submitBugReport,
  validateSubmitBugReportRequest,
} from "./submitBugReport";

const TEST_PROJECT_ID = "flashcards-functions-test";
const BUG_REPORTS_COLLECTION = "bugReports";
const VALID_DESCRIPTION = "The study session froze after the third card.";
const SECONDS_BEFORE_REVOCATION = 60;
const MILLIS_PER_HOUR = 60 * 60 * 1000;
// Slack between this process's clock and the emulator's server timestamp.
const CLOCK_SKEW_TOLERANCE_MILLIS = 1000;

before(() => {
  admin.initializeApp({ projectId: TEST_PROJECT_ID });
});

after(async () => {
  await Promise.all(admin.apps.map((app) => app?.delete()));
});

function validRequest(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    description: VALID_DESCRIPTION,
    severity: "minor",
    appVersionName: "1.4.0",
    appVersionCode: 42,
    deviceModel: "Pixel 8",
    androidVersion: 35,
    ...overrides,
  };
}

function withoutKey(key: string): Record<string, unknown> {
  const request = validRequest();
  delete request[key];
  return request;
}

function validReport() {
  return validateSubmitBugReportRequest(validRequest());
}

async function createActiveUser(): Promise<string> {
  return (await admin.auth().createUser({ uid: randomUUID() })).uid;
}

async function reportsOf(uid: string): Promise<admin.firestore.DocumentData[]> {
  const snapshot = await admin.firestore().collection(BUG_REPORTS_COLLECTION).where("uid", "==", uid).get();
  return snapshot.docs.map((reportDoc) => reportDoc.data());
}

async function seedReportCreatedAt(uid: string, createdAtMillis: number): Promise<void> {
  await admin.firestore().collection(BUG_REPORTS_COLLECTION).add({ ...validRequest(), uid, createdAt: Timestamp.fromMillis(createdAtMillis), status: "new" });
}

describe("validateSubmitBugReportRequest", () => {
  it("returns the request with a trimmed description", () => {
    assert.deepEqual(validateSubmitBugReportRequest(validRequest({ description: `  ${VALID_DESCRIPTION}\n` })), validRequest());
  });

  it("accepts descriptions at both length limits", () => {
    const shortest = "a".repeat(MIN_DESCRIPTION_LENGTH);
    const longest = "a".repeat(MAX_DESCRIPTION_LENGTH);

    assert.equal(validateSubmitBugReportRequest(validRequest({ description: shortest })).description, shortest);
    assert.equal(validateSubmitBugReportRequest(validRequest({ description: longest })).description, longest);
  });

  const invalidRequests: [string, unknown][] = [
    ["a description shorter than the minimum", validRequest({ description: "a".repeat(MIN_DESCRIPTION_LENGTH - 1) })],
    ["a description that is too short once trimmed", validRequest({ description: `   ${"a".repeat(MIN_DESCRIPTION_LENGTH - 1)}   ` })],
    ["a description longer than the maximum", validRequest({ description: "a".repeat(MAX_DESCRIPTION_LENGTH + 1) })],
    ["a non-string description", validRequest({ description: 42 })],
    ["an unknown severity", validRequest({ severity: "critical" })],
    ["an empty appVersionName", validRequest({ appVersionName: "" })],
    ["an appVersionName over the maximum", validRequest({ appVersionName: "1".repeat(MAX_APP_VERSION_NAME_LENGTH + 1) })],
    ["a non-string appVersionName", validRequest({ appVersionName: 140 })],
    ["an appVersionCode of 0", validRequest({ appVersionCode: 0 })],
    ["a fractional appVersionCode", validRequest({ appVersionCode: 4.2 })],
    ["a string appVersionCode", validRequest({ appVersionCode: "42" })],
    ["an empty deviceModel", validRequest({ deviceModel: "" })],
    ["a deviceModel over the maximum", validRequest({ deviceModel: "P".repeat(MAX_DEVICE_MODEL_LENGTH + 1) })],
    ["an androidVersion below the minimum", validRequest({ androidVersion: MIN_ANDROID_VERSION - 1 })],
    ["a non-integer androidVersion", validRequest({ androidVersion: "35" })],
    ["a missing key", withoutKey("deviceModel")],
    ["an extra key", validRequest({ uid: "someone-else" })],
    ["a non-object request", "not a report"],
    ["a null request", null],
  ];
  for (const [label, request] of invalidRequests) {
    it(`rejects ${label} with invalid-argument`, () => {
      assert.throws(() => validateSubmitBugReportRequest(request), { code: "invalid-argument" });
    });
  }
});

describe("submitBugReport", () => {
  it("rejects the report past the limit within the window while another user can still submit", async () => {
    const uid = randomUUID();
    for (let reportIndex = 0; reportIndex < MAX_REPORTS_PER_WINDOW; reportIndex++) {
      await submitBugReport(admin.firestore(), uid, validReport());
    }

    await assert.rejects(submitBugReport(admin.firestore(), uid, validReport()), { code: "resource-exhausted" });

    assert.equal((await reportsOf(uid)).length, MAX_REPORTS_PER_WINDOW);
    const otherUid = randomUUID();
    await submitBugReport(admin.firestore(), otherUid, validReport());
    assert.equal((await reportsOf(otherUid)).length, 1);
  });

  it("does not count reports older than the window", async () => {
    const uid = randomUUID();
    const outsideWindowMillis = Date.now() - RATE_LIMIT_WINDOW_MILLIS - MILLIS_PER_HOUR;
    for (let reportIndex = 0; reportIndex < MAX_REPORTS_PER_WINDOW; reportIndex++) {
      await seedReportCreatedAt(uid, outsideWindowMillis);
    }

    await submitBugReport(admin.firestore(), uid, validReport());

    assert.equal((await reportsOf(uid)).length, MAX_REPORTS_PER_WINDOW + 1);
  });
});

// The revoked-token guard itself is covered in authGuard.test.ts; these only pin that the callable
// runs it before writing anything and submits as the verified caller.
describe("handleSubmitBugReportCall", () => {
  it("writes the trimmed report of an active caller with server-set uid, createdAt and status", async () => {
    const uid = await createActiveUser();
    const submittedAfterMillis = Date.now();
    const data = validRequest({ description: `  ${VALID_DESCRIPTION}\n` });

    await handleSubmitBugReportCall(admin.auth(), admin.firestore(), callableRequestSignedInAt(uid, nowEpochSeconds(), data));

    const reports = await reportsOf(uid);
    assert.equal(reports.length, 1);
    const { createdAt, ...storedFields } = reports[0];
    assert.deepEqual(storedFields, { ...validRequest(), uid, status: "new" });
    assert.ok(createdAt instanceof Timestamp);
    assert.ok(createdAt.toMillis() >= submittedAfterMillis - CLOCK_SKEW_TOLERANCE_MILLIS);
  });

  it("rejects an invalid payload from an active caller and writes nothing", async () => {
    const uid = await createActiveUser();

    await assert.rejects(handleSubmitBugReportCall(admin.auth(), admin.firestore(), callableRequestSignedInAt(uid, nowEpochSeconds(), withoutKey("severity"))), { code: "invalid-argument" });

    assert.deepEqual(await reportsOf(uid), []);
  });

  it("rejects a caller whose tokens were revoked and writes nothing", async () => {
    const uid = await createActiveUser();
    const signedInAt = nowEpochSeconds() - SECONDS_BEFORE_REVOCATION;
    await admin.auth().revokeRefreshTokens(uid);

    await assert.rejects(handleSubmitBugReportCall(admin.auth(), admin.firestore(), callableRequestSignedInAt(uid, signedInAt, validRequest())), { code: "unauthenticated" });

    assert.deepEqual(await reportsOf(uid), []);
  });

  it("rejects a caller whose user was deleted and writes nothing", async () => {
    const uid = await createActiveUser();
    await admin.auth().deleteUser(uid);

    await assert.rejects(handleSubmitBugReportCall(admin.auth(), admin.firestore(), callableRequestSignedInAt(uid, nowEpochSeconds(), validRequest())), { code: "unauthenticated" });

    assert.deepEqual(await reportsOf(uid), []);
  });
});
