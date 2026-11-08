import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { cleanDisplayFeedback, cleanSpokenFeedback, normalizeGrade } from "./grading";

describe("cleanDisplayFeedback", () => {
  it("keeps balanced inline code spans with their symbols", () => {
    assert.equal(
      cleanDisplayFeedback("Missed recursion, e.g. `fun f(x: Int) = x * f(x - 1)`."),
      "Missed recursion, e.g. `fun f(x: Int) = x * f(x - 1)`.",
    );
  });

  it("strips emphasis and heading marks outside code spans", () => {
    assert.equal(cleanDisplayFeedback("## Missed **the** `#define` case"), "Missed the `#define` case");
  });

  it("keeps symbols that are not markdown", () => {
    assert.equal(cleanDisplayFeedback("In C# it costs O(n * m), not *O(n)*."), "In C# it costs O(n * m), not O(n).");
  });

  it("drops every backtick when they are unbalanced", () => {
    assert.equal(cleanDisplayFeedback("Use `suspend and `Flow` here"), "Use suspend and Flow here");
  });

  it("drops fenced code blocks", () => {
    assert.equal(cleanDisplayFeedback("Missed the cast.\n```kotlin\nval x = y as Int\n```\nCheck it."), "Missed the cast. Check it.");
  });

  it("drops list markers and joins lines", () => {
    assert.equal(cleanDisplayFeedback("Missed:\n1. recursion\n- null returns"), "Missed: recursion null returns");
  });

  it("returns an empty string for blank feedback", () => {
    assert.equal(cleanDisplayFeedback("  \n "), "");
  });
});

describe("cleanSpokenFeedback", () => {
  it("drops backticks and markdown marks but keeps the words", () => {
    assert.equal(cleanSpokenFeedback("Mark it `suspend`, **not** blocking."), "Mark it suspend, not blocking.");
  });

  it("drops fenced code blocks", () => {
    assert.equal(cleanSpokenFeedback("Missed the cast.\n```\nval x = 1\n```"), "Missed the cast.");
  });

  it("unwraps links, strikethrough and quotes", () => {
    assert.equal(cleanSpokenFeedback("> See ~~old~~ [the docs](https://example.com)."), "See old the docs.");
  });
});

describe("normalizeGrade", () => {
  const missedSuspend = "Missed `suspend`.";
  const missedSuspendSpoken = "Missed the suspend keyword.";

  it("clamps and rounds the grade", () => {
    assert.equal(normalizeGrade({ grade: 120.4, feedback: "", spoken_feedback: "" }).gradePercent, 100);
    assert.equal(normalizeGrade({ grade: -3, feedback: missedSuspend, spoken_feedback: "" }).gradePercent, 0);
  });

  it("keeps both cleaned texts", () => {
    assert.deepEqual(normalizeGrade({ grade: 50, feedback: missedSuspend, spoken_feedback: missedSuspendSpoken }), {
      gradePercent: 50,
      feedback: missedSuspend,
      spokenFeedback: missedSuspendSpoken,
    });
  });

  it("speaks the display text without backticks when spoken feedback is empty", () => {
    assert.equal(normalizeGrade({ grade: 50, feedback: missedSuspend, spoken_feedback: " " }).spokenFeedback, "Missed suspend.");
  });

  it("shows the spoken text when display feedback cleans to empty", () => {
    assert.equal(normalizeGrade({ grade: 10, feedback: "```\nx\n```", spoken_feedback: missedSuspendSpoken }).feedback, missedSuspendSpoken);
  });

  it("accepts empty feedback, spoken too, whatever the grade", () => {
    assert.deepEqual(normalizeGrade({ grade: 90, feedback: "", spoken_feedback: "" }), {
      gradePercent: 90,
      feedback: "",
      spokenFeedback: "",
    });
    assert.equal(normalizeGrade({ grade: 10, feedback: "", spoken_feedback: "" }).feedback, "");
  });

  it("rejects a reply without a numeric grade or feedback strings", () => {
    assert.throws(() => normalizeGrade({ grade: Number.NaN, feedback: "", spoken_feedback: "" }));
    assert.throws(() => normalizeGrade({ grade: 50, feedback: undefined, spoken_feedback: "" } as never));
  });
});
