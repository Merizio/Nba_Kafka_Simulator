package br.ufes.soe.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Estado acumulado no consumidor entre mensagens (contadores, contexto da partida atual).
 * Atualizado pelas regras de negócio, não pelo parser.
 */
public final class MatchState {
    private String matchTitle = "";
    private List<Team> teams = new ArrayList<>();
    private final Map<String, Integer> foulsByPlayer = new HashMap<>();
    private int turnoverCount;

    public String getMatchTitle() {
        return matchTitle;
    }

    public List<Team> getTeams() {
        return teams;
    }

    public void resetForNewMatch(String title, List<Team> matchTeams) {
        matchTitle = title != null ? title : "";
        teams = matchTeams != null ? new ArrayList<>(matchTeams) : new ArrayList<>();
        foulsByPlayer.clear();
        turnoverCount = 0;
    }

    public int incrementFoul(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return 0;
        }
        foulsByPlayer.merge(playerName, 1, Integer::sum);
        return foulsByPlayer.get(playerName);
    }

    public int getFoulCount(String playerName) {
        return foulsByPlayer.getOrDefault(playerName, 0);
    }

    public int incrementTurnoverAndGetTotal() {
        return ++turnoverCount;
    }

    public int getTurnoverCount() {
        return turnoverCount;
    }
}
