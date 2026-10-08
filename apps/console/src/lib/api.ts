import "server-only";
import { ChangesApi, Configuration } from "@ugaddress/api-client";
import type { Session } from "./session-crypto";

/** Base URL of the register API as the console's server reaches it. */
export function apiUrl(): string {
  return (process.env.API_URL ?? "http://localhost:8080").replace(/\/+$/, "");
}

/** Change request API on behalf of the signed-in user (bearer token from the session). */
export function changesApi(session: Session): ChangesApi {
  return new ChangesApi(
    new Configuration({
      basePath: apiUrl(),
      accessToken: session.accessToken,
      fetchApi: (url, init) => fetch(url, { ...init, cache: "no-store" }),
    }),
  );
}

/** Whether the user may use the approver inbox. */
export function isApprover(session: Session): boolean {
  return session.groups.includes("custodian-approver");
}
