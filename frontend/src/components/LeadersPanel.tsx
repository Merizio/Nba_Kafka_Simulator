import type { LeaderEntry } from "../api/types";

interface Props {
  leaders: LeaderEntry[];
}

function formatAverage(gamesPlayed: number, averagePoints: number): string {
  if (gamesPlayed <= 0) {
    return "—";
  }
  return `${averagePoints.toFixed(1)} méd`;
}

export function LeadersPanel({ leaders }: Props) {
  return (
    <section className="panel leaders-panel">
      <div className="panel-header">
        <h2>LÍDERES</h2>
        <span className="leaders-badge">Pontos</span>
      </div>

      {leaders.length === 0 ? (
        <p className="empty-state">Aguardando estatísticas de jogadores…</p>
      ) : (
        <ul className="leaders-list">
          {leaders.map((leader) => (
            <li key={`${leader.rank}-${leader.playerName}`}>
              <span className="leader-rank">{leader.rank}</span>
              <div className="leader-info">
                <strong>{leader.playerName}</strong>
                <span>{leader.teamAbbr}</span>
              </div>
              <div className="leader-value">
                <strong>{leader.totalPoints} pts</strong>
                <span>{formatAverage(leader.gamesPlayed, leader.averagePoints)}</span>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
