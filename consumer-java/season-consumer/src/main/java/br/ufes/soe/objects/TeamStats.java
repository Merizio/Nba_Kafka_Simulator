package br.ufes.soe.objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TeamStats {
    private int win;
    private int lose;
    private int point_made;
    private int point_take;

    public TeamStats() {
    }

    @JsonCreator
    public TeamStats(
            @JsonProperty("win") int win,
            @JsonProperty("lose") int lose,
            @JsonProperty("point_made") int point_made,
            @JsonProperty("point_take") int point_take) {
        this.win = win;
        this.lose = lose;
        this.point_made = point_made;
        this.point_take = point_take;
    }

    public TeamStats apply(MatchOutcome outcome) {
        if (outcome.isWin()) {
            this.win++;
        } else {
            this.lose++;
        }
        this.point_made += outcome.score();
        this.point_take += outcome.op_score();
        return this;
    }

    public int getWin() {
        return win;
    }

    public int getLose() {
        return lose;
    }

    public int getPoint_made() {
        return point_made;
    }

    public int getPoint_take() {
        return point_take;
    }

    @Override
    public String toString() {
        return "TeamStats{"
                + "vitórias=" + win
                + ", derrotas=" + lose
                + ", pontosMarcados=" + point_made
                + ", pontosSofidos=" + point_take
                + '}';
    }
}
