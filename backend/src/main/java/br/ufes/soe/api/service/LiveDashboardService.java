package br.ufes.soe.api.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.ufes.soe.api.dto.DashboardEvent;
import br.ufes.soe.api.dto.DashboardSnapshot;
import br.ufes.soe.api.dto.HotStreakEntryDto;
import br.ufes.soe.api.dto.LeaderEntryDto;
import br.ufes.soe.api.dto.LiveMatchDto;
import br.ufes.soe.api.dto.MatchupDto;
import br.ufes.soe.api.dto.OddsSnapshotDto;
import br.ufes.soe.api.dto.RankingEntryDto;
import br.ufes.soe.api.dto.SeasonInfoDto;
import br.ufes.soe.api.dto.TeamStandingDto;
import br.ufes.soe.api.support.TeamCatalog;
import br.ufes.soe.model.MatchState;
import br.ufes.soe.model.NbaPrimitiveEvent;
import br.ufes.soe.model.PlayAction;
import br.ufes.soe.parse.NbaMessageParser;
import br.ufes.soe.rules.GameMonitoringRules;

@Service
public class LiveDashboardService {

    private static final Pattern HOT_STREAK_PATTERN = Pattern.compile(
            "O (.+) está pegando fogo! Fez (\\d+) pontos");
    private static final String SEASON_LABEL = "2025/2026";
    private static final int LEADERS_LIMIT = 3;
    private static final int POINTS_PER_WIN = 3;

    private final ObjectMapper mapper;
    private final NbaMessageParser parser;
    private final GameMonitoringRules rules;
    private final MatchState matchState = new MatchState();

    private final Map<String, LiveMatchDto> liveMatches = new LinkedHashMap<>();
    private final Map<String, Integer> playerPoints = new LinkedHashMap<>();
    private final Map<String, Integer> playerGamesPlayed = new LinkedHashMap<>();
    private final Map<String, TeamStandingDto> teamStandings = new LinkedHashMap<>();
    private final Map<String, String> playerTeams = new LinkedHashMap<>();
    private final Map<String, Integer> playerQuarters = new LinkedHashMap<>();
    private final Map<String, MatchupDto> teamMatchups = new LinkedHashMap<>();
    private final Map<String, OddsSnapshotDto> oddsByGame = new LinkedHashMap<>();
    private final Map<String, Set<String>> gamePlayersByKey = new LinkedHashMap<>();
    private final Map<String, HotStreakEntryDto> hotStreakEntries = new LinkedHashMap<>();
    private final List<String> initialRankOrder = new ArrayList<>();
    private final List<String> alerts = new ArrayList<>();

    private volatile int currentRound;
    private volatile boolean seasonEnded;
    private volatile OddsSnapshotDto latestOdds;

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public LiveDashboardService(ObjectMapper mapper) {
        this.mapper = mapper;
        this.parser = new NbaMessageParser(mapper);
        this.rules = new GameMonitoringRules(mapper);
    }

    public synchronized DashboardSnapshot snapshot() {
        return buildSnapshot();
    }

    public synchronized List<RankingEntryDto> ranking() {
        return buildRanking();
    }

    public synchronized List<LeaderEntryDto> leaders(String category) {
        if ("assists".equalsIgnoreCase(category)) {
            return List.of();
        }
        return buildLeadersPoints();
    }

    public synchronized List<HotStreakEntryDto> hotStreaks() {
        return buildHotStreaks();
    }

    public synchronized SeasonInfoDto seasonInfo() {
        return new SeasonInfoDto(SEASON_LABEL, currentRound, seasonEnded);
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(ex -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event()
                    .name("snapshot")
                    .data(new DashboardEvent("snapshot", snapshot())));
        } catch (IOException ignored) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    public synchronized void onNbaGameMessage(String key, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            JsonNode root = parser.parseToTree(raw);
            String tipo = root.path("tipo").asText("");
            String gameKey = key != null && !key.isBlank() ? key : "unknown";
            switch (tipo) {
                case "SEASON_START" -> initializeSeasonTeams(root);
                case "RODADA_INICIO" -> currentRound = root.path("rodada").asInt(currentRound);
                case "SEASON_END" -> seasonEnded = true;
                case "INICIO" -> registerRosterTeams(gameKey, root);
                case "FINAL" -> finalizeGame(gameKey, root);
                default -> parser.toEvent(root).ifPresent(this::applyGameEvent);
            }
            if ("INICIO".equals(tipo) || "EVENTO".equals(tipo)) {
                updateLiveMatchFromKey(gameKey, root, tipo);
                trackPlayerContext(root);
                syncMatchupsForGame(gameKey);
            }
        } catch (Exception ex) {
            // mantém API resiliente a mensagens malformadas
        }
        broadcast("nba_game");
    }

    public void onOddsMessage(String key, String raw) {
        try {
            JsonNode node = mapper.readTree(raw);
            OddsSnapshotDto odds = new OddsSnapshotDto(
                    node.path("teamA").asText(""),
                    node.path("teamB").asText(""),
                    node.path("oddsA").asDouble(0),
                    node.path("oddsB").asDouble(0));
            latestOdds = odds;
            String gameKey = key != null && !key.isBlank() ? key : "unknown";
            oddsByGame.put(gameKey, odds);
            for (String liveKey : liveMatches.keySet()) {
                syncMatchupsForGame(liveKey);
            }
        } catch (JsonProcessingException ignored) {
            return;
        }
        broadcast("odds_game");
    }

    public void onPlayerStats(String player, int points) {
        // Pontuação da temporada é acumulada em applyGameEvent a partir de nba_game.
    }

    public void onTeamStats(String team, String raw) {
        if (!teamStandings.containsKey(team)) {
            return;
        }
        try {
            JsonNode node = mapper.readTree(raw);
            teamStandings.put(team, new TeamStandingDto(
                    team,
                    node.path("win").asInt(0),
                    node.path("lose").asInt(0),
                    node.path("point_made").asInt(0),
                    node.path("point_take").asInt(0)));
        } catch (JsonProcessingException ignored) {
            return;
        }
        broadcast("stats_time");
    }

    public void onHotStreak(String player, String message) {
        alerts.add(0, message);
        if (alerts.size() > 50) {
            alerts.subList(50, alerts.size()).clear();
        }
        hotStreakEntries.put(player, parseHotStreak(player, message));
        broadcast("hotstreak_player");
    }

    public void onSimultaneousStreak(long count) {
        alerts.add(0, count + " jogadores em hot streak simultânea");
        if (alerts.size() > 50) {
            alerts.subList(50, alerts.size()).clear();
        }
        broadcast("simultaneous_streaks");
    }

    private DashboardSnapshot buildSnapshot() {
        return new DashboardSnapshot(
                seasonInfo(),
                buildRanking(),
                buildLeadersPoints(),
                List.of(),
                buildHotStreaks(),
                List.copyOf(liveMatches.values()),
                Map.copyOf(playerPoints),
                Map.copyOf(teamStandings),
                latestOdds,
                List.copyOf(alerts));
    }

    private List<RankingEntryDto> buildRanking() {
        List<String> sortedTeams = teamStandings.keySet().stream()
                .sorted(Comparator
                        .comparingInt((String team) -> standingPoints(teamStandings.get(team)))
                        .reversed()
                        .thenComparing((String team) -> teamStandings.get(team).wins(), Comparator.reverseOrder())
                        .thenComparingInt(this::initialOrderIndex))
                .toList();

        List<RankingEntryDto> ranking = new ArrayList<>();
        for (int i = 0; i < sortedTeams.size(); i++) {
            String teamAbbr = sortedTeams.get(i);
            TeamStandingDto team = teamStandings.get(teamAbbr);
            ranking.add(new RankingEntryDto(
                    i + 1,
                    teamAbbr,
                    TeamCatalog.fullName(teamAbbr),
                    standingPoints(team),
                    team.wins(),
                    team.losses(),
                    teamMatchups.get(teamAbbr)));
        }
        return ranking;
    }

    private List<LeaderEntryDto> buildLeadersPoints() {
        List<String> sortedPlayers = playerPoints.keySet().stream()
                .filter(player -> playerPoints.getOrDefault(player, 0) > 0)
                .sorted(Comparator
                        .comparingInt((String player) -> playerPoints.getOrDefault(player, 0)).reversed()
                        .thenComparingDouble(this::averagePointsFor).reversed()
                        .thenComparing(String::compareTo))
                .limit(LEADERS_LIMIT)
                .toList();

        List<LeaderEntryDto> leaders = new ArrayList<>();
        for (int i = 0; i < sortedPlayers.size(); i++) {
            String player = sortedPlayers.get(i);
            int total = playerPoints.getOrDefault(player, 0);
            int games = playerGamesPlayed.getOrDefault(player, 0);

            // Cria um Jogador e adiciona na lista leaders
            leaders.add(new LeaderEntryDto(
                    i+1,
                    player,
                    playerTeams.getOrDefault(player, "—"),
                    total,
                    games,
                    roundAverage(total, games)));
        }
        return leaders;
    }

    private List<HotStreakEntryDto> buildHotStreaks() {
        return hotStreakEntries.values().stream()
                .sorted(Comparator.comparingInt(HotStreakEntryDto::streakPoints).reversed()
                        .thenComparing(HotStreakEntryDto::playerName))
                .limit(5)
                .toList();
    }

    private int standingPoints(TeamStandingDto team) {
        return team.wins() * POINTS_PER_WIN;
    }

    private double averagePointsFor(String player) {
        return roundAverage(
                playerPoints.getOrDefault(player, 0),
                playerGamesPlayed.getOrDefault(player, 0));
    }

    private double roundAverage(int totalPoints, int gamesPlayed) {
        if (gamesPlayed <= 0) {
            return 0.0;
        }
        return Math.round((totalPoints * 10.0) / gamesPlayed) / 10.0;
    }

    private int initialOrderIndex(String team) {
        int index = initialRankOrder.indexOf(team);
        return index >= 0 ? index : Integer.MAX_VALUE;
    }

    private void applyGameEvent(NbaPrimitiveEvent event) {
        try {
            rules.apply(event, matchState);
        } catch (Exception ignored) {
            // estado ao vivo opcional; não bloqueia API
        }
        if (event instanceof NbaPrimitiveEvent.MatchPlayEvent play
                && play.action() instanceof PlayAction.Point point) {
            String player = point.player().getName();
            if (!player.isBlank()) {
                playerPoints.merge(player, point.pointsValue(), Integer::sum);
                playerQuarters.put(player, play.quarter());
                if (play.teamName() != null && !play.teamName().isBlank()) {
                    playerTeams.put(player, play.teamName());
                }
            }
        }
    }

    private void initializeSeasonTeams(JsonNode root) {
        List<String> teams = new ArrayList<>();
        for (JsonNode teamNode : root.path("times")) {
            String team = teamNode.asText("").trim();
            if (!team.isBlank()) {
                teams.add(team);
            }
        }
        if (teams.isEmpty()) {
            return;
        }

        Collections.shuffle(teams);
        initialRankOrder.clear();
        initialRankOrder.addAll(teams);

        teamStandings.clear();
        teamMatchups.clear();
        liveMatches.clear();
        oddsByGame.clear();
        gamePlayersByKey.clear();
        playerPoints.clear();
        playerGamesPlayed.clear();
        playerTeams.clear();
        playerQuarters.clear();
        hotStreakEntries.clear();
        alerts.clear();

        seasonEnded = false;
        currentRound = 0;
        latestOdds = null;

        for (String team : teams) {
            teamStandings.put(team, new TeamStandingDto(team, 0, 0, 0, 0));
        }
    }

    private void registerRosterTeams(String gameKey, JsonNode root) {
        Set<String> players = new HashSet<>();
        Iterator<String> fieldNames = root.fieldNames();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            if ("tipo".equals(field) || "match".equals(field)) {
                continue;
            }
            JsonNode roster = root.get(field);
            if (roster == null || !roster.isObject()) {
                continue;
            }
            collectPlayers(field, roster.path("titulares"), players);
            collectPlayers(field, roster.path("reservas"), players);
        }
        gamePlayersByKey.put(gameKey, players);
    }

    private void collectPlayers(String teamAbbr, JsonNode playersNode, Set<String> players) {
        if (!playersNode.isArray()) {
            return;
        }
        for (JsonNode player : playersNode) {
            String name = player.asText("");
            if (!name.isBlank()) {
                playerTeams.put(name, teamAbbr);
                players.add(name);
            }
        }
    }

    private void trackPlayerContext(JsonNode root) {
        if (!"EVENTO".equals(root.path("tipo").asText(""))) {
            return;
        }
        String team = root.path("time").asText("");
        int quarter = root.path("quarto").asInt(0);
        JsonNode detalhes = root.path("detalhes");
        if (detalhes.has("jogador")) {
            String player = detalhes.path("jogador").asText("");
            if (!player.isBlank()) {
                if (!team.isBlank()) {
                    playerTeams.put(player, team);
                }
                if (quarter > 0) {
                    playerQuarters.put(player, quarter);
                }
            }
        }
    }

    private void finalizeGame(String gameKey, JsonNode root) {
        String matchLine = root.path("match").asText("");
        ParsedScore parsed = ParsedScore.tryParse(matchLine, false);
        if (!parsed.home().isBlank() && !parsed.away().isBlank()) {
            OddsSnapshotDto odds = oddsByGame.get(gameKey);
            Double homeOdds = resolveOdds(parsed.home(), odds);
            Double awayOdds = resolveOdds(parsed.away(), odds);
            teamMatchups.put(
                    parsed.home(),
                    new MatchupDto(
                            parsed.home(),
                            parsed.homeScore(),
                            homeOdds,
                            parsed.away(),
                            parsed.awayScore(),
                            awayOdds,
                            false));
            teamMatchups.put(
                    parsed.away(),
                    new MatchupDto(
                            parsed.away(),
                            parsed.awayScore(),
                            awayOdds,
                            parsed.home(),
                            parsed.homeScore(),
                            homeOdds,
                            false));
        }
        Set<String> players = gamePlayersByKey.remove(gameKey);
        if (players != null) {
            for (String player : players) {
                playerGamesPlayed.merge(player, 1, Integer::sum);
            }
        }
        liveMatches.remove(gameKey);
        oddsByGame.remove(gameKey);
    }

    private void syncMatchupsForGame(String gameKey) {
        LiveMatchDto match = liveMatches.get(gameKey);
        if (match == null || match.homeTeam().isBlank() || match.awayTeam().isBlank()) {
            return;
        }
        OddsSnapshotDto odds = oddsByGame.get(gameKey);
        Double homeOdds = resolveOdds(match.homeTeam(), odds);
        Double awayOdds = resolveOdds(match.awayTeam(), odds);

        teamMatchups.put(
                match.homeTeam(),
                new MatchupDto(
                        match.homeTeam(),
                        match.homeScore(),
                        homeOdds,
                        match.awayTeam(),
                        match.awayScore(),
                        awayOdds,
                        true));
        teamMatchups.put(
                match.awayTeam(),
                new MatchupDto(
                        match.awayTeam(),
                        match.awayScore(),
                        awayOdds,
                        match.homeTeam(),
                        match.homeScore(),
                        homeOdds,
                        true));
    }

    private Double resolveOdds(String team, OddsSnapshotDto odds) {
        if (odds == null || team == null || team.isBlank()) {
            return null;
        }
        if (team.equals(odds.teamA())) {
            return odds.oddsA();
        }
        if (team.equals(odds.teamB())) {
            return odds.oddsB();
        }
        return null;
    }

    private HotStreakEntryDto parseHotStreak(String player, String message) {
        int streakPoints = 0;
        Matcher matcher = HOT_STREAK_PATTERN.matcher(message);
        if (matcher.find()) {
            player = matcher.group(1).trim();
            streakPoints = Integer.parseInt(matcher.group(2));
        }
        String team = playerTeams.getOrDefault(player, "—");
        int quarter = resolveQuarter(player, team);
        return new HotStreakEntryDto(player, team, quarter, streakPoints);
    }

    private int resolveQuarter(String player, String team) {
        Integer stored = playerQuarters.get(player);
        if (stored != null && stored > 0) {
            return stored;
        }
        for (LiveMatchDto match : liveMatches.values()) {
            if (team.equals(match.homeTeam()) || team.equals(match.awayTeam())) {
                return match.quarter();
            }
        }
        return 0;
    }

    private void updateLiveMatchFromKey(String gameKey, JsonNode root, String tipo) {
        if ("FINAL".equals(tipo)) {
            return;
        }
        LiveMatchDto current = liveMatches.getOrDefault(
                gameKey,
                new LiveMatchDto(gameKey, "", "", 0, 0, 0));

        String scoreboard = root.path("placar").asText("");
        if (scoreboard.isBlank() && "INICIO".equals(tipo)) {
            scoreboard = root.path("match").asText("");
        }
        ParsedScore parsed = ParsedScore.tryParse(scoreboard, "INICIO".equals(tipo));
        int quarter = root.path("quarto").asInt(current.quarter());

        liveMatches.put(gameKey, new LiveMatchDto(
                gameKey,
                parsed.home(),
                parsed.away(),
                parsed.homeScore(),
                parsed.awayScore(),
                quarter));
    }

    private void broadcast(String source) {
        DashboardEvent event = new DashboardEvent(source, snapshot());
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("update").data(event));
            } catch (IOException ex) {
                emitters.remove(emitter);
            }
        }
    }

    private record ParsedScore(String home, int homeScore, int awayScore, String away) {
        static ParsedScore tryParse(String placar, boolean allowNamesOnly) {
            if (placar == null || placar.isBlank()) {
                return new ParsedScore("", 0, 0, "");
            }
            String[] parts = placar.split(" X ");
            if (parts.length != 2) {
                return new ParsedScore("", 0, 0, "");
            }
            String[] home = parts[0].trim().split("\\s+");
            String[] away = parts[1].trim().split("\\s+");
            if (home.length < 1 || away.length < 1) {
                return new ParsedScore("", 0, 0, "");
            }
            if (allowNamesOnly && home.length == 1 && away.length == 1) {
                return new ParsedScore(home[0], 0, 0, away[0]);
            }
            if (home.length < 2 || away.length < 2) {
                return new ParsedScore("", 0, 0, "");
            }
            try {
                return new ParsedScore(
                        home[0],
                        Integer.parseInt(home[1]),
                        Integer.parseInt(away[0]),
                        away[1]);
            } catch (NumberFormatException ex) {
                return new ParsedScore("", 0, 0, "");
            }
        }
    }
}
