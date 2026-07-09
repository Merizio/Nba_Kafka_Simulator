package br.ufes.soe.objects;

public record MatchOutcome(
    String teamName, 
    String playerName, // Adicionado para rastrear o autor da jogada
    int points,        // Adicionado: pontos desta jogada específica
    int score,         // Score total do time (se necessário)
    int op_score,      // Score do oponente (se necessário)
    boolean isWin,
    String type        // Adicionado para identificar "FIM DE JOGO" ou "JOGADA"
) {}