package br.ufes.soe.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Estado acumulado no consumidor entre mensagens (contadores, contexto da partida atual).
 * Atualizado pelas regras de negócio, não pelo parser.
 */
public final class MatchState {
    private String matchTitle = "";
    private final Map<String, Integer> foulsByPlayer = new HashMap<>();
    private int turnoverCount;

    public String getMatchTitle() {
        return matchTitle;
    }

    public void resetForNewMatch(String title) {
        matchTitle = title != null ? title : "";
        foulsByPlayer.clear();
        turnoverCount = 0;
    }

    public int getFoulCount(String player) {
        return foulsByPlayer.getOrDefault(player, 0);
    }

    /** Retorna o total após incrementar. */
    public int incrementFoul(String player) {
        if (player == null || player.isEmpty()) {
            return 0;
        }
        foulsByPlayer.merge(player, 1, Integer::sum);
        return foulsByPlayer.get(player);
    }

    public int incrementTurnoverAndGetTotal() {
        return ++turnoverCount;
    }

    public int getTurnoverCount() {
        return turnoverCount;
    }
}
