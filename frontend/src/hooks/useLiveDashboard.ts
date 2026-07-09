import { useEffect, useState } from "react";
import { fetchDashboard, openLiveStream } from "../api/client";
import type { DashboardSnapshot } from "../api/types";

export function useLiveDashboard() {
  const [data, setData] = useState<DashboardSnapshot | null>(null);
  const [connected, setConnected] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let source: EventSource | null = null;
    let cancelled = false;

    fetchDashboard()
      .then((snapshot) => {
        if (!cancelled) {
          setData(snapshot);
        }
      })
      .catch((err: Error) => {
        if (!cancelled) {
          setError(err.message);
        }
      });

    source = openLiveStream(
      (snapshot) => {
        setData(snapshot);
        setConnected(true);
        setError(null);
      },
      () => {
        setConnected(false);
      }
    );

    source.onopen = () => setConnected(true);

    return () => {
      cancelled = true;
      source?.close();
    };
  }, []);

  return { data, connected, error };
}
