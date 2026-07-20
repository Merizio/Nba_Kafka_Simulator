import type { Matchup, RankingEntry } from "../api/types";

interface Props {
  entries: RankingEntry[];
}

function formatOdds(value: number | null): string {
  if (value == null || Number.isNaN(value)) {
    return "";
  }
  return value.toFixed(2);
}

function MatchupCell({ confronto }: { confronto: Matchup }) {
  const teamOdds = formatOdds(confronto.teamOdds);
  const opponentOdds = formatOdds(confronto.opponentOdds);

  return (
    <span className={`matchup-line${confronto.live ? " live" : ""}`}>
      <strong>{confronto.teamAbbr}</strong>
      {teamOdds && <span className="odd-tag">{teamOdds}</span>}
      <span className="matchup-score">
        {confronto.teamScore} x {confronto.opponentScore}
      </span>
      {opponentOdds && <span className="odd-tag">{opponentOdds}</span>}
      <strong>{confronto.opponentAbbr}</strong>
    </span>
  );
}

export function RankingTable({ entries }: Props) {
  if (entries.length === 0) {
    return (
      <p className="empty-state">Aguardando início da temporada pelo simulador…</p>
    );
  }

  return (
    <div className="ranking-table-wrap">
      <table className="ranking-table">
        <thead>
          <tr>
            <th>PTS</th>
            <th>TIME</th>
            <th>V</th>
            <th>D</th>
            <th>CONFRONTO</th>
          </tr>
        </thead>
        <tbody>
          {entries.map((entry) => (
            <tr key={entry.teamAbbr}>
              <td className="pts-cell">{entry.points}</td>
              <td className="team-cell">
                <div className="team-cell-inner">
                  <span className="rank-num">{entry.rank}</span>
                  <div className="team-info">
                    <strong>{entry.teamAbbr}</strong>
                    <span>{entry.teamName}</span>
                  </div>
                </div>
              </td>
              <td className="win-cell">{entry.wins}</td>
              <td className="loss-cell">{entry.losses}</td>
              <td className="matchup-cell">
                {entry.confronto ? (
                  <MatchupCell confronto={entry.confronto} />
                ) : (
                  "—"
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
