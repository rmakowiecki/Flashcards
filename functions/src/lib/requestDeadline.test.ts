import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { HttpError } from "./httpError";
import { RequestDeadline } from "./requestDeadline";

describe("RequestDeadline", () => {
  it("gives a step its own limit while the budget has more left", () => {
    let nowMs = 0;
    const deadline = new RequestDeadline(19_000, () => nowMs);

    nowMs = 1_000;

    assert.equal(deadline.stepTimeoutMs(15_000), 15_000);
  });

  it("cuts a step short to what is left of the budget", () => {
    let nowMs = 0;
    const deadline = new RequestDeadline(19_000, () => nowMs);

    nowMs = 12_000;

    assert.equal(deadline.stepTimeoutMs(15_000), 7_000);
  });

  it("refuses to start a step once the budget is spent", () => {
    let nowMs = 0;
    const deadline = new RequestDeadline(19_000, () => nowMs);

    nowMs = 19_000;

    assert.throws(
      () => deadline.stepTimeoutMs(15_000),
      (error: unknown) => error instanceof HttpError && error.statusCode === 504,
    );
  });
});
