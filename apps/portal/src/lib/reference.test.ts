import { describe, expect, it } from "vitest";
import { classifyReference, toApiRef } from "./reference";

describe("classifyReference", () => {
  it("accepts national IDs in display form, with dashes and with the DEMO marker", () => {
    // GIVEN the same ID written three ways WHEN classified THEN each yields the 11 digits
    for (const input of ["4821 093 7618", "4821-093-7618", "DEMO 4821 093 7618"]) {
      expect(classifyReference(input)).toEqual({ kind: "nationalId", id: "48210937618" });
    }
  });

  it("recognises aliases", () => {
    expect(classifyReference(" demo-plot:AMA-0001 ")).toEqual({ kind: "alias", system: "demo-plot", value: "AMA-0001" });
    expect(toApiRef(classifyReference("demo-plot:AMA-0001"))).toBe("demo-plot:AMA-0001");
  });

  it("reports a wrong check digit separately from a wrong format", () => {
    // GIVEN a single-digit typo WHEN classified THEN the check digit catches it
    expect(classifyReference("4821 093 7619")).toEqual({ kind: "invalid", reason: "checkDigit" });
    expect(classifyReference("4821 093")).toEqual({ kind: "invalid", reason: "format" });
    expect(classifyReference("   ")).toEqual({ kind: "empty" });
    expect(toApiRef({ kind: "empty" })).toBeUndefined();
  });
});
