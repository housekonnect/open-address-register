/**
 * National address IDs: 10 random digits followed by one Damm check digit (ADR 0005).
 * TypeScript port of `backend/national-id`; both are tested against
 * `contracts/test-vectors/national-id.json`.
 */

export const PAYLOAD_LENGTH = 10;
export const LENGTH = PAYLOAD_LENGTH + 1;
export const DEMONSTRATION_MARKER = "DEMO";

/** Returns a uniformly distributed integer in `[0, maxExclusive)`. */
export type RandomSource = (maxExclusive: number) => number;

/** Thrown when input is not a valid national ID. The message never echoes the input. */
export class InvalidNationalIdError extends Error {
  override readonly name = "InvalidNationalIdError";
}

/** Weakly totally anti-symmetric quasigroup of order 10 (H. M. Damm, 2004). */
const DAMM_TABLE: readonly (readonly number[])[] = [
  [0, 3, 1, 7, 5, 9, 8, 6, 4, 2],
  [7, 0, 9, 2, 1, 5, 4, 8, 6, 3],
  [4, 2, 0, 6, 8, 7, 1, 3, 5, 9],
  [1, 7, 5, 0, 9, 8, 3, 4, 2, 6],
  [6, 1, 2, 3, 0, 4, 5, 9, 7, 8],
  [3, 6, 7, 4, 2, 0, 9, 5, 8, 1],
  [5, 8, 6, 9, 7, 2, 0, 1, 3, 4],
  [8, 9, 4, 5, 3, 6, 2, 0, 1, 7],
  [9, 4, 3, 8, 6, 1, 7, 2, 0, 5],
  [2, 5, 8, 1, 4, 3, 6, 7, 9, 0],
];

const DIGITS = /^[0-9]*$/;

/** Computes the Damm check digit of a string of ASCII digits. */
export function dammCheckDigit(digits: string): number {
  if (!DIGITS.test(digits)) {
    throw new InvalidNationalIdError("Only digits are allowed");
  }
  let interim = 0;
  for (const char of digits) {
    interim = DAMM_TABLE[interim]?.[char.charCodeAt(0) - 48] ?? 0;
  }
  return interim;
}

/** A valid national ID, represented by its 11 digits without separators. */
export type NationalId = string & { readonly __brand: "NationalId" };

function requireValid(digits: string): NationalId {
  if (digits.length !== LENGTH) {
    throw new InvalidNationalIdError(`A national ID has exactly ${String(LENGTH)} digits`);
  }
  if (!DIGITS.test(digits)) {
    throw new InvalidNationalIdError("A national ID contains only digits");
  }
  if (dammCheckDigit(digits) !== 0) {
    throw new InvalidNationalIdError("Check digit does not match");
  }
  return digits as NationalId;
}

/** Creates an ID from a 10-digit payload by appending its Damm check digit. */
export function fromPayload(payload: string): NationalId {
  if (payload.length !== PAYLOAD_LENGTH || !DIGITS.test(payload)) {
    throw new InvalidNationalIdError(`Payload must be exactly ${String(PAYLOAD_LENGTH)} digits`);
  }
  return requireValid(payload + String(dammCheckDigit(payload)));
}

/**
 * Parses user input. Spaces and dashes are ignored, as is a leading `DEMO` marker
 * (case-insensitive), so every display form parses back to its ID.
 */
export function parse(input: string): NationalId {
  let text = input.trim();
  if (text.toUpperCase().startsWith(DEMONSTRATION_MARKER)) {
    text = text.slice(DEMONSTRATION_MARKER.length);
  }
  const digits = text.replace(/[ -]/g, "");
  if (!DIGITS.test(digits)) {
    throw new InvalidNationalIdError("Only digits, spaces and dashes are allowed");
  }
  return requireValid(digits);
}

/** Like {@link parse}, but returns `undefined` instead of throwing. */
export function tryParse(input: string): NationalId | undefined {
  try {
    return parse(input);
  } catch (error) {
    if (error instanceof InvalidNationalIdError) {
      return undefined;
    }
    throw error;
  }
}

/** Checks whether user input is a valid ID, accepting the same forms as {@link parse}. */
export function validate(input: string): boolean {
  return tryParse(input) !== undefined;
}

/** Returns the display form, grouped 4-3-4, e.g. `4821 093 7618`. */
export function format(id: NationalId): string {
  return `${id.slice(0, 4)} ${id.slice(4, 7)} ${id.slice(7)}`;
}

/** Returns the display form of a demonstration ID, e.g. `DEMO 4821 093 7618`. */
export function formatAsDemonstration(id: NationalId): string {
  return `${DEMONSTRATION_MARKER} ${format(id)}`;
}

/** Cryptographically secure default random source (Web Crypto, available in browsers, Node and Hermes). */
export const secureRandom: RandomSource = (maxExclusive) => {
  const limit = Math.floor(0x1_0000_0000 / maxExclusive) * maxExclusive;
  const buffer = new Uint32Array(1);
  for (;;) {
    crypto.getRandomValues(buffer);
    const value = buffer[0] ?? 0;
    if (value < limit) {
      return value % maxExclusive;
    }
  }
};

/** Generates a new ID: 10 uniformly random digits plus the Damm check digit. */
export function generate(random: RandomSource = secureRandom): NationalId {
  let payload = "";
  for (let i = 0; i < PAYLOAD_LENGTH; i++) {
    payload += String(random(10));
  }
  return fromPayload(payload);
}
