package br.ufes.soe.derived;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer; // CORRETO

import com.fasterxml.jackson.databind.ObjectMapper;

import br.ufes.soe.derived.model.Odds;
import br.ufes.soe.derived.rules.OddsControlRules;
import br.ufes.soe.model.MatchState;
import br.ufes.soe.model.NbaPrimitiveEvent;
import br.ufes.soe.parse.NbaMessageParser;
import br.ufes.soe.rules.GameMonitoringRules;

/**
 * Orquestra o cliente Kafka: assina o tópico, faz poll e delega parse ({@link NbaMessageParser})
 * e regras ({@link GameMonitoringRules}).
 */
public final class OddConsumerProducer {

    private static final String BOOTSTRAP = "localhost:19092";
    private static final String CONSUMERTOPIC = "nba_game";
    private static final String PRODUCERTOPIC = "odds_game";
    private static final String GROUP_ID = "nba-monitor-grupo";


    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        NbaMessageParser parser = new NbaMessageParser(mapper);


        //config consumer
        Properties consumer_props = new Properties();
        consumer_props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
        consumer_props.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        consumer_props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumer_props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumer_props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer_props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

        //config producer
        Properties producer_props = new Properties();
        producer_props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
        producer_props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producer_props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producer_props.put(ProducerConfig.ACKS_CONFIG, "all");
    
        
        OddsControlRules regras = new OddsControlRules();
        MatchState state = new MatchState();
        Odds apostas = new Odds();

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumer_props);
            KafkaProducer<String, String> producer = new KafkaProducer<>(producer_props)) {

            consumer.subscribe(Collections.singletonList(CONSUMERTOPIC));
            System.out.printf("Consumindo tópico `%s` em %s (group=%s) para %s%n", CONSUMERTOPIC, BOOTSTRAP, GROUP_ID, PRODUCERTOPIC);
            System.out.println("Aguardando mensagens… (Ctrl+C para sair)\n");

            //loop de consumo
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

                       

                        //rules.apply(parsed.get(), state);
                        regras.apply(parsed.get(), state, apostas);
                        System.out.println();

                        String jsonOdds = mapper.writeValueAsString(apostas);
                        producer.send(new ProducerRecord<>(PRODUCERTOPIC, jsonOdds));
                    } catch (Exception e) {
                        System.err.println("[erro] offset=" + record.offset() + ": " + e.getMessage());
                    }
                }
                producer.flush();
            }
        }
    }

    private OddConsumerProducer() {
    }
}
