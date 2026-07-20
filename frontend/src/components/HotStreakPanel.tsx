import type { HotStreakEntry } from "../api/types";

interface Props {
  entries: HotStreakEntry[];
}

function quarterLabel(quarter: number): string {
  if (quarter <= 0) {
    return "Ao vivo";
  }
  return `${quarter}º Q`;
}

export function HotStreakPanel({ entries }: Props) {
  return (
    <section className="panel hot-streak-panel">
      <h2>HOT STREAK</h2>

      {entries.length === 0 ? (
        <p className="empty-state">Nenhum jogador em sequência quente no momento.</p>
      ) : (
        <ul className="hot-streak-list">
          {entries.map((entry) => (
            <li key={entry.playerName} className="hot-streak-card">
              <div className="hot-streak-main">
                <span className="fire-icon" aria-hidden="true">
                  🔥
                </span>
                <div>
                  <strong>{entry.playerName}</strong>
                  <span className="hot-meta">
                    {entry.teamAbbr} • {quarterLabel(entry.quarter)}
                  </span>
                </div>
              </div>
              <div className="hot-streak-stats">
                <strong>{entry.streakPoints} pts</strong>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
