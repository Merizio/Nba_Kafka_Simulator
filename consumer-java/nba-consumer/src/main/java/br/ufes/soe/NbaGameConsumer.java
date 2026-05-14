package br.ufes.soe;

import br.ufes.soe.model.MatchState;
import br.ufes.soe.model.NbaPrimitiveEvent;
import br.ufes.soe.model.OddsPayload;
import br.ufes.soe.parse.NbaMessageParser;
import br.ufes.soe.rules.GameMonitoringRules;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Orquestra o cliente Kafka: assina os tópicos de partida e de odds, faz poll e delega parse
 * ({@link NbaMessageParser}) e regras ({@link GameMonitoringRules}).
 */
public final class NbaGameConsumer {

    private static final String BOOTSTRAP = "localhost:19092";
    private static final String TOPIC_GAME = "nba_game";
    private static final String TOPIC_ODDS = "odds_game";
    private static final List<String> TOPICS = Arrays.asList(TOPIC_GAME, TOPIC_ODDS);
    private static final String GROUP_ID = "nba-monitor-grupo";

    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

        ObjectMapper mapper = new ObjectMapper();
        NbaMessageParser parser = new NbaMessageParser(mapper);
        GameMonitoringRules rules = new GameMonitoringRules(mapper);
        MatchState state = new MatchState();

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(TOPICS);
            System.err.printf(
                    "Consumindo `%s` e `%s` em %s (group=%s). O placar ao vivo usa o terminal (stdout).%n",
                    TOPIC_GAME, TOPIC_ODDS, BOOTSTRAP, GROUP_ID);

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
                for (ConsumerRecord<String, String> record : records) {
                    String raw = record.value();
                    if (raw == null) {
                        continue;
                    }
                    String topic = record.topic();
                    try {
                        if (TOPIC_ODDS.equals(topic)) {
                            OddsPayload odds = mapper.readValue(raw, OddsPayload.class);
                            rules.applyOddsUpdate(odds, state);
                            continue;
                        }
                        if (!TOPIC_GAME.equals(topic)) {
                            continue;
                        }
                        Optional<NbaPrimitiveEvent> parsed = parser.toEvent(parser.parseToTree(raw));
                        if (parsed.isEmpty()) {
                            System.err.println(
                                    "[parse] mensagem sem tipo reconhecível topic="
                                            + topic
                                            + " offset="
                                            + record.offset());
                            continue;
                        }
                        rules.apply(parsed.get(), state);
                    } catch (Exception e) {
                        System.err.println(
                                "[erro] topic=" + topic + " offset=" + record.offset() + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    private NbaGameConsumer() {
    }
}
