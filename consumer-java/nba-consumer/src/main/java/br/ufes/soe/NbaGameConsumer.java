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


/*
Consumidor dos dois tópicos (nba_game, odds_game) que faz a geração do placar com as estatisticas
*/
public final class NbaGameConsumer {

    /* Definindo constantes para config */
    private static final String BOOTSTRAP = "localhost:19092";
    private static final String TOPIC_GAME = "nba_game";
    private static final String TOPIC_ODDS = "odds_game";
    private static final List<String> TOPICS = Arrays.asList(TOPIC_GAME, TOPIC_ODDS);
    private static final String GROUP_ID = "nba-monitor-grupo";

    public static void main(String[] args) throws Exception {

        /* Configurações do Consumidor NbaGameConsumer */
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
            System.err.printf("\nTópicos(Consumidos): `%s` e `%s`\nServer: %s (group=%s)\n", TOPIC_GAME, TOPIC_ODDS, BOOTSTRAP, GROUP_ID);
            System.out.println("Aguardando mensagens…\n");
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
                for (ConsumerRecord<String, String> record : records) {

                    String raw = record.value();
                    if (raw == null) {
                        continue;
                    }

                    String topic = record.topic();
                    
                    try {
                        /* Caso o topico do evento seja odds_game, atualiza as odds e redesenha o painel */
                        if (TOPIC_ODDS.equals(topic)) {
                            OddsPayload odds = mapper.readValue(raw, OddsPayload.class);
                            rules.applyOddsUpdate(odds, state);
                            continue;
                        }

                        /* Caso seja topico nba_game */
                        Optional<NbaPrimitiveEvent> parsed = parser.toEvent(parser.parseToTree(raw));
            
                        if (parsed.isEmpty()) continue;
                        rules.apply(parsed.get(), state);

                    } catch (Exception e) {
                        System.err.println("[erro] topic=" + topic + " offset=" + record.offset() + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    private NbaGameConsumer() {
    }
}
