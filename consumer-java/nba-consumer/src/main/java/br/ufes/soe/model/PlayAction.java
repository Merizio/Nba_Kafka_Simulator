package br.ufes.soe.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Ação dentro de um evento de jogo NbaPrimitiveEvent MatchPlayEvent
 */

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME, 
    include = JsonTypeInfo.As.PROPERTY, 
    property = "type", // O JSON deve ter um campo "type" indicando qual é a ação
    visible=true
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = PlayAction.Point.class, name = "Point"),
    @JsonSubTypes.Type(value = PlayAction.Foul.class, name = "Foul"),
    @JsonSubTypes.Type(value = PlayAction.Turnover.class, name = "Turnover"),
    @JsonSubTypes.Type(value = PlayAction.Substitution.class, name = "Substitution"),
    @JsonSubTypes.Type(value = PlayAction.Unknown.class, name = "Unknown") 

})

public sealed interface PlayAction permits PlayAction.Point, PlayAction.Foul, PlayAction.Turnover, PlayAction.Substitution, PlayAction.Unknown {

    record Point(Player player, int pointsValue) implements PlayAction {}

    record Foul(Player committedBy, Player receivedBy) implements PlayAction {}

    record Turnover(Player player) implements PlayAction {}

    record Substitution(Player playerOut, Player playerIn) implements PlayAction {}

    record Unknown(String rawActionKey) implements PlayAction {}
}
