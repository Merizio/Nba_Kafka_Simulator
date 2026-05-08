package br.ufes.soe;

import br.ufes.soe.model.MatchState;
import br.ufes.soe.model.NbaPrimitiveEvent;
import br.ufes.soe.parse.NbaMessageParser;
import br.ufes.soe.rules.GameMonitoringRules;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Properties;

/**
 * Orquestra o cliente Kafka: assina o tópico, faz poll e delega parse ({@link NbaMessageParser})
 * e regras ({@link GameMonitoringRules}).
 */
public final class NbaGameConsumer {

    private static final String BOOTSTRAP = "localhost:19092";
    private static final String TOPIC = "nba_game";
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
            consumer.subscribe(Collections.singletonList(TOPIC));
            System.out.printf("Consumindo tópico `%s` em %s (group=%s)%n", TOPIC, BOOTSTRAP, GROUP_ID);
            System.out.println("Aguardando mensagens… (Ctrl+C para sair)\n");

            while (true) {
                // poll para buscar os novos eventos
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
                for (ConsumerRecord<String, String> record : records) {
                    // record.value() é o valor da mensagem: JSON convertido para String que foi enviado pelo produtor
                    String raw = record.value();
                    if (raw == null) {
                        continue;
                    }
                    try {
                        // parseToTree converte a String JSON em um objeto JsonNode
                        // toEvent converte o JsonNode em um objeto NbaPrimitiveEvent
                        Optional<NbaPrimitiveEvent> parsed = parser.toEvent(parser.parseToTree(raw));
                        if (parsed.isEmpty()) {
                            System.err.println("[parse] mensagem sem tipo reconhecível offset=" + record.offset());
                            continue;
                        }
                        System.out.printf("--- offset=%d partition=%d%n", record.offset(), record.partition());
                        rules.apply(parsed.get(), state);
                        System.out.println();
                    } catch (Exception e) {
                        System.err.println("[erro] offset=" + record.offset() + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    private NbaGameConsumer() {
    }
}
