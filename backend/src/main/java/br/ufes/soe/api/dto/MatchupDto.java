package br.ufes.soe.api.dto;

public record MatchupDto(
        String teamAbbr,
        int teamScore,
        Double teamOdds,
        String opponentAbbr,
        int opponentScore,
        Double opponentOdds,
        boolean live
) {}
