export interface SeasonInfo {
  label: string;
  currentRound: number;
  seasonEnded: boolean;
}

export interface Matchup {
  teamAbbr: string;
  teamScore: number;
  teamOdds: number | null;
  opponentAbbr: string;
  opponentScore: number;
  opponentOdds: number | null;
  live: boolean;
}

export interface RankingEntry {
  rank: number;
  teamAbbr: string;
  teamName: string;
  points: number;
  wins: number;
  losses: number;
  confronto: Matchup | null;
}

export interface LeaderEntry {
  rank: number;
  playerName: string;
  teamAbbr: string;
  totalPoints: number;
  gamesPlayed: number;
  averagePoints: number;
}

export interface HotStreakEntry {
  playerName: string;
  teamAbbr: string;
  quarter: number;
  streakPoints: number;
}

export interface LiveMatch {
  gameKey: string;
  homeTeam: string;
  awayTeam: string;
  homeScore: number;
  awayScore: number;
  quarter: number;
}

export interface TeamStanding {
  team: string;
  wins: number;
  losses: number;
  pointsMade: number;
  pointsTaken: number;
}

export interface OddsSnapshot {
  teamA: string;
  teamB: string;
  oddsA: number;
  oddsB: number;
}

export interface DashboardSnapshot {
  season: SeasonInfo;
  ranking: RankingEntry[];
  leadersPoints: LeaderEntry[];
  leadersAssists: LeaderEntry[];
  hotStreaks: HotStreakEntry[];
  liveMatches: LiveMatch[];
  playerPoints: Record<string, number>;
  teamStandings: Record<string, TeamStanding>;
  latestOdds: OddsSnapshot | null;
  alerts: string[];
}

export interface DashboardEvent {
  type: string;
  snapshot: DashboardSnapshot;
}
