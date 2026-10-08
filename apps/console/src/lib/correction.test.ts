import { describe, expect, it } from "vitest";
import { newIdempotencyKey, validateCorrection, type CorrectionInput } from "./correction";

const valid: CorrectionInput = {
  idempotencyKey: newIdempotencyKey(),
  targetType: "building",
  targetId: "01a11ba5-3bbf-7302-91e1-e9cb6c53d1d2",
  summary: "Plate shows 5A",
  proposedHouseNumber: "5A",
};

describe("validateCorrection", () => {
  it("accepts a complete correction", () => {
    expect(validateCorrection(valid)).toBeUndefined();
  });

  it("rejects bad targets, summaries, house numbers and keys", () => {
    expect(validateCorrection({ ...valid, targetId: "not-a-uuid" })).toBe("target");
    expect(validateCorrection({ ...valid, summary: "  " })).toBe("summary");
    expect(validateCorrection({ ...valid, proposedHouseNumber: "five" })).toBe("houseNumber");
    expect(validateCorrection({ ...valid, idempotencyKey: "short" })).toBe("key");
  });

  it("creates keys the API accepts", () => {
    expect(newIdempotencyKey()).toMatch(/^[A-Za-z0-9_-]{8,128}$/);
    expect(newIdempotencyKey()).not.toBe(newIdempotencyKey());
  });
});
