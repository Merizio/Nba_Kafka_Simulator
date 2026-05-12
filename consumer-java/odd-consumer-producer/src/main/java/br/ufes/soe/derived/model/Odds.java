package br.ufes.soe.derived.model;

import br.ufes.soe.model.Team;

public class Odds {
    public Odds() {
    }
    public Odds(Team teamA, Team teamB, Double oddsA, Double oddsB) {
        this.teamA = teamA;
        this.teamB = teamB;
        this.oddsA = oddsA;
        this.oddsB = oddsB;
    }
    
    private Team teamA;
    private Team teamB;    
    public Team getTeamA() {
        return teamA;
    }
    public void setTeamA(Team teamA) {
        this.teamA = teamA;
    }

    public Team getTeamB() {
        return teamB;
    }
    public void setTeamB(Team teamB) {
        this.teamB = teamB;
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


