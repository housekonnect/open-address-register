import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import {
  InvalidNationalIdError,
  format,
  formatAsDemonstration,
  fromPayload,
  generate,
  parse,
  tryParse,
  validate,
  type NationalId,
  type RandomSource,
} from "../src/index.js";

interface Vectors {
  checkDigit: { payload: string; id: string }[];
  valid: { id: string; display: string; demonstrationDisplay: string }[];
  invalid: { input: string; reason: string }[];
  parse: { input: string; id: string }[];
}

const vectorsPath = fileURLToPath(
  new URL("../../../contracts/test-vectors/national-id.json", import.meta.url),
);
const vectors = JSON.parse(readFileSync(vectorsPath, "utf8")) as Vectors;

/** Deterministic PRNG (mulberry32) used as the injected random source. */
function seeded(seed: number): RandomSource {
  let state = seed >>> 0;
  return (maxExclusive) => {
    state = (state + 0x6d2b79f5) >>> 0;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return Math.floor((((t ^ (t >>> 14)) >>> 0) / 0x1_0000_0000) * maxExclusive);
  };
}

const random = seeded(20261008);
const randomIds: NationalId[] = Array.from({ length: 1000 }, () => generate(random));

describe("shared test vectors", () => {
  it.each(vectors.checkDigit)("appends the Damm check digit to $payload", ({ payload, id }) => {
    // GIVEN a payload WHEN the check digit is appended THEN the ID matches the vector
    expect(fromPayload(payload)).toBe(id);
  });

  it.each(vectors.valid)("formats $id", ({ id, display, demonstrationDisplay }) => {
    // GIVEN a valid ID
    const nationalId = parse(id);
    // WHEN it is formatted THEN both display forms match and parse back
    expect(format(nationalId)).toBe(display);
    expect(formatAsDemonstration(nationalId)).toBe(demonstrationDisplay);
    expect(parse(display)).toBe(id);
    expect(parse(demonstrationDisplay)).toBe(id);
  });

  it.each(vectors.invalid)("rejects $reason", ({ input }) => {
    // GIVEN an invalid input WHEN it is validated or parsed THEN it is rejected
    expect(validate(input)).toBe(false);
    expect(tryParse(input)).toBeUndefined();
    expect(() => parse(input)).toThrow(InvalidNationalIdError);
  });

  it.each(vectors.parse)("parses '$input'", ({ input, id }) => {
    // GIVEN input with separators or a demonstration marker WHEN parsed THEN they are ignored
    expect(parse(input)).toBe(id);
  });
});

describe("error detection over 1,000 random IDs", () => {
  it.each(randomIds)("rejects every single-digit substitution of %s", (id) => {
    // GIVEN a valid generated ID
    expect(validate(id)).toBe(true);
    for (let position = 0; position < id.length; position++) {
      for (let digit = 0; digit <= 9; digit++) {
        const replacement = String(digit);
        if (replacement === id[position]) continue;
        // WHEN one digit is replaced THEN the result is rejected
        const mutated = id.slice(0, position) + replacement + id.slice(position + 1);
        expect(validate(mutated), `substitution at ${String(position)}`).toBe(false);
      }
    }
  });

  it.each(randomIds)("rejects every adjacent transposition of %s", (id) => {
    for (let position = 0; position < id.length - 1; position++) {
      const a = id.charAt(position);
      const b = id.charAt(position + 1);
      // Swapping identical digits changes nothing, so there is no error to detect.
      if (a === b) continue;
      // WHEN two adjacent different digits are swapped THEN the result is rejected
      const mutated = id.slice(0, position) + b + a + id.slice(position + 2);
      expect(validate(mutated), `transposition at ${String(position)}`).toBe(false);
    }
  });
});

describe("generation", () => {
  it("is reproducible with an injected random source", () => {
    // GIVEN two sources with the same seed WHEN both generate THEN the IDs are equal and valid
    const a = generate(seeded(42));
    const b = generate(seeded(42));
    expect(a).toBe(b);
    expect(validate(a)).toBe(true);
  });

  it("uses a secure default source", () => {
    expect(validate(generate())).toBe(true);
  });

  it("never echoes the input in error messages", () => {
    // GIVEN a mistyped ID WHEN it is parsed THEN the error message does not contain the input
    const input = "48210937619";
    let message = "";
    try {
      parse(input);
    } catch (error) {
      message = error instanceof Error ? error.message : "";
    }
    expect(message).not.toBe("");
    expect(message).not.toContain(input);
  });
});
