package br.ufes.soe.derived.model;

import br.ufes.soe.model.Team;

public class Odds {

    private String teamA;
    private String teamB;    
    private Double oddsA, oddsB;

    public Odds() {
    }
    public Odds(Team teamA, Team teamB, Double oddsA, Double oddsB) {
        this.teamA = teamA.getName();
        this.teamB = teamB.getName();
        this.oddsA = oddsA;
        this.oddsB = oddsB;
    }
    
    public String getTeamA() {
        return teamA;
    }
    public void setTeamA(Team teamA) {
        this.teamA = teamA.getName();
    }

    public String getTeamB() {
        return teamB;
    }
    public void setTeamB(Team teamB) {
        this.teamB = teamB.getName();
    }
    
    public Double getOddsA() {
        return oddsA;
    }
    public void setOddsA(Double oddsA) {
        this.oddsA = oddsA;
    }
    public Double getOddsB() {
        return oddsB;
    }
    public void setOddsB(Double oddsB) {
        this.oddsB = oddsB;
    }

    public void printOdds() {
        System.out.printf("%s: %f.2 X %s: %f.2", teamA, oddsA, teamB, oddsB);
    }
}


