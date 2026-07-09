package br.ufes.soe.api.dto;

public record TeamStandingDto(
        String team,
        int wins,
        int losses,
        int pointsMade,
        int pointsTaken
) {}
