package br.ufes.soe.api.dto;

public record LiveMatchDto(
        String gameKey,
        String homeTeam,
        String awayTeam,
        int homeScore,
        int awayScore,
        int quarter
) {}
