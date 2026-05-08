package br.ufes.soe.rules;

import br.ufes.soe.model.MatchState;
import br.ufes.soe.model.NbaPrimitiveEvent;
import br.ufes.soe.model.NbaPrimitiveEvent.MatchPlayEvent;
import br.ufes.soe.model.NbaPrimitiveEvent.MatchStartEvent;
import br.ufes.soe.model.NbaPrimitiveEvent.UnrecognizedEvent;
import br.ufes.soe.model.PlayAction;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Regras de negócio e ações diante dos eventos primitivos já parseados.
 */
public final class GameMonitoringRules {

    private final ObjectMapper mapper;

    public GameMonitoringRules(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void apply(NbaPrimitiveEvent event, MatchState state) throws Exception {
        if (event instanceof MatchStartEvent start) {
            onMatchStart(start, state);
        } else if (event instanceof MatchPlayEvent play) {
            onMatchPlay(play, state);
        } else if (event instanceof UnrecognizedEvent bad) {
            onUnrecognized(bad);
        }
    }

    private void onMatchStart(MatchStartEvent e, MatchState state) throws Exception {
        state.resetForNewMatch(e.match());
        System.out.println("[INÍCIO DE PARTIDA] " + (e.match().isEmpty() ? "(sem campo match)" : e.match()));
        for (String team : e.teamNames()) {
            System.out.printf("  time no JSON: %s%n", team);
        }
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(e.sourcePayload()));
    }

    private void onMatchPlay(MatchPlayEvent e, MatchState state) {
        PlayAction a = e.action();
        System.out.printf(
                "[EVENTO] quarto=%d time=%s placar=%s ação=%s%n",
                e.quarter(),
                e.team(),
                e.scoreboard(),
                actionLabel(a));

        if (a instanceof PlayAction.Point p) {
            System.out.printf("  → Cesta: %s (%d pts)%n", p.player(), p.pointsValue());
        } else if (a instanceof PlayAction.Foul f) {
            int n = state.incrementFoul(f.committedBy());
            System.out.printf(
                    "  → Falta: %s (total de faltas neste jogador no estado local: %d)%n",
                    f.committedBy(),
                    n);
        } else if (a instanceof PlayAction.Turnover t) {
            int total = state.incrementTurnoverAndGetTotal();
            System.out.printf("  → Turnover #%d: %s%n", total, t.player());
        } else if (a instanceof PlayAction.Substitution s) {
            System.out.printf(
                    "  → Sub: sai %s, entra %s%n",
                    s.playerOut(),
                    s.playerIn());
        } else if (a instanceof PlayAction.Unknown u) {
            System.out.printf("  → Ação não mapeada: `%s`%n", u.rawActionKey());
        }
    }

    private void onUnrecognized(UnrecognizedEvent e) throws Exception {
        System.out.printf("[tipo desconhecido] %s%n", e.tipoHint());
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(e.rawNode()));
    }

    private static String actionLabel(PlayAction a) {
        if (a instanceof PlayAction.Point) {
            return "POINT";
        }
        if (a instanceof PlayAction.Foul) {
            return "FOUL";
        }
        if (a instanceof PlayAction.Turnover) {
            return "TURNOVER";
        }
        if (a instanceof PlayAction.Substitution) {
            return "SUBSTITUTION";
        }
        if (a instanceof PlayAction.Unknown u) {
            return u.rawActionKey().isEmpty() ? "?" : u.rawActionKey();
        }
        return "?";
    }
}
