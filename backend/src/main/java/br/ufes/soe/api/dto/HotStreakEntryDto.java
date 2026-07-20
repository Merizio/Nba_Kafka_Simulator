package br.ufes.soe.api.dto;

public record HotStreakEntryDto(
        String playerName,
        String teamAbbr,
        int quarter,
        int streakPoints
) {}
