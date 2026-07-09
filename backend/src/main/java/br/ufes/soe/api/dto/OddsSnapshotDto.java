package br.ufes.soe.api.dto;

public record OddsSnapshotDto(
        String teamA,
        String teamB,
        double oddsA,
        double oddsB
) {}
