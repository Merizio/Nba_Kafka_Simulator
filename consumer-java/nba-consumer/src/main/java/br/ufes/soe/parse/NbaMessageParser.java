package br.ufes.soe.parse;

import br.ufes.soe.model.NbaPrimitiveEvent;
import br.ufes.soe.model.NbaPrimitiveEvent.MatchEndEvent;
import br.ufes.soe.model.NbaPrimitiveEvent.MatchPlayEvent;
import br.ufes.soe.model.NbaPrimitiveEvent.MatchStartEvent;
import br.ufes.soe.model.PlayAction;
import br.ufes.soe.model.Player;
import br.ufes.soe.model.Team;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public final class NbaMessageParser {

    private final ObjectMapper mapper;

    public NbaMessageParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public JsonNode parseToTree(String raw) throws JsonProcessingException {
        return mapper.readTree(raw);
    }

    public Optional<NbaPrimitiveEvent> toEvent(JsonNode root) {
        if (root == null || root.isNull()) {
            return Optional.empty();
        }
        String tipo = root.path("tipo").asText("");
        return switch (tipo) {
            case "INICIO" -> Optional.of(parseInicio(root));
            case "EVENTO" -> Optional.of(parseEvento(root));
            case "FINAL" -> Optional.of(parseFinal(root));
            default -> Optional.empty();
        };
    }

    /*
        Funções privates da parser para lidar com Evento de Inicio, Play e End.
    */


    /**
     * 
     * @param root JsonNode com as informações do evento
     * @return NbaPrimitiveEvent MatchStartEvent que guarda 
     */
    private static MatchStartEvent parseInicio(JsonNode root) {
        String match = root.path("match").asText("");
        List<Team> teams = new ArrayList<>();
        for (Iterator<String> it = root.fieldNames(); it.hasNext(); ) {
            String teamName = it.next();
            if ("tipo".equals(teamName) || "match".equals(teamName)) {
                continue;
            }
            JsonNode teamNode = root.path(teamName);
            List<Player> starters = new ArrayList<>();
            List<Player> reserves = new ArrayList<>();
            Team team = new Team(teamName, starters, reserves);

            for (JsonNode n : teamNode.path("titulares")) {
                if (n.isTextual()) {
                    starters.add(new Player(n.asText(), -1, team, ""));
                }
            }
            for (JsonNode n : teamNode.path("reservas")) {
                if (n.isTextual()) {
                    reserves.add(new Player(n.asText(), -1, team, ""));
                }
            }
            teams.add(team);
        }
        return new MatchStartEvent(match, teams, root);
    }

    private static MatchPlayEvent parseEvento(JsonNode root) {
        int quarter = root.path("quarto").asInt(-1);
        String teamName = root.path("time").asText("");
        String scoreboard = root.path("placar").asText("");
        JsonNode detalhes = root.path("detalhes");
        PlayAction action = parseDetalhes(detalhes);
        return new MatchPlayEvent(quarter, teamName, scoreboard, action);
    }

    private static MatchEndEvent parseFinal(JsonNode root) {
        String placarLinha = root.path("match").asText("");
        return new MatchEndEvent("", placarLinha, root);
    }

    private static PlayAction parseDetalhes(JsonNode det) {
        if (det == null || det.isNull() || det.isMissingNode()) {
            return new PlayAction.Unknown("");
        }
        String acao = det.path("ação").asText("");
        return switch (acao) {
            case "POINT" -> new PlayAction.Point(
                    playerNamed(det.path("jogador").asText("")),
                    det.path("valor").asInt(0));
            case "FOUL" -> new PlayAction.Foul(
                    playerNamed(det.path("jogador_commit").asText("")),
                    playerNamed(det.path("jogador_receive").asText("")));
            case "TURNOVER" -> new PlayAction.Turnover(playerNamed(det.path("jogador").asText("")));
            case "SUBSTITUTION" -> new PlayAction.Substitution(
                    playerNamed(det.path("jogador_out").asText("")),
                    playerNamed(det.path("jogador_in").asText("")));
            default -> new PlayAction.Unknown(acao);
        };
    }

    private static Player playerNamed(String name) {
        return new Player(name, -1, null, "");
    }
}
