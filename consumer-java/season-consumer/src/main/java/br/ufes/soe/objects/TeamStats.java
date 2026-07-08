package br.ufes.soe.objects;

public class TeamStats {
    private int win = 0;
    private int lose = 0;
    private int point_made = 0;
    private int point_take = 0;

    public TeamStats apply(MatchOutcome outcome) {
        if (outcome.isWin()) this.win++;
        else this.lose++;

        this.point_made += outcome.score();
        this.point_take += outcome.op_score();

        return this;
    }
}
