"use server";

import { ResponseError } from "@ugaddress/api-client";
import { changesApi } from "@/lib/api";
import { validateCorrection, type CorrectionInput, type CorrectionProblem } from "@/lib/correction";
import { currentSession } from "@/lib/session";

export type SubmitResult =
  | { status: "submitted"; id: string; state: string }
  | { status: "error"; reason: CorrectionProblem | "unauthorized" | "forbidden" | "failed" };

/** Submits a correction as a change request on behalf of the signed-in custodian editor. */
export async function submitCorrection(input: CorrectionInput): Promise<SubmitResult> {
  const session = await currentSession();
  if (!session) return { status: "error", reason: "unauthorized" };
  const problem = validateCorrection(input);
  if (problem) return { status: "error", reason: problem };

  const api = changesApi(session);
  try {
    const created = await api.createChangeRequest({
      idempotencyKey: input.idempotencyKey,
      changeRequestCreate: {
        kind: "correction",
        summary: input.summary.trim(),
        ...(input.targetType === "building" ? { targetObjectId: input.targetId } : { thoroughfareId: input.targetId }),
        ...(input.proposedHouseNumber ? { proposedHouseNumber: input.proposedHouseNumber.trim() } : {}),
      },
    });
    return { status: "submitted", id: created.id, state: created.state };
  } catch (error) {
    if (error instanceof ResponseError) {
      if (error.response.status === 401) return { status: "error", reason: "unauthorized" };
      if (error.response.status === 403) return { status: "error", reason: "forbidden" };
    }
    console.error("Submitting a correction failed", error instanceof ResponseError ? error.response.status : error);
    return { status: "error", reason: "failed" };
  }
}
