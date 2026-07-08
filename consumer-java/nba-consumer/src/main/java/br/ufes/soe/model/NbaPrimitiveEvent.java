package br.ufes.soe.model;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Evento já interpretado a partir do JSON do produtor Python (sem lógica de negócio).
 */
public sealed interface NbaPrimitiveEvent permits
        NbaPrimitiveEvent.MatchStartEvent,
        NbaPrimitiveEvent.MatchPlayEvent,
        NbaPrimitiveEvent.MatchEndEvent,
        NbaPrimitiveEvent.UnrecognizedEvent {

    /** {@code sourcePayload} é o nó JSON original (apenas referência para log/diagnóstico). */
    record MatchStartEvent(String match, List<Team> teams, JsonNode sourcePayload) implements NbaPrimitiveEvent {}

    /** {@code teamName} corresponde ao campo {@code time} do produtor (nome do time no ataque). */
    record MatchPlayEvent(int quarter, String teamName, String scoreboard, PlayAction action) implements NbaPrimitiveEvent {}

    /** Fim da partida: produtor envia {@code tipo FINAL}; placar completo costuma vir em {@code match}. */
    record MatchEndEvent(String matchTitleHint, String finalScoreboard, JsonNode sourcePayload) implements NbaPrimitiveEvent {}

    record UnrecognizedEvent(String tipoHint, JsonNode rawNode) implements NbaPrimitiveEvent {}
}
