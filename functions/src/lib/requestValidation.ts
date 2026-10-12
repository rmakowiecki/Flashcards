import { HttpsError } from "firebase-functions/v2/https";

/**
 * Field checks shared by the callables' request validators. Each returns the value narrowed to its
 * type, or throws `invalid-argument` naming the field, so a validator reads as one line per field.
 */

export function fail(message: string): never {
  throw new HttpsError("invalid-argument", message);
}

export function requireNonEmptyString(value: unknown, field: string): string {
  if (typeof value !== "string" || value.length === 0) fail(`${field} must be a non-empty string`);
  return value;
}

export function requireStringOfLength(value: unknown, field: string, minLength: number, maxLength: number): string {
  if (typeof value !== "string" || value.length < minLength || value.length > maxLength) {
    fail(`${field} must be a string of ${minLength}-${maxLength} characters`);
  }
  return value;
}

export function requireStringArray(value: unknown, field: string): string[] {
  if (!Array.isArray(value) || value.length === 0 || !value.every((element) => typeof element === "string" && element.length > 0)) {
    fail(`${field} must be a non-empty array of non-empty strings`);
  }
  return value as string[];
}

export function requireFiniteNumber(value: unknown, field: string, minimum = 0): number {
  if (typeof value !== "number" || !Number.isFinite(value) || value < minimum) {
    fail(`${field} must be a number >= ${minimum}`);
  }
  return value;
}

export function requireFiniteNumberInRange(value: unknown, field: string, minimum: number, maximum: number): number {
  if (typeof value !== "number" || !Number.isFinite(value) || value < minimum || value > maximum) {
    fail(`${field} must be a number between ${minimum} and ${maximum}`);
  }
  return value;
}

export function requireIntegerAtLeast(value: unknown, field: string, minimum: number): number {
  if (typeof value !== "number" || !Number.isInteger(value) || value < minimum) fail(`${field} must be an integer of at least ${minimum}`);
  return value;
}

export function requireBoolean(value: unknown, field: string): boolean {
  if (typeof value !== "boolean") fail(`${field} must be a boolean`);
  return value;
}
