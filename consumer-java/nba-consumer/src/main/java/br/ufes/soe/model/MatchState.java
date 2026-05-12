package br.ufes.soe.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Estado da partida: placar, elenco, estatísticas e parsing do texto {@code placar} do produtor.
 */
public final class MatchState {
    private static final String PT_SEP = "\u0001";

    private String matchTitle = "";
    private List<Team> teams = new ArrayList<>();
    private String teamHomeName = "";
    private String teamAwayName = "";
    private int scoreHome;
    private int scoreAway;
    private int currentQuarter = -1;
    private boolean matchEnded;

    private final Map<String, Integer> pointsByTeamPlayer = new HashMap<>();
    private final Map<String, Integer> foulsByTeamPlayer = new HashMap<>();

    private int turnoverCount;

    public boolean isMatchEnded() {
        return matchEnded;
    }

    public void setMatchEnded(boolean matchEnded) {
        this.matchEnded = matchEnded;
    }

    public String getMatchTitle() {
        return matchTitle;
    }

    public List<Team> getTeams() {
        return teams;
    }

    public String getTeamHomeName() {
        return teamHomeName;
    }

    public String getTeamAwayName() {
        return teamAwayName;
    }

    public int getScoreHome() {
        return scoreHome;
    }

    public int getScoreAway() {
        return scoreAway;
    }

    public int getCurrentQuarter() {
        return currentQuarter;
    }

    public void resetForNewMatch(String title, List<Team> matchTeams) {
        matchTitle = title != null ? title : "";
        teams = matchTeams != null ? new ArrayList<>(matchTeams) : new ArrayList<>();
        teamHomeName = "";
        teamAwayName = "";
        scoreHome = 0;
        scoreAway = 0;
        currentQuarter = -1;
        matchEnded = false;
        pointsByTeamPlayer.clear();
        foulsByTeamPlayer.clear();
        turnoverCount = 0;

        parseTeamsOrderFromMatchTitle();
        if (teams.size() >= 2 && (teamHomeName.isEmpty() || teamAwayName.isEmpty())) {
            teamHomeName = teams.get(0).getName();
            teamAwayName = teams.get(1).getName();
        }
    }

    /** Espera formato do produtor: {@code TimeA pontosA X pontosB TimeB}. */
    public void updateFromPlacar(String placar) {
        ParsedBi parsed = ParsedBi.tryParse(placar);
        if (parsed == null) {
            return;
        }
        teamHomeName = parsed.teamHome();
        teamAwayName = parsed.teamAway();
        scoreHome = parsed.scoreHome();
        scoreAway = parsed.scoreAway();
    }

    public void setCurrentQuarter(int q) {
        if (q > 0) {
            currentQuarter = q;
        }
    }

    public void recordPoint(String scoringTeamName, String playerName, int deltaPts) {
        if (playerName == null || playerName.isEmpty() || scoringTeamName == null) {
            return;
        }
        String key = keyTp(scoringTeamName, playerName);
        pointsByTeamPlayer.merge(key, deltaPts, Integer::sum);
        Player roster = findPlayerOnRoster(scoringTeamName, playerName);
        if (roster != null) {
            roster.IncrementPoints(deltaPts);
        }
    }

    public void recordFoulCommitted(String committingTeamName, String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return;
        }
        String team = committingTeamName != null ? committingTeamName : "";
        foulsByTeamPlayer.merge(keyTp(team, playerName), 1, Integer::sum);
        Player roster = findPlayerOnRoster(team, playerName);
        if (roster != null) {
            roster.IncrementFouls();
        }
    }

    public void applySubstitution(String teamName, String playerOutName, String playerInName) {
        Team t = findTeam(teamName);
        if (t == null) {
            return;
        }
        Player out = findPlayerInLists(t, playerOutName);
        Player in = findPlayerInLists(t, playerInName);
        if (out == null || in == null || out == in) {
            return;
        }
        List<Player> starters = t.getStarters();
        List<Player> reserves = t.getReserves();
        int outS = starters.indexOf(out);
        int outR = reserves.indexOf(out);
        int inS = starters.indexOf(in);
        int inR = reserves.indexOf(in);

        if (outS >= 0 && inR >= 0) {
            starters.set(outS, in);
            reserves.set(inR, out);
            return;
        }
        if (outR >= 0 && inS >= 0) {
            reserves.set(outR, in);
            starters.set(inS, out);
        }
    }

    public int incrementTurnoverAndGetTotal() {
        return ++turnoverCount;
    }

    public int getTurnoverCount() {
        return turnoverCount;
    }

    public Map<String, Integer> foulTotalsSnapshot() {
        return new HashMap<>(foulsByTeamPlayer);
    }

    public Map<String, Integer> pointsTotalsSnapshot() {
        return new HashMap<>(pointsByTeamPlayer);
    }

    /** Melhores {@code limit} jogadores do time por pontos ({@code nome -> pts}). */
    public List<Map.Entry<String, Integer>> topScorersForTeam(String teamName, int limit) {
        if (teamName == null || teamName.isEmpty()) {
            return List.of();
        }
        String prefix = teamName + PT_SEP;
        return pointsByTeamPlayer.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .map(e -> Map.entry(playerFromKey(e.getKey()), e.getValue()))
                .collect(Collectors.toList());
    }

    private static String keyTp(String team, String player) {
        return Objects.requireNonNullElse(team, "") + PT_SEP + Objects.requireNonNullElse(player, "");
    }

    private static String playerFromKey(String key) {
        int i = key.indexOf(PT_SEP);
        return i >= 0 ? key.substring(i + 1) : key;
    }

    private void parseTeamsOrderFromMatchTitle() {
        String m = matchTitle;
        if (m == null || m.isEmpty()) {
            return;
        }
        String sep = " X ";
        int ix = m.indexOf(sep);
        if (ix <= 0) {
            return;
        }
        teamHomeName = m.substring(0, ix).trim();
        teamAwayName = m.substring(ix + sep.length()).trim();
    }

    private Team findTeam(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        for (Team t : teams) {
            if (name.equals(t.getName())) {
                return t;
            }
        }
        return null;
    }

    private Player findPlayerOnRoster(String teamName, String playerName) {
        Team t = findTeam(teamName);
        return t != null ? findPlayerInLists(t, playerName) : null;
    }

    private static Player findPlayerInLists(Team t, String playerName) {
        if (playerName == null) {
            return null;
        }
        for (Player p : t.getStarters()) {
            if (playerName.equals(p.getName())) {
                return p;
            }
        }
        for (Player p : t.getReserves()) {
            if (playerName.equals(p.getName())) {
                return p;
            }
        }
        return null;
    }

    /** Parsing robusto do placar texto do Python: {@code TimeEsquerda ptsEsquerda X ptsDireita TimeDireita}. */
    record ParsedBi(String teamHome, int scoreHome, String teamAway, int scoreAway) {
        static ParsedBi tryParse(String placar) {
            if (placar == null || placar.isBlank()) {
                return null;
            }
            String s = placar.trim();
            String marker = " X ";
            int mid = s.indexOf(marker);
            if (mid < 0) {
                return null;
            }
            String left = s.substring(0, mid).trim();
            String right = s.substring(mid + marker.length()).trim();
            Optional<int[]> ls = scoreTrailing(left);
            Optional<int[]> rs = scoreLeading(right);
            if (ls.isEmpty() || rs.isEmpty()) {
                return null;
            }
            int[] l = ls.get();
            int[] r = rs.get();
            String teamHomeNameLocal = left.substring(0, l[0]).trim();
            int scoreHomeLocal = l[1];
            int scoreAwayLocal = r[1];
            String teamAwayNameLocal = right.substring(r[0]).trim();
            return new ParsedBi(teamHomeNameLocal, scoreHomeLocal, teamAwayNameLocal, scoreAwayLocal);
        }

        /** {@code left} como "{nome} {pts}" — último token inteiro é pontuação. */
        private static Optional<int[]> scoreTrailing(String left) {
            int sp = left.lastIndexOf(' ');
            if (sp <= 0 || sp >= left.length() - 1) {
                return Optional.empty();
            }
            String num = left.substring(sp + 1).trim();
            try {
                return Optional.of(new int[]{sp, Integer.parseInt(num)});
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }

        /** {@code right} como "{pts} {nome}" — primeiro token inteiro é pontuação. */
        private static Optional<int[]> scoreLeading(String right) {
            int sp = right.indexOf(' ');
            if (sp <= 0 || sp >= right.length() - 1) {
                return Optional.empty();
            }
            String num = right.substring(0, sp).trim();
            try {
                int pts = Integer.parseInt(num);
                return Optional.of(new int[]{sp, pts});
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
    }
}
