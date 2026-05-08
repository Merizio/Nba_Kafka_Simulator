package br.ufes.soe.model;

/**
 * Ação dentro de um evento de jogo ({@link NbaPrimitiveEvent.MatchPlayEvent}), após o parse do campo {@code detalhes}.
 */
public sealed interface PlayAction permits PlayAction.Point, PlayAction.Foul, PlayAction.Turnover, PlayAction.Substitution, PlayAction.Unknown {

    record Point(String player, int pointsValue) implements PlayAction {}

    record Foul(String committedBy, String receivedBy) implements PlayAction {}

    record Turnover(String player) implements PlayAction {}

    record Substitution(String playerOut, String playerIn) implements PlayAction {}

    record Unknown(String rawActionKey) implements PlayAction {}
}
