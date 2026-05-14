package br.ufes.soe.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class OddsPayload {

    private String teamA;
    private String teamB;
    private Double oddsA;
    private Double oddsB;

    public String getTeamA() {
        return teamA;
    }

    public void setTeamA(String teamA) {
        this.teamA = teamA;
    }

    public String getTeamB() {
        return teamB;
    }

    public void setTeamB(String teamB) {
        this.teamB = teamB;
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
}
