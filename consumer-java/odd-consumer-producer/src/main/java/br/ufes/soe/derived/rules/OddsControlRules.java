package br.ufes.soe.derived.rules;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.ufes.soe.derived.model.Odds;
import br.ufes.soe.model.MatchState;
import br.ufes.soe.model.NbaPrimitiveEvent;
import br.ufes.soe.model.NbaPrimitiveEvent.MatchPlayEvent;
import br.ufes.soe.model.NbaPrimitiveEvent.MatchStartEvent;
import br.ufes.soe.model.Team;

public class OddsControlRules {
    private final ObjectMapper mapper = new ObjectMapper();
    
    public void apply(NbaPrimitiveEvent event, MatchState state, Odds control) throws Exception {
        if (event instanceof MatchPlayEvent play) {
            lookScoreBoard(play, control);
        }else if (event instanceof MatchStartEvent start) {
            startMatch(start, state, control);
        }
    }

    private void startMatch(MatchStartEvent e, MatchState state, Odds control) throws Exception {
        state.resetForNewMatch(e.match(), e.teams());
        control.setTeamA(e.teams().get(0));
        control.setTeamB(e.teams().get(1));

        System.out.println("[INÍCIO DE PARTIDA] " + (e.match().isEmpty() ? "(sem campo match)" : e.match()));
        for (Team team : e.teams()) {
            System.out.printf(
                    "  time: %s — titulares=%d, reservas=%d%n",
                    team.getName(),
                    team.getStarters().size(),
                    team.getReserves().size());
        }
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(e.sourcePayload()));
    }

    private void lookScoreBoard(MatchPlayEvent e, Odds control) {
        System.out.printf(
                "[EVENTO] quarto=%d, placar=%s%n",
                e.quarter(),
                e.scoreboard());

        String[] scores = e.scoreboard().split(" X ");
        try {
            String[] scoresA = scores[0].split(" ");
            String[] scoresB = scores[1].split(" ");
            int scoreA = Integer.parseInt(scoresA[1]);
            int scoreB = Integer.parseInt(scoresB[0]);

            //calcular se vai modificar as odds
            OddsCalculator calc = new OddsCalculator();

            calc.calculateOdds(e, control, scoreA, scoreB);
            
            System.out.printf("  [SCOREBOARD ATUALIZADO] %s: %d (%.2f)| %s: %d (%.2f)%n", 
            control.getTeamA(), scoreA, control.getOddsA(), 
            control.getTeamB(),scoreB, control.getOddsB());

        } catch (NumberFormatException ex) {
            System.err.printf("  [ERRO ao parsear placar] '%s' - %s%n", e.scoreboard(), ex.getMessage());
        }
    }
}
