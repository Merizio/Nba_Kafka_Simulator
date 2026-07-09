import type {
  DashboardSnapshot,
  HotStreakEntry,
  LeaderEntry,
  RankingEntry,
  SeasonInfo,
} from "./types";

const API_BASE = import.meta.env.VITE_API_BASE ?? "";

async function getJson<T>(path: string): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`);
  if (!res.ok) {
    throw new Error(`Falha na requisição (${res.status})`);
  }
  return res.json() as Promise<T>;
}

export async function fetchDashboard(): Promise<DashboardSnapshot> {
  return getJson("/api/dashboard");
}

export async function fetchSeason(): Promise<SeasonInfo> {
  return getJson("/api/season");
}

export async function fetchRanking(): Promise<RankingEntry[]> {
  return getJson("/api/ranking");
}

export async function fetchLeaders(): Promise<LeaderEntry[]> {
  return getJson("/api/leaders");
}

export async function fetchHotStreak(): Promise<HotStreakEntry[]> {
  return getJson("/api/hot-streak");
}

export function openLiveStream(
  onEvent: (snapshot: DashboardSnapshot, type: string) => void,
  onError?: (err: Event) => void
): EventSource {
  const source = new EventSource(`${API_BASE}/api/live`);

  source.addEventListener("snapshot", (ev) => {
    const payload = JSON.parse((ev as MessageEvent).data);
    onEvent(payload.snapshot, payload.type ?? "snapshot");
  });

  source.addEventListener("update", (ev) => {
    const payload = JSON.parse((ev as MessageEvent).data);
    onEvent(payload.snapshot, payload.type ?? "update");
  });

  source.onerror = (err) => {
    onError?.(err);
  };

  return source;
}
