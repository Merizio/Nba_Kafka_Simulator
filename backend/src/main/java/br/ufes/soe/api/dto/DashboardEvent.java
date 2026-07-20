package br.ufes.soe.api.dto;

public record DashboardEvent(
        String type,
        DashboardSnapshot snapshot
) {}
