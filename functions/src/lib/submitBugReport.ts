import { Auth } from "firebase-admin/auth";
import { FieldValue, Firestore, Timestamp } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { CallableRequest, HttpsError } from "firebase-functions/v2/https";
import { requireActiveSession } from "./authGuard";
import { fail, requireIntegerAtLeast, requireStringOfLength } from "./requestValidation";

/**
 * The sole writer of Bug Reports. A client never touches `bugReports` directly: this function
 * validates the report, rate-limits its author and adds the server-set fields (see ADR-0058).
 *
 * Kept out of `index.ts` so the validation, the rate limit and the guard's place in front of the
 * write are tested directly against the emulators, without an `onCall` round trip.
 */

const BUG_REPORTS_COLLECTION = "bugReports";

// `bugReports/{reportId}` document fields. The request keys are the client-sent subset of these.
const FIELD_UID = "uid";
const FIELD_DESCRIPTION = "description";
const FIELD_SEVERITY = "severity";
const FIELD_APP_VERSION_NAME = "appVersionName";
const FIELD_APP_VERSION_CODE = "appVersionCode";
const FIELD_DEVICE_MODEL = "deviceModel";
const FIELD_ANDROID_VERSION = "androidVersion";
const FIELD_CREATED_AT = "createdAt";
const FIELD_STATUS = "status";

// Only the server writes `new`; the developer, or an investigation agent, moves a report on with the Admin SDK.
type BugReportStatus = "new" | "triaged" | "fixed";
const NEW_REPORT_STATUS: BugReportStatus = "new";

const REQUEST_KEYS: readonly string[] = [
  FIELD_DESCRIPTION,
  FIELD_SEVERITY,
  FIELD_APP_VERSION_NAME,
  FIELD_APP_VERSION_CODE,
  FIELD_DEVICE_MODEL,
  FIELD_ANDROID_VERSION,
];

const VALID_SEVERITIES = ["blocker", "minor", "cosmetic"] as const;

type BugReportSeverity = (typeof VALID_SEVERITIES)[number];

// Mirror the description limits on the client's `BugReport` domain model; change both together.
export const MIN_DESCRIPTION_LENGTH = 20;
export const MAX_DESCRIPTION_LENGTH = 1000;

export const MAX_APP_VERSION_NAME_LENGTH = 50;
export const MAX_DEVICE_MODEL_LENGTH = 100;
// The app's minSdk: no device below it can run the app that sends the report.
export const MIN_ANDROID_VERSION = 26;

export const MAX_REPORTS_PER_WINDOW = 10;
export const RATE_LIMIT_WINDOW_MILLIS = 24 * 60 * 60 * 1000;

export interface ValidatedBugReportRequest {
  description: string;
  severity: BugReportSeverity;
  appVersionName: string;
  appVersionCode: number;
  deviceModel: string;
  androidVersion: number;
}

/**
 * Checks the raw request against the Bug Report contract and returns it with `description`
 * trimmed. Any missing or extra key, wrong type or out-of-range value throws `invalid-argument`.
 */
export function validateSubmitBugReportRequest(data: unknown): ValidatedBugReportRequest {
  if (typeof data !== "object" || data === null || Array.isArray(data)) fail("request must be an object");
  const request = data as Record<string, unknown>;

  const missingKeys = REQUEST_KEYS.filter((key) => !(key in request));
  if (missingKeys.length > 0) fail(`missing keys: ${missingKeys.join(", ")}`);
  const extraKeys = Object.keys(request).filter((key) => !REQUEST_KEYS.includes(key));
  if (extraKeys.length > 0) fail(`unexpected keys: ${extraKeys.join(", ")}`);

  const rawDescription = request[FIELD_DESCRIPTION];
  if (typeof rawDescription !== "string") fail(`${FIELD_DESCRIPTION} must be a string`);
  const description = requireStringOfLength(rawDescription.trim(), FIELD_DESCRIPTION, MIN_DESCRIPTION_LENGTH, MAX_DESCRIPTION_LENGTH);

  const severity = request[FIELD_SEVERITY];
  if (!VALID_SEVERITIES.includes(severity as BugReportSeverity)) fail(`${FIELD_SEVERITY} must be one of ${VALID_SEVERITIES.join(", ")}`);

  return {
    description,
    severity: severity as BugReportSeverity,
    appVersionName: requireStringOfLength(request[FIELD_APP_VERSION_NAME], FIELD_APP_VERSION_NAME, 1, MAX_APP_VERSION_NAME_LENGTH),
    appVersionCode: requireIntegerAtLeast(request[FIELD_APP_VERSION_CODE], FIELD_APP_VERSION_CODE, 1),
    deviceModel: requireStringOfLength(request[FIELD_DEVICE_MODEL], FIELD_DEVICE_MODEL, 1, MAX_DEVICE_MODEL_LENGTH),
    androidVersion: requireIntegerAtLeast(request[FIELD_ANDROID_VERSION], FIELD_ANDROID_VERSION, MIN_ANDROID_VERSION),
  };
}

/**
 * Throws `resource-exhausted` when `uid` already has [MAX_REPORTS_PER_WINDOW] reports created in
 * the last rolling [RATE_LIMIT_WINDOW_MILLIS]. Counted with an aggregate query, not a transaction,
 * so two concurrent submissions at the limit may both pass; that slack is accepted.
 */
async function requireUnderRateLimit(db: Firestore, uid: string): Promise<void> {
  const windowStart = Timestamp.fromMillis(Date.now() - RATE_LIMIT_WINDOW_MILLIS);
  const recentReports = await db
    .collection(BUG_REPORTS_COLLECTION)
    .where(FIELD_UID, "==", uid)
    .where(FIELD_CREATED_AT, ">=", windowStart)
    .count()
    .get();
  if (recentReports.data().count >= MAX_REPORTS_PER_WINDOW) {
    logger.warn("submitBugReport rate limit reached", { uid });
    throw new HttpsError("resource-exhausted", "Too many bug reports, try again later");
  }
}

/**
 * Enforces the rate limit for `uid`, then adds one `bugReports` document with the server-set `uid`,
 * `createdAt` and `status`. Writes nothing when the caller is over the limit.
 */
export async function submitBugReport(db: Firestore, uid: string, report: ValidatedBugReportRequest): Promise<void> {
  await requireUnderRateLimit(db, uid);
  await db.collection(BUG_REPORTS_COLLECTION).add({
    [FIELD_UID]: uid,
    [FIELD_DESCRIPTION]: report.description,
    [FIELD_SEVERITY]: report.severity,
    [FIELD_APP_VERSION_NAME]: report.appVersionName,
    [FIELD_APP_VERSION_CODE]: report.appVersionCode,
    [FIELD_DEVICE_MODEL]: report.deviceModel,
    [FIELD_ANDROID_VERSION]: report.androidVersion,
    [FIELD_CREATED_AT]: FieldValue.serverTimestamp(),
    [FIELD_STATUS]: NEW_REPORT_STATUS,
  });
}

/**
 * The whole `submitBugReport` callable, minus the `onCall` wrapper: rejects an inactive caller
 * ([requireActiveSession]) before anything else, then validates the payload and submits it as that
 * caller. Nothing is written when any step fails.
 */
export async function handleSubmitBugReportCall(auth: Auth, db: Firestore, request: Pick<CallableRequest<unknown>, "auth" | "data">): Promise<void> {
  const uid = await requireActiveSession(auth, request);
  const report = validateSubmitBugReportRequest(request.data);
  await submitBugReport(db, uid, report);
}
