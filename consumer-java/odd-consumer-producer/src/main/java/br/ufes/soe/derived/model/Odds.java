package br.ufes.soe.derived.model;

import br.ufes.soe.model.Team;

public class Odds {
    public Odds() {
    }
    public Odds(Team teamA, Team teamB, Double oddsA, Double oddsB) {
        this.teamA = teamA.getName();
        this.teamB = teamB.getName();
        this.oddsA = oddsA;
        this.oddsB = oddsB;
    }
    
    private String teamA;
    private String teamB;    
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

    private Double oddsA, oddsB;
    
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
}


