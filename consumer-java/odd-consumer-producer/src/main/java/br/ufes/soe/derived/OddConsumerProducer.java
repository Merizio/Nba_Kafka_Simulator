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

public final class OddConsumerProducer {

    private static final String BOOTSTRAP = "localhost:19092";
    private static final String CONSUMERTOPIC = "nba_game";
    private static final String PRODUCERTOPIC = "odds_game";
    private static final String GROUP_ID = "odds-pipeline-grupo";


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
            System.out.printf("\nTópico(Consumido): `%s`\nServer: %s (group=%s)\nTópico(Produzido): `%s`%n", CONSUMERTOPIC, BOOTSTRAP, GROUP_ID, PRODUCERTOPIC);
            System.out.println("Aguardando mensagens…\n");

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
                for (ConsumerRecord<String, String> record : records) {
                    String raw = record.value();
                    if (raw == null) {
                        continue;
                    }
                    try {
                        Optional<NbaPrimitiveEvent> parsed = parser.toEvent(parser.parseToTree(raw));
                        if (parsed.isEmpty()) {
                            System.err.println("[parse] mensagem sem tipo reconhecível offset=" + record.offset());
                            continue;
                        }

                       
                        regras.apply(parsed.get(), state, apostas);
                        apostas.printOdds();

    
                        String jsonOdds = mapper.writeValueAsString(apostas);

                        producer.send(
                                new ProducerRecord<>(PRODUCERTOPIC, jsonOdds),
                                (metadata, err) -> {
                                    if (err != null) {
                                        System.err.println("[odds_game] falha ao enviar: " + err);
                                    } else {
                                        System.err.printf(
                                                "[odds_game] enviado partition=%d offset=%d%n",
                                                metadata.partition(),
                                                metadata.offset());
                                    }
                                });
                    } catch (Exception e) {
                        System.err.println("[erro] offset=" + record.offset() + ": " + e.getMessage());
                    }
                }
                producer.flush();
                System.out.flush();
            }
        }
    }

    private OddConsumerProducer() {
    }
}
