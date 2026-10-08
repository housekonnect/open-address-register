import { CaptureQueue } from "./capture-queue";
import { MemoryQueueStore } from "./memory-store";
import type { NewCapture, QueuedCapture, UploadResult, Uploader } from "./types";

/** A fake register that, like the real API, applies each idempotency key at most once. */
class FakeRegister {
  online = false;
  /** When set, the server processes the request but the response is lost on the way back. */
  loseNextResponse = false;
  readonly changeRequests = new Map<string, string>();
  requests = 0;

  readonly upload: Uploader = async (capture: QueuedCapture): Promise<UploadResult> => {
    this.requests++;
    if (!this.online) return { ok: false, failure: { kind: "network" } };
    let id = this.changeRequests.get(capture.idempotencyKey);
    if (!id) {
      id = `cr-${this.changeRequests.size + 1}`;
      this.changeRequests.set(capture.idempotencyKey, id);
    }
    if (this.loseNextResponse) {
      this.loseNextResponse = false;
      return { ok: false, failure: { kind: "network" } };
    }
    return { ok: true, changeRequestId: id };
  };
}

const capture = (note: string): NewCapture => ({
  longitude: 32.5945,
  latitude: 0.3502,
  accuracyMeters: 4,
  kind: "building",
  note,
  photoUri: `file:///captures/${note}.jpg`,
});

function newQueue() {
  let counter = 0;
  let clock = Date.parse("2026-10-08T09:00:00Z");
  const store = new MemoryQueueStore();
  const queue = new CaptureQueue(
    store,
    () => `00000000-0000-4000-8000-${String(++counter).padStart(12, "0")}`,
    () => new Date((clock += 1000)),
  );
  return { queue, store };
}

describe("CaptureQueue", () => {
  it("stores captures while offline and keeps them pending", async () => {
    // GIVEN an offline device
    const { queue, store } = newQueue();
    const register = new FakeRegister();

    // WHEN two captures are made and a sync is attempted
    await queue.enqueue(capture("first"));
    await queue.enqueue(capture("second"));
    const report = await queue.sync(register.upload);

    // THEN nothing reached the server and both captures wait, with the error recorded
    expect(report).toEqual({ synced: 0, failed: 0, remaining: 2, stoppedBy: "network" });
    expect(register.changeRequests.size).toBe(0);
    const pending = await store.pending();
    expect(pending.map((c) => c.note)).toEqual(["first", "second"]);
    expect(pending[0]?.attempts).toBe(1);
    expect(pending[0]?.lastError).toBe("network");
  });

  it("uploads everything in order once back online", async () => {
    // GIVEN captures made offline
    const { queue } = newQueue();
    const register = new FakeRegister();
    await queue.enqueue(capture("first"));
    await queue.enqueue(capture("second"));
    await queue.sync(register.upload);

    // WHEN connectivity returns and the queue syncs
    register.online = true;
    const report = await queue.sync(register.upload);

    // THEN both are synced and linked to their change requests
    expect(report).toEqual({ synced: 2, failed: 0, remaining: 0, stoppedBy: null });
    expect((await queue.list()).map((c) => [c.note, c.status, c.changeRequestId])).toEqual([
      ["first", "synced", "cr-1"],
      ["second", "synced", "cr-2"],
    ]);
  });

  it("never creates a duplicate when a response is lost and the sync is retried", async () => {
    // GIVEN a capture whose upload succeeds on the server but whose response is lost
    const { queue } = newQueue();
    const register = new FakeRegister();
    register.online = true;
    register.loseNextResponse = true;
    const queued = await queue.enqueue(capture("lost-response"));

    // WHEN the sync fails on the client and is retried
    const first = await queue.sync(register.upload);
    const second = await queue.sync(register.upload);

    // THEN the same idempotency key was sent twice and exactly one change request exists
    expect(first.stoppedBy).toBe("network");
    expect(second.synced).toBe(1);
    expect(register.requests).toBe(2);
    expect(register.changeRequests.size).toBe(1);
    expect([...register.changeRequests.keys()]).toEqual([queued.idempotencyKey]);
  });

  it("marks captures the server rejects as failed and continues with the rest", async () => {
    // GIVEN a server that rejects the first capture (e.g. outside the verifier's jurisdiction)
    const { queue } = newQueue();
    await queue.enqueue(capture("outside"));
    await queue.enqueue(capture("inside"));
    let calls = 0;
    const upload: Uploader = async () =>
      ++calls === 1
        ? { ok: false, failure: { kind: "rejected", status: 403, detail: "outside jurisdiction" } }
        : { ok: true, changeRequestId: "cr-9" };

    // WHEN the queue syncs
    const report = await queue.sync(upload);

    // THEN the rejected capture is failed with its reason and the next one is synced
    expect(report).toEqual({ synced: 1, failed: 1, remaining: 0, stoppedBy: null });
    const [outside, inside] = await queue.list();
    expect(outside?.status).toBe("failed");
    expect(outside?.lastError).toBe("403: outside jurisdiction");
    expect(inside?.status).toBe("synced");
  });

  it("stops without losing captures when the session has expired", async () => {
    const { queue, store } = newQueue();
    await queue.enqueue(capture("needs-login"));
    const report = await queue.sync(async () => ({ ok: false, failure: { kind: "unauthorized" } }));
    expect(report.stoppedBy).toBe("unauthorized");
    expect(await store.pending()).toHaveLength(1);
  });

  it("runs concurrent sync requests only once", async () => {
    // GIVEN a pending capture and two triggers at the same time (connectivity event and button press)
    const { queue } = newQueue();
    const register = new FakeRegister();
    register.online = true;
    await queue.enqueue(capture("once"));

    // WHEN both sync
    const [a, b] = await Promise.all([queue.sync(register.upload), queue.sync(register.upload)]);

    // THEN one upload happened
    expect(a).toBe(b);
    expect(register.requests).toBe(1);
  });

  it("gives every capture its own idempotency key accepted by the API", async () => {
    const { queue } = newQueue();
    const a = await queue.enqueue(capture("a"));
    const b = await queue.enqueue(capture("b"));
    expect(a.idempotencyKey).not.toBe(b.idempotencyKey);
    expect(a.idempotencyKey).toMatch(/^[A-Za-z0-9_-]{8,128}$/);
  });
});
