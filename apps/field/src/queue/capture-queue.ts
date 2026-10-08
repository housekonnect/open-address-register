import type { NewCapture, QueueStore, QueuedCapture, Uploader } from "./types";

export interface SyncReport {
  synced: number;
  failed: number;
  remaining: number;
  /** Why syncing stopped early, if it did. */
  stoppedBy: "network" | "unauthorized" | "server" | null;
}

/**
 * Offline-first queue of field captures.
 *
 * Captures are written to the store first and uploaded later. Each capture gets its idempotency key when it is
 * created, so a retry after a lost response never creates a duplicate on the server.
 */
export class CaptureQueue {
  private syncing: Promise<SyncReport> | null = null;

  constructor(
    private readonly store: QueueStore,
    private readonly newId: () => string,
    private readonly now: () => Date = () => new Date(),
  ) {}

  /** Stores a new capture; works without connectivity. */
  async enqueue(capture: NewCapture): Promise<QueuedCapture> {
    const queued: QueuedCapture = {
      ...capture,
      id: this.newId(),
      idempotencyKey: this.newId().replaceAll("-", ""),
      capturedAt: this.now().toISOString(),
      status: "pending",
      attempts: 0,
      lastError: null,
      changeRequestId: null,
    };
    await this.store.insert(queued);
    return queued;
  }

  list(): Promise<QueuedCapture[]> {
    return this.store.all();
  }

  /**
   * Uploads pending captures, oldest first. Stops at the first network, authorization or server error so the
   * remaining captures keep their order; captures the server rejects permanently are marked failed.
   * Concurrent calls share one run.
   */
  sync(upload: Uploader): Promise<SyncReport> {
    if (!this.syncing) {
      this.syncing = this.run(upload).finally(() => {
        this.syncing = null;
      });
    }
    return this.syncing;
  }

  private async run(upload: Uploader): Promise<SyncReport> {
    const pending = await this.store.pending();
    let synced = 0;
    let failed = 0;
    for (const [index, capture] of pending.entries()) {
      const result = await upload(capture);
      if (result.ok) {
        await this.store.markSynced(capture.id, result.changeRequestId);
        synced++;
        continue;
      }
      const failure = result.failure;
      if (failure.kind === "rejected") {
        await this.store.markFailed(capture.id, `${failure.status}: ${failure.detail}`);
        failed++;
        continue;
      }
      await this.store.markAttempt(capture.id, failure.kind === "server" ? `server ${failure.status}` : failure.kind);
      return { synced, failed, remaining: pending.length - index, stoppedBy: failure.kind };
    }
    return { synced, failed, remaining: 0, stoppedBy: null };
  }
}
