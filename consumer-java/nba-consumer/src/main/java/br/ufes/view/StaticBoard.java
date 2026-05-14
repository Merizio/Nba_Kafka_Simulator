package br.ufes.view;

import br.ufes.soe.model.MatchState;
import br.ufes.soe.model.Player;
import br.ufes.soe.model.Team;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Painel ao vivo no terminal (ANSI): largura fixa entre barras, alinhamento monoespaçado.
 */
public final class StaticBoard {

    /** Texto útil entre {@code ║} e {@code ║} (espaço + conteúdo + espaço = CONTENT_W + 2 no topo). */
    private static final int INNER_TEXT = 56;

    private static final String ANSI_HOME = "\033[H";
    private static final String ANSI_CLR = "\033[2J";
    private static final String ANSI_SHOW_CURSOR = "\033[?25h";
    private static final String ANSI_HIDE_CURSOR = "\033[?25l";

    /** Espaço horizontal interior entre cantos (alinha com cada linha {@code ║ … ║}). */
    private static final int INNER_FILL = INNER_TEXT + 2;

    public StaticBoard() {
    }

    public void startLiveSession() {
        System.out.print(ANSI_HIDE_CURSOR);
    }

    public void endLiveSession() {
        System.out.print(ANSI_SHOW_CURSOR);
    }

    public void renderLive(MatchState state) {
        Names names = fitTeamNames(state);
        StringBuilder sb = new StringBuilder(1600);
        sb.append(ANSI_CLR).append(ANSI_HOME);

        hRule(sb, '╔', '═', '╗');
        cell(sb, padCenter("NBA — ao vivo (Kafka)", INNER_TEXT));
        hRule(sb, '╠', '═', '╣');

        int hs = state.getScoreHome();
        int as = state.getScoreAway();
        String scoreLine = formatScoreRow(names.teamHome(), hs, as, names.teamAway());
        cell(sb, scoreLine);

        if (state.hasOddsSnapshot()) {
            hRule(sb, '╠', '═', '╣');
            cell(sb, formatOddsLine(state));
        }

        hRule(sb, '╠', '═', '╣');

        int q = state.getCurrentQuarter();
        cell(sb, " Quarto: " + (q > 0 ? q + "º" : "—"));

        hRule(sb, '╠', '═', '╣');
        appendTopBlock(sb, state, names.teamHome());
        hRule(sb, '╠', '═', '╣');
        appendTopBlock(sb, state, names.teamAway());

        hRule(sb, '╚', '═', '╝');
        cell(sb, padCenter("Ctrl+C para sair", INNER_TEXT));

        System.out.print(sb);
        System.out.flush();
    }

    private static void appendTopBlock(StringBuilder sb, MatchState state, String teamRaw) {
        String team = nz(teamRaw);
        String title = truncate("Top 3 — " + team, INNER_TEXT);
        cell(sb, " " + title);

        List<Map.Entry<String, Integer>> top = state.topScorersForTeam(team, 3);
        for (int i = 0; i < 3; i++) {
            String row;
            if (i < top.size()) {
                Map.Entry<String, Integer> e = top.get(i);
                String name = truncate(e.getKey(), 38);
                row = String.format("  %d. %-38s %5d pts", i + 1, name, e.getValue());
            } else {
                row = String.format("  %d. %38s %8s", i + 1, "—", "");
            }
            cell(sb, truncate(row, INNER_TEXT));
        }
    }

    /** Uma linha de conteúdo: {@code ║ } + texto ({@link #INNER_TEXT} cols) + {@code  ║}. */
    private static void cell(StringBuilder sb, String text) {
        String body = truncate(text, INNER_TEXT);
        sb.append('║')
                .append(' ')
                .append(String.format("%-" + INNER_TEXT + "s", body))
                .append(' ')
                .append('║')
                .append('\n');
    }

    private static void hRule(StringBuilder sb, char left, char fill, char right) {
        sb.append(left);
        for (int i = 0; i < INNER_FILL; i++) {
            sb.append(fill);
        }
        sb.append(right).append('\n');
    }

    private static String formatScoreRow(String homeName, int homePts, int awayPts, String awayName) {
        String h = truncate(nz(homeName), 18);
        String a = truncate(nz(awayName), 18);
        String core = String.format("%-18s  %3d   ×   %-3d  %-18s", h, homePts, awayPts, a);
        return " " + truncate(core, INNER_TEXT);
    }

    private static String formatOddsLine(MatchState state) {
        String la = truncate(nz(state.getOddsLabelTeamA()), 16);
        String lb = truncate(nz(state.getOddsLabelTeamB()), 16);
        String core = String.format(
                "Odds  %s  %.2f  ×  %.2f  %s",
                la, state.getOddsValueA(), state.getOddsValueB(), lb);
        return " " + truncate(core, INNER_TEXT);
    }

    private static String padCenter(String s, int width) {
        String t = truncate(s, width);
        if (t.length() >= width) {
            return t;
        }
        int pad = width - t.length();
        int left = pad / 2;
        int right = pad - left;
        return " ".repeat(left) + t + " ".repeat(right);
    }

    private record Names(String teamHome, String teamAway) {}

    private static Names fitTeamNames(MatchState state) {
        String h = nz(state.getTeamHomeName());
        String a = nz(state.getTeamAwayName());
        if (!h.isEmpty() && !a.isEmpty()) {
            return new Names(h, a);
        }
        List<Team> teams = state.getTeams();
        if (teams.size() >= 2) {
            return new Names(teams.get(0).getName(), teams.get(1).getName());
        }
        return new Names(h.isEmpty() ? "Time A" : h, a.isEmpty() ? "Time B" : a);
    }

    public static void printFinalReport(PrintStream out, MatchState state) {
        out.println();
        hRulePlain(out, '╔', '═', '╗');
        plainCell(out, padCenter("FIM DE PARTIDA — ESTATÍSTICAS", INNER_TEXT));
        hRulePlain(out, '╠', '═', '╣');

        plainCell(out, " Turnovers (total): " + state.getTurnoverCount());

        hRulePlain(out, '╠', '═', '╣');

        for (Team t : state.getTeams()) {
            plainCell(out, " " + truncate("── " + t.getName() + " ──", INNER_TEXT));
            String hdr = String.format("   %-26s %5s %7s", "Jogador", "Pts", "Faltas");
            plainCell(out, truncate(hdr, INNER_TEXT));
            List<Player> all = new ArrayList<>();
            all.addAll(t.getStarters());
            all.addAll(t.getReserves());
            all.sort(Comparator.comparingInt(Player::getPoints).reversed());
            for (Player p : all) {
                String row = String.format(
                        "   %-26s %5d %7d",
                        truncate(p.getName(), 26),
                        p.getPoints(),
                        p.getFouls());
                plainCell(out, truncate(row, INNER_TEXT));
            }
            plainCell(out, "");
        }

        hRulePlain(out, '╚', '═', '╝');
        plainCell(out, padCenter("(fim do relatório)", INNER_TEXT));
        out.println();
    }

    private static void hRulePlain(PrintStream out, char left, char fill, char right) {
        out.print(left);
        out.print(String.valueOf(fill).repeat(INNER_FILL));
        out.println(right);
    }

    private static void plainCell(PrintStream out, String text) {
        String body = truncate(text, INNER_TEXT);
        out.print('║');
        out.print(' ');
        out.printf("%-" + INNER_TEXT + "s", body);
        out.print(' ');
        out.println('║');
    }

    private static String nz(String s) {
        return s != null ? s : "";
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, Math.max(0, max - 3)) + "...";
    }
}
