/** Kinds of objects a verifier can capture (matches the API contract's ObjectKind). */
export type CaptureKind = "building" | "entrance" | "access_point" | "landmark" | "facility";

export type CaptureStatus = "pending" | "synced" | "failed";

/** A capture stored on the device until it has reached the register. */
export interface QueuedCapture {
  id: string;
  /** Generated once when the capture is made; every retry sends the same key, so the server applies it once. */
  idempotencyKey: string;
  capturedAt: string;
  longitude: number;
  latitude: number;
  accuracyMeters: number | null;
  kind: CaptureKind;
  note: string | null;
  /** Local file URI of the photo, inside the app's document directory. */
  photoUri: string;
  /** SHA-256 of the photo bytes (lower-case hex), computed on the device when the photo was taken. */
  photoSha256: string;
  status: CaptureStatus;
  attempts: number;
  lastError: string | null;
  changeRequestId: string | null;
}

export type NewCapture = Pick<
  QueuedCapture,
  "longitude" | "latitude" | "accuracyMeters" | "kind" | "note" | "photoUri" | "photoSha256"
>;

/** Persistence of the queue. Implemented with expo-sqlite on devices and in memory in tests. */
export interface QueueStore {
  insert(capture: QueuedCapture): Promise<void>;
  /** Pending captures, oldest first. */
  pending(): Promise<QueuedCapture[]>;
  all(): Promise<QueuedCapture[]>;
  markSynced(id: string, changeRequestId: string): Promise<void>;
  markAttempt(id: string, error: string): Promise<void>;
  markFailed(id: string, error: string): Promise<void>;
}

/** Why an upload did not succeed. Decides whether the queue retries. */
export type UploadFailure =
  | { kind: "network" }
  | { kind: "unauthorized" }
  | { kind: "server"; status: number }
  | { kind: "rejected"; status: number; detail: string };

export type UploadResult = { ok: true; changeRequestId: string } | { ok: false; failure: UploadFailure };

/** Sends one capture to the register. */
export type Uploader = (capture: QueuedCapture) => Promise<UploadResult>;
