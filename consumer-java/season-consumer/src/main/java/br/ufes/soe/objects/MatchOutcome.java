package br.ufes.soe.objects;

public record MatchOutcome(String teamName, int score, int op_score,  boolean isWin) {}