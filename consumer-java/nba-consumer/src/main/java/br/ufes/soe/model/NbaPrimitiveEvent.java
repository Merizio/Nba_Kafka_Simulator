package br.ufes.soe.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;


public sealed interface NbaPrimitiveEvent permits
        NbaPrimitiveEvent.MatchStartEvent,
        NbaPrimitiveEvent.MatchPlayEvent,
        NbaPrimitiveEvent.MatchEndEvent {

    record MatchStartEvent(String match, List<Team> teams, JsonNode sourcePayload) implements NbaPrimitiveEvent {}

    record MatchPlayEvent(int quarter, String teamName, String scoreboard, PlayAction action) implements NbaPrimitiveEvent {}

    record MatchEndEvent(String matchTitleHint, String finalScoreboard, JsonNode sourcePayload) implements NbaPrimitiveEvent {}
}
