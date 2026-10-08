import type { QueueStore, QueuedCapture } from "./types";

/** In-memory queue store, used in tests. */
export class MemoryQueueStore implements QueueStore {
  private readonly rows = new Map<string, QueuedCapture>();

  async insert(capture: QueuedCapture): Promise<void> {
    this.rows.set(capture.id, { ...capture });
  }

  async pending(): Promise<QueuedCapture[]> {
    return (await this.all()).filter((c) => c.status === "pending");
  }

  async all(): Promise<QueuedCapture[]> {
    return [...this.rows.values()].sort((a, b) => a.capturedAt.localeCompare(b.capturedAt)).map((c) => ({ ...c }));
  }

  async markSynced(id: string, changeRequestId: string): Promise<void> {
    this.update(id, { status: "synced", changeRequestId, lastError: null });
  }

  async markAttempt(id: string, error: string): Promise<void> {
    const row = this.rows.get(id);
    if (row) this.update(id, { attempts: row.attempts + 1, lastError: error });
  }

  async markFailed(id: string, error: string): Promise<void> {
    const row = this.rows.get(id);
    if (row) this.update(id, { status: "failed", attempts: row.attempts + 1, lastError: error });
  }

  private update(id: string, patch: Partial<QueuedCapture>): void {
    const row = this.rows.get(id);
    if (row) this.rows.set(id, { ...row, ...patch });
  }
}
