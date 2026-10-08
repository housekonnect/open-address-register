import { describe, expect, it } from "vitest";
import { ChangesApi, Configuration, ResolveApi, ResponseError } from "../src";

function recordingFetch(status: number, body: unknown) {
  const calls: { url: string; init: RequestInit | undefined }[] = [];
  const fetchApi = (input: RequestInfo | URL, init?: RequestInit) => {
    calls.push({ url: input instanceof Request ? input.url : String(input), init });
    return Promise.resolve(
      new Response(JSON.stringify(body), {
        status,
        headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
      }),
    );
  };
  return { calls, fetchApi };
}

describe("generated client", () => {
  it("sends the Idempotency-Key header on POST /v1/change-requests", async () => {
    // GIVEN a client with a recording fetch
    const { calls, fetchApi } = recordingFetch(201, {
      id: "0199a0e0-0000-7000-8000-000000000001",
      kind: "correction",
      state: "submitted",
      summary: "House number plate shows 12, register says 14",
      adminUnitId: "0199a0e0-0000-7000-8000-000000000002",
      createdAt: "2026-10-08T12:00:00Z",
    });
    const api = new ChangesApi(new Configuration({ basePath: "http://api.test", fetchApi }));

    // WHEN a change request is created
    const created = await api.createChangeRequest({
      idempotencyKey: "a1b2c3d4e5f6",
      changeRequestCreate: { kind: "correction", summary: "House number plate shows 12, register says 14" },
    });

    // THEN the key is sent and the response is mapped
    const headers = new Headers(calls[0]?.init?.headers);
    expect(calls[0]?.url).toBe("http://api.test/v1/change-requests");
    expect(headers.get("Idempotency-Key")).toBe("a1b2c3d4e5f6");
    expect(created.createdAt).toBeInstanceOf(Date);
  });

  it("surfaces problem details as a ResponseError", async () => {
    // GIVEN an API that answers 404 with a problem detail
    const { fetchApi } = recordingFetch(404, { title: "Not found", status: 404 });
    const api = new ResolveApi(new Configuration({ basePath: "http://api.test", fetchApi }));

    // WHEN resolving an unknown reference THEN a ResponseError with status 404 is thrown
    await expect(api.resolve({ ref: "00000000000" })).rejects.toBeInstanceOf(ResponseError);
  });
});
