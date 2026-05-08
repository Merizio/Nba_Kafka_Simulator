package br.ufes.soe.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Evento já interpretado a partir do JSON do produtor Python (sem lógica de negócio).
 */
public sealed interface NbaPrimitiveEvent permits
        NbaPrimitiveEvent.MatchStartEvent,
        NbaPrimitiveEvent.MatchPlayEvent,
        NbaPrimitiveEvent.UnrecognizedEvent {

    /** {@code sourcePayload} é o nó JSON original (apenas referência para log/diagnóstico). */
    record MatchStartEvent(String match, List<String> teamNames, JsonNode sourcePayload) implements NbaPrimitiveEvent {}

    record MatchPlayEvent(int quarter, String team, String scoreboard, PlayAction action) implements NbaPrimitiveEvent {}

    record UnrecognizedEvent(String tipoHint, JsonNode rawNode) implements NbaPrimitiveEvent {}
}
