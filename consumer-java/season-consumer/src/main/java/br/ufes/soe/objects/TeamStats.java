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
        return "TeamStats{" +
            "vitórias=" + this.win +   // Substitua pelos nomes reais dos seus atributos
            ", derrotas=" + this.lose +
            ", pontosMarcados=" + this.point_made +
            ", pontosSofidos=" + this.point_take +
            '}';
    }

}
