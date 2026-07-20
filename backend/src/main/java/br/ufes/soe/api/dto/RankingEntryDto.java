package br.ufes.soe.api.dto;

public record RankingEntryDto(
        int rank,
        String teamAbbr,
        String teamName,
        int points,
        int wins,
        int losses,
        MatchupDto confronto
) {}
