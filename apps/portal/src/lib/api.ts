import "server-only";
import { Configuration, ResolveApi, ResponseError, type Resolution } from "@ugaddress/api-client";

export type ResolveResult =
  | { status: "found"; resolution: Resolution }
  | { status: "not-found" }
  | { status: "invalid" }
  | { status: "unavailable" };

function api(): ResolveApi {
  const basePath = process.env.API_URL ?? "http://localhost:8080";
  return new ResolveApi(
    new Configuration({ basePath, fetchApi: (input, init) => fetch(input, { ...init, cache: "no-store" }) }),
  );
}

/** Resolves a national ID or alias through the register API (server side, anonymous). */
export async function resolveReference(ref: string): Promise<ResolveResult> {
  try {
    return { status: "found", resolution: await api().resolve({ ref }) };
  } catch (error) {
    if (error instanceof ResponseError) {
      if (error.response.status === 404) return { status: "not-found" };
      if (error.response.status === 400) return { status: "invalid" };
    }
    return { status: "unavailable" };
  }
}
