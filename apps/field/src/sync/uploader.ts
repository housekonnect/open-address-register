import { FieldCaptureMetadataToJSON, type FieldCapture, type FieldCaptureMetadata } from "@ugaddress/api-client";
import type { QueuedCapture, UploadResult, Uploader } from "../queue/types";

/** Problem title of a photo whose stored bytes differ from the device's SHA-256 (see the API contract). */
export const PHOTO_INTEGRITY_TITLE = "Photo integrity check failed";

export interface UploaderOptions {
  apiUrl: string;
  /** Returns a valid access token, refreshing it if needed; undefined if the user must sign in. */
  getAccessToken: () => Promise<string | undefined>;
  /** Writes the metadata JSON to a local file and returns its URI (React Native sends parts from files). */
  writeMetadataFile: (capture: QueuedCapture, json: string) => Promise<string>;
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
    // React Native's FormData accepts { uri, name, type } descriptors for file parts.
    body.append("metadata", { uri: metadataUri, name: "metadata.json", type: "application/json" } as unknown as Blob);
    body.append("photo", { uri: capture.photoUri, name: `${capture.id}.jpg`, type: "image/jpeg" } as unknown as Blob);

    let response: Response;
    try {
      response = await fetchImpl(`${options.apiUrl.replace(/\/+$/, "")}/v1/field/captures`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Idempotency-Key": capture.idempotencyKey, Accept: "application/json" },
        body,
      });
    } catch {
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
