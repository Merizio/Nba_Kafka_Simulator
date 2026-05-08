package br.ufes.soe.model;

/**
 * Ação dentro de um evento de jogo ({@link NbaPrimitiveEvent.MatchPlayEvent}), após o parse do campo {@code detalhes}.
 */
public sealed interface PlayAction permits PlayAction.Point, PlayAction.Foul, PlayAction.Turnover, PlayAction.Substitution, PlayAction.Unknown {

    record Point(Player player, int pointsValue) implements PlayAction {}

    record Foul(Player committedBy, Player receivedBy) implements PlayAction {}

    record Turnover(Player player) implements PlayAction {}

    record Substitution(Player playerOut, Player playerIn) implements PlayAction {}

    record Unknown(String rawActionKey) implements PlayAction {}
}
