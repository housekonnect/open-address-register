import { dammCheckDigit, tryParse } from "@ugaddress/national-id";

/** What the user typed into the search box. */
export type Reference =
  | { kind: "empty" }
  | { kind: "alias"; system: string; value: string }
  | { kind: "nationalId"; id: string }
  | { kind: "invalid"; reason: "checkDigit" | "format" };

/**
 * Classifies search input: a national ID in any display form, or an alias `<system>:<value>`.
 * A string of 11 digits with a wrong check digit is reported separately, so the user can fix the typo.
 */
export function classifyReference(input: string): Reference {
  const text = input.trim();
  if (text === "") return { kind: "empty" };
  const colon = text.indexOf(":");
  if (colon > 0 && colon < text.length - 1) {
    return { kind: "alias", system: text.slice(0, colon), value: text.slice(colon + 1) };
  }
  const id = tryParse(text);
  if (id) return { kind: "nationalId", id };
  const digits = text.replace(/^demo/i, "").replace(/[\s-]/g, "");
  if (/^[0-9]{11}$/.test(digits) && dammCheckDigit(digits) !== 0) {
    return { kind: "invalid", reason: "checkDigit" };
  }
  return { kind: "invalid", reason: "format" };
}

/** The value sent to the API's `ref` parameter. */
export function toApiRef(reference: Reference): string | undefined {
  switch (reference.kind) {
    case "alias":
      return `${reference.system}:${reference.value}`;
    case "nationalId":
      return reference.id;
    default:
      return undefined;
  }
}
