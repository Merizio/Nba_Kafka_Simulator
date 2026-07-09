package br.ufes.soe.objects;

public class TeamStats {
    private int win = 0;
    private int lose = 0;
    private int point_made = 0;
    private int point_take = 0;

    // 1. Construtor vazio (Necessário para desserialização do JSON)
    public TeamStats() {
    }

    public TeamStats apply(MatchOutcome outcome) {
        if (outcome.isWin()) this.win++;
        else this.lose++;

        this.point_made += outcome.score();
        this.point_take += outcome.op_score();

        return this;
    }

    // 2. Setters (Necessários para que o JsonSerde preencha os dados)
    public void setWin(int win) { this.win = win; }
    public void setLose(int lose) { this.lose = lose; }
    public void setPoint_made(int point_made) { this.point_made = point_made; }
    public void setPoint_take(int point_take) { this.point_take = point_take; }

    // Getters existentes
    public int getWin() { return win; }
    public int getLose() { return lose; }
    public int getPoint_made() { return point_made; }
    public int getPoint_take() { return point_take; }

    @Override
    public String toString() {
        return "TeamStats{" +
              "vitórias=" + this.win + 
              ", derrotas=" + this.lose +
              ", pontosMarcados=" + this.point_made +
              ", pontosSofidos=" + this.point_take +
              '}';
    }
}