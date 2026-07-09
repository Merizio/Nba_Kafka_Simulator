package br.ufes.soe.api.dto;

import java.util.List;
import java.util.Map;

public record DashboardSnapshot(
        SeasonInfoDto season,
        List<RankingEntryDto> ranking,
        List<LeaderEntryDto> leadersPoints,
        List<LeaderEntryDto> leadersAssists,
        List<HotStreakEntryDto> hotStreaks,
        List<LiveMatchDto> liveMatches,
        Map<String, Integer> playerPoints,
        Map<String, TeamStandingDto> teamStandings,
        OddsSnapshotDto latestOdds,
        List<String> alerts
) {
    public static DashboardSnapshot empty() {
        return new DashboardSnapshot(
                new SeasonInfoDto("2025/2026", 0, false),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Map.of(),
                null,
                List.of());
    }
}
