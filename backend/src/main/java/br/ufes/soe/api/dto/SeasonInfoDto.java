package br.ufes.soe.api.dto;

public record SeasonInfoDto(
        String label,
        int currentRound,
        boolean seasonEnded
) {}
