package br.ufes.soe.model;


public final class Player {
    private final String name;
    private final int age;
    private final Team team;
    private final String position;
    private int points;
    private int fouls;

    public Player(String name, int age, Team team, String position) {
        this.name = name != null ? name : "";
        this.age = age;
        this.team = team;
        this.position = position != null ? position : "";
    }

    public String getName() {
        return name;
    }

    public int getFouls() {
        return fouls;
    }

    public int getPoints() {
        return points;
    }

    public void IncrementPoints(int points) {
        this.points += points;
    }

    public void IncrementFouls() {
        this.fouls++;
    }

    public int getAge() {
        return age;
    }

    public Team getTeam() {
        return team;
    }

    public String getPosition() {
        return position;
    }

    @Override
    public String toString() {
        return name;
    }
}
