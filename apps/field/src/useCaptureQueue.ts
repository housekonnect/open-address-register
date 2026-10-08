import * as Crypto from "expo-crypto";
import { File, Paths } from "expo-file-system";
import * as Network from "expo-network";
import { openDatabaseAsync } from "expo-sqlite";
import { useCallback, useEffect, useRef, useState } from "react";
import { config } from "./config";
import { CaptureQueue, type SyncReport } from "./queue/capture-queue";
import { SqliteQueueStore } from "./queue/sqlite-store";
import type { NewCapture, QueuedCapture } from "./queue/types";
import { createUploader } from "./sync/uploader";

/** Wires the durable queue to SQLite, the uploader to the API, and syncing to connectivity changes. */
export function useCaptureQueue(getAccessToken: () => Promise<string | undefined>, enabled: boolean) {
  const queue = useRef<CaptureQueue | null>(null);
  const [captures, setCaptures] = useState<QueuedCapture[]>([]);
  const [online, setOnline] = useState(true);
  const [syncing, setSyncing] = useState(false);
  const [lastReport, setLastReport] = useState<SyncReport>();

  const refresh = useCallback(async () => {
    if (queue.current) setCaptures(await queue.current.list());
  }, []);

  const sync = useCallback(async () => {
    if (!queue.current || !enabled) return;
    setSyncing(true);
    try {
      const upload = createUploader({
        apiUrl: config.apiUrl,
        getAccessToken,
        writeMetadataFile: async (capture, json) => {
          const file = new File(Paths.cache, `metadata-${capture.id}.json`);
          file.write(json);
          return file.uri;
        },
      });
      setLastReport(await queue.current.sync(upload));
    } finally {
      setSyncing(false);
      await refresh();
    }
  }, [enabled, getAccessToken, refresh]);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const store = await SqliteQueueStore.open(await openDatabaseAsync("captures.db"));
      if (cancelled) return;
      queue.current = new CaptureQueue(store, () => Crypto.randomUUID());
      await refresh();
    })();
    return () => {
      cancelled = true;
    };
  }, [refresh]);

  useEffect(() => {
    const subscription = Network.addNetworkStateListener((state) => {
      const reachable = state.isInternetReachable !== false && state.isConnected === true;
      setOnline(reachable);
      if (reachable) void sync();
    });
    return () => subscription.remove();
  }, [sync]);

  const enqueue = useCallback(
    async (capture: NewCapture) => {
      if (!queue.current) return;
      await queue.current.enqueue(capture);
      await refresh();
      if (online) void sync();
    },
    [online, refresh, sync],
  );

  return { captures, online, syncing, lastReport, enqueue, sync };
}
