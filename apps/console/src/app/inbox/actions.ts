"use server";

import { ResponseError } from "@ugaddress/api-client";
import { revalidatePath } from "next/cache";
import { changesApi } from "@/lib/api";
import { currentSession } from "@/lib/session";

export type DecisionResult =
  | { status: "decided"; state: string }
  | { status: "error"; reason: "unauthorized" | "forbidden" | "conflict" | "reason" | "failed" };

async function decide(id: string, call: (api: ReturnType<typeof changesApi>) => Promise<{ state: string }>): Promise<DecisionResult> {
  const session = await currentSession();
  if (!session) return { status: "error", reason: "unauthorized" };
  try {
    const decided = await call(changesApi(session));
    revalidatePath("/inbox");
    revalidatePath(`/inbox/${id}`);
    return { status: "decided", state: decided.state };
  } catch (error) {
    if (error instanceof ResponseError) {
      if (error.response.status === 401) return { status: "error", reason: "unauthorized" };
      if (error.response.status === 403) return { status: "error", reason: "forbidden" };
      if (error.response.status === 409) return { status: "error", reason: "conflict" };
      if (error.response.status === 400) return { status: "error", reason: "reason" };
    }
    console.error("Deciding a change request failed", error instanceof ResponseError ? error.response.status : error);
    return { status: "error", reason: "failed" };
  }
}

/** Approves a change request (the API enforces the four-eyes rule and the jurisdiction). */
export async function approveChangeRequest(id: string, idempotencyKey: string): Promise<DecisionResult> {
  return decide(id, (api) => api.approveChangeRequest({ id, idempotencyKey }));
}

/** Returns a change request to its proposer with a written reason. */
export async function returnChangeRequest(id: string, reason: string, idempotencyKey: string): Promise<DecisionResult> {
  if (reason.trim().length < 3 || reason.trim().length > 500) return { status: "error", reason: "reason" };
  return decide(id, (api) =>
    api.returnChangeRequest({ id, idempotencyKey, changeRequestReturn: { reason: reason.trim() } }),
  );
}
