package br.ufes.soe.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Team {

    private final String name;
    private final List<Player> starters;
    private final List<Player> reserves;
    private int points;

    public Team(String name, List<Player> starters, List<Player> reserves) {
        this.name = name != null ? name : "";
        this.starters = starters != null ? starters : new ArrayList<>();
        this.reserves = reserves != null ? reserves : new ArrayList<>();
        this.points = 0;
    }

    public String getName() {
        return name;
    }

    public List<Player> getStarters() {
        return starters;
    }

    public List<Player> getReserves() {
        return reserves;
    }

    /** Lista imutável para uso seguro fora do parser (opcional). */
    public List<Player> startersView() {
        return Collections.unmodifiableList(starters);
    }

    public List<Player> reservesView() {
        return Collections.unmodifiableList(reserves);
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }
}
