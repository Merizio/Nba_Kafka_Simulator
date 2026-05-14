package br.ufes.soe.derived.rules;

import br.ufes.soe.derived.model.Odds;
import br.ufes.soe.model.NbaPrimitiveEvent.MatchPlayEvent;

public class OddsCalculator {

    /**
     * Margem antes de inverter para odd decimal: sem isso a sigmóide encosta na capa máxima
     * ({@code 1/p → teto}), e valores “grudados” em 10,00 parecem não reagirem ao placar.
     */
    private static final double PROB_EPS = 0.06;

    private static final double MIN_DECIMAL_ODD = 1.01;
    private static final double MAX_DECIMAL_ODD = 24.99;

    public void calculateOdds(MatchPlayEvent e, Odds control, int scoreA, int scoreB) {
        int scoreDiff = scoreA - scoreB;
        double rawProbA = calcularWinProbability(scoreDiff, e.quarter());
        double probA = clamp(rawProbA);
        double probB = 1.0 - probA;

        control.setOddsA(toDecimalOdd(probA));
        control.setOddsB(toDecimalOdd(probB));
    }

    public double calcularWinProbability(int scoreDiff, int quarter) {
        double pesoQuarto = switch (quarter) {
            case 1 -> 0.025;
            case 2 -> 0.05;
            case 3 -> 0.075;
            case 4 -> 0.10;
            default -> 0.05;
        };

        double spreadLead = 1 / (1 + Math.exp(-(scoreDiff * pesoQuarto)));
        return spreadLead;
    }

    private static double clamp(double p) {
        return Math.min(1.0 - PROB_EPS, Math.max(PROB_EPS, p));
    }

    private static double toDecimalOdd(double winProbability) {
        double decimal = 1.0 / winProbability;
        return Math.min(MAX_DECIMAL_ODD, Math.max(MIN_DECIMAL_ODD, decimal));
    }
}
