package br.ufes.soe.season;

/**
 * Ponto de entrada do novo consumidor — implementar assinatura Kafka e regras aqui.
 */
public final class SeasonConsumerMain {

    private static final String BOOTSTRAP = "localhost:19092";
    private static final String TOPIC = "nba_game";
    private static final String GROUP_ID = "season-consumer-grupo";

    public static void main(String[] args) {
        System.err.printf(
                "season-consumer pronto para implementação (topic=%s, bootstrap=%s, group=%s)%n",
                TOPIC, BOOTSTRAP, GROUP_ID);
    }

    private SeasonConsumerMain() {
    }
}
