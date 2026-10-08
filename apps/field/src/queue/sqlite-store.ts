import type { SQLiteDatabase } from "expo-sqlite";
import type { CaptureKind, CaptureStatus, QueueStore, QueuedCapture } from "./types";

interface Row {
  id: string;
  idempotency_key: string;
  captured_at: string;
  longitude: number;
  latitude: number;
  accuracy_meters: number | null;
  kind: string;
  note: string | null;
  photo_uri: string;
  photo_sha256: string;
  status: string;
  attempts: number;
  last_error: string | null;
  change_request_id: string | null;
}

const SCHEMA = `
  CREATE TABLE IF NOT EXISTS capture_queue (
    id TEXT PRIMARY KEY NOT NULL,
    idempotency_key TEXT NOT NULL UNIQUE,
    captured_at TEXT NOT NULL,
    longitude REAL NOT NULL,
    latitude REAL NOT NULL,
    accuracy_meters REAL,
    kind TEXT NOT NULL,
    note TEXT,
    photo_uri TEXT NOT NULL,
    photo_sha256 TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'pending',
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    change_request_id TEXT
  );
  CREATE INDEX IF NOT EXISTS capture_queue_status ON capture_queue (status, captured_at);
`;

function fromRow(row: Row): QueuedCapture {
  return {
    id: row.id,
    idempotencyKey: row.idempotency_key,
    capturedAt: row.captured_at,
    longitude: row.longitude,
    latitude: row.latitude,
    accuracyMeters: row.accuracy_meters,
    kind: row.kind as CaptureKind,
    note: row.note,
    photoUri: row.photo_uri,
    photoSha256: row.photo_sha256,
    status: row.status as CaptureStatus,
    attempts: row.attempts,
    lastError: row.last_error,
    changeRequestId: row.change_request_id,
  };
}

/** Durable queue store on the device (expo-sqlite). Survives app restarts and crashes. */
export class SqliteQueueStore implements QueueStore {
  private constructor(private readonly db: SQLiteDatabase) {}

  static async open(db: SQLiteDatabase): Promise<SqliteQueueStore> {
    await db.execAsync(`PRAGMA journal_mode = WAL; ${SCHEMA}`);
    // Queues created before photo hashes existed get the column; their captures upload without a valid hash and
    // the server rejects them, so no unverified photo is ever accepted.
    const columns = await db.getAllAsync<{ name: string }>("PRAGMA table_info(capture_queue)");
    if (!columns.some((c) => c.name === "photo_sha256")) {
      await db.execAsync("ALTER TABLE capture_queue ADD COLUMN photo_sha256 TEXT NOT NULL DEFAULT ''");
    }
    return new SqliteQueueStore(db);
  }

  async insert(c: QueuedCapture): Promise<void> {
    await this.db.runAsync(
      `INSERT INTO capture_queue (id, idempotency_key, captured_at, longitude, latitude, accuracy_meters, kind, note,
                                  photo_uri, photo_sha256, status, attempts, last_error, change_request_id)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [c.id, c.idempotencyKey, c.capturedAt, c.longitude, c.latitude, c.accuracyMeters, c.kind, c.note, c.photoUri,
        c.photoSha256, c.status, c.attempts, c.lastError, c.changeRequestId],
    );
  }

  async pending(): Promise<QueuedCapture[]> {
    const rows = await this.db.getAllAsync<Row>(
      "SELECT * FROM capture_queue WHERE status = 'pending' ORDER BY captured_at",
    );
    return rows.map(fromRow);
  }

  async all(): Promise<QueuedCapture[]> {
    const rows = await this.db.getAllAsync<Row>("SELECT * FROM capture_queue ORDER BY captured_at DESC");
    return rows.map(fromRow);
  }

  async markSynced(id: string, changeRequestId: string): Promise<void> {
    await this.db.runAsync(
      "UPDATE capture_queue SET status = 'synced', change_request_id = ?, last_error = NULL WHERE id = ?",
      [changeRequestId, id],
    );
  }

  async markAttempt(id: string, error: string): Promise<void> {
    await this.db.runAsync("UPDATE capture_queue SET attempts = attempts + 1, last_error = ? WHERE id = ?", [error, id]);
  }

  async markFailed(id: string, error: string): Promise<void> {
    await this.db.runAsync(
      "UPDATE capture_queue SET status = 'failed', attempts = attempts + 1, last_error = ? WHERE id = ?",
      [error, id],
    );
  }
}
