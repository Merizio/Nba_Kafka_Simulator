package br.ufes.soe.derived.rules;

import br.ufes.soe.derived.model.Odds;
import br.ufes.soe.model.NbaPrimitiveEvent.MatchPlayEvent;

public class OddsCalculator {

    public void calculateOdds(MatchPlayEvent e, Odds control, int scoreA, int scoreB){
        Integer scoreDiff = (int) (scoreA-scoreB);
        double probA = calcularWinProbability(scoreDiff, e.quarter());
        double probB = (1.0-probA);

        control.setOddsA(calculaOddFinal(probA));
        control.setOddsA(calculaOddFinal(probB));
    };


    public double calcularWinProbability(int scoreDiff, int quarter) {

        double pesoQuarto = switch (quarter) {
        case 1 -> 0.15;
        case 2 -> 0.30;
        case 3 -> 0.55;
        case 4 -> 0.90;
        default -> 0.50;
        };

        double spreadLead = 1 / (1 + Math.exp(-(scoreDiff * pesoQuarto)));
        return spreadLead; // Usa uma função sigmóide para manter entre 0 e 1
    }

    public double calculaOddFinal(double probability){
        return (1/probability);
    }
}
