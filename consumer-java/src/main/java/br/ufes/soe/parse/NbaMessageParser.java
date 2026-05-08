package br.ufes.soe.parse;

import br.ufes.soe.model.NbaPrimitiveEvent;
import br.ufes.soe.model.NbaPrimitiveEvent.MatchPlayEvent;
import br.ufes.soe.model.NbaPrimitiveEvent.MatchStartEvent;
import br.ufes.soe.model.NbaPrimitiveEvent.UnrecognizedEvent;
import br.ufes.soe.model.PlayAction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Traduz o payload JSON do produtor em {@link NbaPrimitiveEvent}. Sem regras de negócio.
 */
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
            case "" -> Optional.empty();
            default -> Optional.of(new UnrecognizedEvent(tipo, root));
        };
    }

    private static MatchStartEvent parseInicio(JsonNode root) {
        String match = root.path("match").asText("");
        List<String> teams = new ArrayList<>();
        for (Iterator<String> it = root.fieldNames(); it.hasNext(); ) {
            String key = it.next();
            if ("tipo".equals(key) || "match".equals(key)) {
                continue;
            }
            teams.add(key);
        }
        return new MatchStartEvent(match, List.copyOf(teams), root);
    }

    private static MatchPlayEvent parseEvento(JsonNode root) {
        int quarter = root.path("quarto").asInt(-1);
        String team = root.path("time").asText("");
        String scoreboard = root.path("placar").asText("");
        JsonNode det = root.path("detalhes");
        PlayAction action = parseDetalhes(det);
        return new MatchPlayEvent(quarter, team, scoreboard, action);
    }

    private static PlayAction parseDetalhes(JsonNode det) {
        if (det == null || det.isNull() || det.isMissingNode()) {
            return new PlayAction.Unknown("");
        }
        String acao = det.path("ação").asText("");
        return switch (acao) {
            case "POINT" -> new PlayAction.Point(
                    det.path("jogador").asText(""),
                    det.path("valor").asInt(0));
            case "FOUL" -> new PlayAction.Foul(
                    det.path("jogador_commit").asText(""),
                    det.path("jogador_receive").asText(""));
            case "TURNOVER" -> new PlayAction.Turnover(det.path("jogador").asText(""));
            case "SUBSTITUTION" -> new PlayAction.Substitution(
                    det.path("jogador_out").asText(""),
                    det.path("jogador_in").asText(""));
            default -> new PlayAction.Unknown(acao);
        };
    }
}
