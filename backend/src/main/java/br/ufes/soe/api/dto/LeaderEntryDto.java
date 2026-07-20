package br.ufes.soe.api.dto;

public record LeaderEntryDto(
        int rank,
        String playerName,
        String teamAbbr,
        int totalPoints,
        int gamesPlayed,
        double averagePoints
) {}
