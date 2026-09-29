import { HttpError } from "./httpError";

/**
 * A time budget shared by every step of one request. Each step asks for its own limit, cut short
 * by what is left of the budget, so the steps together never run past it.
 */
export class RequestDeadline {
  private readonly endsAtMs: number;

  constructor(
    budgetMs: number,
    private readonly nowMs: () => number = Date.now,
  ) {
    this.endsAtMs = nowMs() + budgetMs;
  }

  /** The time the next step may take. Throws once the budget is spent, before the step starts. */
  stepTimeoutMs(stepLimitMs: number): number {
    const remainingMs = this.endsAtMs - this.nowMs();
    if (remainingMs <= 0) {
      throw new HttpError(504, "Request deadline exceeded");
    }
    return Math.min(stepLimitMs, remainingMs);
  }
}
