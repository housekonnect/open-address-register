import { FieldCaptureMetadataToJSON, type FieldCapture, type FieldCaptureMetadata } from "@ugaddress/api-client";
import type { QueuedCapture, UploadResult, Uploader } from "../queue/types";

/** Problem title of a photo whose stored bytes differ from the device's SHA-256 (see the API contract). */
export const PHOTO_INTEGRITY_TITLE = "Photo integrity check failed";

export interface UploaderOptions {
  apiUrl: string;
  /** Returns a valid access token, refreshing it if needed; undefined if the user must sign in. */
  getAccessToken: () => Promise<string | undefined>;
  /** Writes the metadata JSON to a local file and returns its URI. */
  writeMetadataFile: (capture: QueuedCapture, json: string) => Promise<string>;
  /**
   * A multipart part with a file name and content type whose bytes come from a local file. Expo's fetch only
   * accepts Blobs or objects with `bytes()`, not React Native's `{ uri, name, type }` descriptors.
   */
  filePart: (uri: string, name: string, type: string) => Blob;
  fetchImpl?: typeof fetch;
}

/** Builds the multipart metadata of a capture, typed by the generated API client. */
export function captureMetadata(capture: QueuedCapture): FieldCaptureMetadata {
  return {
    capturedAt: new Date(capture.capturedAt),
    location: { type: "Point", coordinates: [capture.longitude, capture.latitude] },
    accuracyMeters: capture.accuracyMeters,
    kind: capture.kind,
    note: capture.note,
    photoSha256: capture.photoSha256,
    locationMocked: capture.locationMocked,
  };
}

/**
 * Uploads captures to `POST /v1/field/captures`. The photo and the metadata are sent as file parts with explicit
 * content types, the idempotency key as a header.
 */
export function createUploader(options: UploaderOptions): Uploader {
  const fetchImpl = options.fetchImpl ?? fetch;
  return async (capture): Promise<UploadResult> => {
    const token = await options.getAccessToken();
    if (!token) return { ok: false, failure: { kind: "unauthorized" } };

    const metadataJson = JSON.stringify(FieldCaptureMetadataToJSON(captureMetadata(capture)));
    const metadataUri = await options.writeMetadataFile(capture, metadataJson);
    const body = new FormData();
    body.append("metadata", options.filePart(metadataUri, "metadata.json", "application/json"));
    body.append("photo", options.filePart(capture.photoUri, `${capture.id}.jpg`, "image/jpeg"));

    let response: Response;
    try {
      response = await fetchImpl(`${options.apiUrl.replace(/\/+$/, "")}/v1/field/captures`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Idempotency-Key": capture.idempotencyKey, Accept: "application/json" },
        body,
      });
    } catch (error) {
      // No response at all: offline, unreachable host, or a part that could not be read. Kept for retry.
      console.warn("Capture upload got no response:", error instanceof Error ? error.message : String(error));
      return { ok: false, failure: { kind: "network" } };
    }
    if (response.status === 201 || response.status === 200) {
      const created = (await response.json()) as FieldCapture;
      return { ok: true, changeRequestId: created.changeRequestId };
    }
    if (response.status === 401) return { ok: false, failure: { kind: "unauthorized" } };
    if (response.status >= 500 || response.status === 429) return { ok: false, failure: { kind: "server", status: response.status } };
    const problem = await response
      .json()
      .then((body: { detail?: string; title?: string }) => body)
      .catch(() => ({}) as { detail?: string; title?: string });
    // The stored photo did not match the hash taken on the device: the server kept nothing. The photo was hashed
    // when it was taken, so the bytes most likely changed in transit; keep the capture and retry on the next sync.
    if (response.status === 422 && problem.title === PHOTO_INTEGRITY_TITLE) {
      return { ok: false, failure: { kind: "server", status: response.status } };
    }
    return { ok: false, failure: { kind: "rejected", status: response.status, detail: problem.detail ?? problem.title ?? "" } };
  };
}
