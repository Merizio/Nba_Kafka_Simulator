package br.ufes.soe.rules;

import br.ufes.soe.model.MatchState;
import br.ufes.soe.model.NbaPrimitiveEvent;
import br.ufes.soe.model.OddsPayload;
import br.ufes.soe.model.NbaPrimitiveEvent.MatchEndEvent;
import br.ufes.soe.model.NbaPrimitiveEvent.MatchPlayEvent;
import br.ufes.soe.model.NbaPrimitiveEvent.MatchStartEvent;
import br.ufes.soe.model.NbaPrimitiveEvent.UnrecognizedEvent;
import br.ufes.soe.model.PlayAction;
import br.ufes.view.StaticBoard;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Regras de negócio: atualiza estado, placar ao vivo no terminal e relatório final.
 */
public final class GameMonitoringRules {

    private final ObjectMapper mapper;
    private final StaticBoard board = new StaticBoard();
    private boolean liveBoardStarted;

    public GameMonitoringRules(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void apply(NbaPrimitiveEvent event, MatchState state) throws Exception {
        if (event instanceof MatchStartEvent start) {
            onMatchStart(start, state);
        } else if (event instanceof MatchPlayEvent play) {
            onMatchPlay(play, state);
        } else if (event instanceof MatchEndEvent end) {
            onMatchEnd(end, state);
        } else if (event instanceof UnrecognizedEvent bad) {
            onUnrecognized(bad);
        }
    }

    /** Atualiza snapshot de odds (tópico {@code odds_game}) e redesenha o painel se a sessão ao vivo já começou. */
    public void applyOddsUpdate(OddsPayload odds, MatchState state) {
        if (odds == null) {
            return;
        }
        state.updateOddsFromPayload(odds.getTeamA(), odds.getTeamB(), odds.getOddsA(), odds.getOddsB());
        if (liveBoardStarted) {
            board.renderLive(state);
        }
    }

    private void onMatchStart(MatchStartEvent e, MatchState state) {
        state.resetForNewMatch(e.match(), e.teams());
        if (!liveBoardStarted) {
            board.startLiveSession();
            liveBoardStarted = true;
        }
        board.renderLive(state);
    }

    private void onMatchPlay(MatchPlayEvent e, MatchState state) {
        state.updateFromPlacar(e.scoreboard());
        state.setCurrentQuarter(e.quarter());

        PlayAction a = e.action();
        String possessionTeam = e.teamName();

        if (a instanceof PlayAction.Point p) {
            state.recordPoint(possessionTeam, p.player().getName(), p.pointsValue());
        } else if (a instanceof PlayAction.Foul f) {
            state.recordFoulCommitted(possessionTeam, f.committedBy().getName());
        } else if (a instanceof PlayAction.Turnover) {
            state.incrementTurnoverAndGetTotal();
        } else if (a instanceof PlayAction.Substitution s) {
            state.applySubstitution(possessionTeam, s.playerOut().getName(), s.playerIn().getName());
        }

        board.renderLive(state);
    }

    private void onMatchEnd(MatchEndEvent e, MatchState state) {
        state.setMatchEnded(true);
        String fin = e.finalScoreboard();
        if (fin != null && !fin.isBlank()) {
            state.updateFromPlacar(fin);
        }
        board.endLiveSession();
        StaticBoard.printFinalReport(System.out, state);
    }

    private void onUnrecognized(UnrecognizedEvent e) throws Exception {
        System.err.printf("[tipo desconhecido] %s%n", e.tipoHint());
        System.err.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(e.rawNode()));
    }
}
