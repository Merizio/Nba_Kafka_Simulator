package br.ufes.soe.derived.rules;

import br.ufes.soe.derived.model.Odds;
import br.ufes.soe.model.NbaPrimitiveEvent.MatchPlayEvent;

public class OddsCalculator {

    public void calculateOdds(MatchPlayEvent e, Odds control, int scoreA, int scoreB){
        Integer scoreDiff = (int) (scoreA-scoreB);
        double probA = calcularWinProbability(scoreDiff, e.quarter());
        double probB = (1.0-probA);

        control.setOddsA(calculaOddFinal(probA));
        control.setOddsB(calculaOddFinal(probB));
    };


    public double calcularWinProbability(int scoreDiff, int quarter) {

        double pesoQuarto = switch (quarter) {
        case 1 -> 0.025;
        case 2 -> 0.05;
        case 3 -> 0.075;
        case 4 -> 0.10;
        default -> 0.05;
        };

        double spreadLead = 1 / (1 + Math.exp(-(scoreDiff * pesoQuarto)));
        return spreadLead; // Usa uma função sigmóide para manter entre 0 e 1
    }

    public double calculaOddFinal(double probability){
        double prob = 1/(probability);
        if (prob>10){
            return 10;
        }else if(prob<1){
            return 1.01;
        }else{
            return prob;
        }
    }
}
