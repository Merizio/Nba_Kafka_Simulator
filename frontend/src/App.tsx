import { HotStreakPanel } from "./components/HotStreakPanel";
import { LeadersPanel } from "./components/LeadersPanel";
import { RankingTable } from "./components/RankingTable";
import { useLiveDashboard } from "./hooks/useLiveDashboard";
import "./App.css";

export default function App() {
  const { data, connected, error } = useLiveDashboard();
  const seasonLabel = data?.season.label ?? "2025/2026";

  return (
    <div className="app">
      <header className="app-header">
        <p className="eyebrow">TEMPORADA NBA</p>
        <h1>{seasonLabel}</h1>
        <p className="connection-status">
          {connected ? "Ao vivo" : "Reconectando…"}
          {data?.season.currentRound
            ? ` · Rodada ${data.season.currentRound}`
            : ""}
          {data?.season.seasonEnded ? " · Temporada encerrada" : ""}
        </p>
        {error && <p className="error-banner">{error}</p>}
      </header>

      <main className="dashboard-grid">
        <section className="ranking-section">
          <h2>RANKING</h2>
          <RankingTable entries={data?.ranking ?? []} />
        </section>

        <aside className="sidebar">
          <LeadersPanel leaders={data?.leadersPoints ?? []} />
          <HotStreakPanel entries={data?.hotStreaks ?? []} />
        </aside>
      </main>
    </div>
  );
}
