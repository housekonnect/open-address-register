import type { QueuedCapture } from "../queue/types";
import { captureMetadata, createUploader } from "./uploader";

const capture: QueuedCapture = {
  id: "00000000-0000-4000-8000-000000000001",
  idempotencyKey: "abcdef0123456789",
  capturedAt: "2026-10-08T09:00:00.000Z",
  longitude: 32.5945,
  latitude: 0.3502,
  accuracyMeters: 4,
  kind: "building",
  note: "No plate yet",
  photoUri: "file:///captures/1.jpg",
  photoSha256: "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
  status: "pending",
  attempts: 0,
  lastError: null,
  changeRequestId: null,
};

function uploaderWith(response: Response | Error, token: string | null = "token-1") {
  const requests: { url: string; init: RequestInit }[] = [];
  const fetchImpl = (async (url: string, init: RequestInit) => {
    requests.push({ url, init });
    if (response instanceof Error) throw response;
    return response;
  }) as unknown as typeof fetch;
  const upload = createUploader({
    apiUrl: "http://api.test/",
    getAccessToken: async () => token ?? undefined,
    writeMetadataFile: async () => "file:///cache/metadata.json",
    fetchImpl,
  });
  return { upload, requests };
}

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

describe("createUploader", () => {
  it("posts the capture with its idempotency key and bearer token", async () => {
    // GIVEN a server that accepts the capture
    const { upload, requests } = uploaderWith(
      json(201, { id: "cr-1", changeRequestId: "cr-1", photoStored: true, receivedAt: "2026-10-08T09:01:00Z" }),
    );
    // WHEN the capture is uploaded
    const result = await upload(capture);
    // THEN the request carries the key and token and the change request id is returned
    expect(result).toEqual({ ok: true, changeRequestId: "cr-1" });
    const headers = requests[0]?.init.headers as Record<string, string>;
    expect(requests[0]?.url).toBe("http://api.test/v1/field/captures");
    expect(headers["Idempotency-Key"]).toBe("abcdef0123456789");
    expect(headers.Authorization).toBe("Bearer token-1");
  });

  it("maps failures to retry decisions", async () => {
    expect((await uploaderWith(new TypeError("Network request failed")).upload(capture))).toEqual({ ok: false, failure: { kind: "network" } });
    expect((await uploaderWith(json(401, {})).upload(capture))).toEqual({ ok: false, failure: { kind: "unauthorized" } });
    expect((await uploaderWith(json(503, {})).upload(capture))).toEqual({ ok: false, failure: { kind: "server", status: 503 } });
    expect((await uploaderWith(json(403, { detail: "outside jurisdiction" })).upload(capture))).toEqual({
      ok: false,
      failure: { kind: "rejected", status: 403, detail: "outside jurisdiction" },
    });
    expect((await uploaderWith(json(201, {}), null).upload(capture))).toEqual({ ok: false, failure: { kind: "unauthorized" } });
  });

  it("retries a capture whose stored photo did not match the device's hash", async () => {
    // GIVEN the server reports a photo integrity mismatch (it kept nothing)
    const mismatch = json(422, { title: "Photo integrity check failed", status: 422, detail: "Nothing was kept" });
    // WHEN the capture is uploaded
    // THEN the capture stays pending for the next sync instead of being marked failed
    expect(await uploaderWith(mismatch).upload(capture)).toEqual({ ok: false, failure: { kind: "server", status: 422 } });
    // AND a reused idempotency key (also 422) is still a permanent rejection
    expect(await uploaderWith(json(422, { title: "Idempotency key reused", detail: "reused" })).upload(capture)).toEqual({
      ok: false,
      failure: { kind: "rejected", status: 422, detail: "reused" },
    });
  });

  it("builds contract-conformant metadata", () => {
    expect(captureMetadata(capture)).toMatchObject({
      kind: "building",
      location: { type: "Point", coordinates: [32.5945, 0.3502] },
      note: "No plate yet",
      photoSha256: "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
    });
  });
});
