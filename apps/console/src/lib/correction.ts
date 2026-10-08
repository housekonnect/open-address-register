/** Input of the correction form, validated on the server before it reaches the register API. */
export interface CorrectionInput {
  idempotencyKey: string;
  targetType: "street" | "building";
  targetId: string;
  summary: string;
  proposedHouseNumber?: string | undefined;
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const KEY = /^[A-Za-z0-9_-]{8,128}$/;
const HOUSE_NUMBER = /^[0-9]{1,5}[A-Z]?$/;

export type CorrectionProblem = "target" | "summary" | "houseNumber" | "key";

/** Returns the first problem with the input, or undefined if it is valid. */
export function validateCorrection(input: CorrectionInput): CorrectionProblem | undefined {
  if (!KEY.test(input.idempotencyKey)) return "key";
  if (!UUID.test(input.targetId) || (input.targetType !== "street" && input.targetType !== "building")) return "target";
  const summary = input.summary.trim();
  if (summary.length < 3 || summary.length > 500) return "summary";
  if (input.proposedHouseNumber && !HOUSE_NUMBER.test(input.proposedHouseNumber.trim())) return "houseNumber";
  return undefined;
}

/** A fresh idempotency key for one submission; retries of the same submission reuse it. */
export function newIdempotencyKey(): string {
  return crypto.randomUUID().replaceAll("-", "");
}
