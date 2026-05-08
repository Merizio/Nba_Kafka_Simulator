package br.ufes.soe.derived;

import br.ufes.soe.parse.NbaMessageParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.Properties;

/**
 * Serviço consumidor + produtor: lê o tópico de eventos primitivos e republica
 * um evento derivado no tópico configurado (pipeline para inferências compostas).
 */
public final class DerivedMonitorMain {

    private static final String BOOTSTRAP = "localhost:19092";
    private static final String INPUT_TOPIC = "nba_game";
    private static final String OUTPUT_TOPIC = "nba_game_derived";
    private static final String GROUP_ID = "nba-derived-relay-grupo";

    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        NbaMessageParser parser = new NbaMessageParser(mapper);

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.ACKS_CONFIG, "1");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps);
             KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {

            consumer.subscribe(Collections.singletonList(INPUT_TOPIC));
            System.out.printf(
                    "derived-monitor: consumindo `%s`, publicando em `%s` (%s, group=%s)%n",
                    INPUT_TOPIC, OUTPUT_TOPIC, BOOTSTRAP, GROUP_ID);
            System.out.println("Ctrl+C para sair.\n");

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
                for (ConsumerRecord<String, String> record : records) {
                    String raw = record.value();
                    if (raw == null) {
                        continue;
                    }
                    JsonNode tree = mapper.readTree(raw);
                    Optional<String> tipoPrimitive = Optional.of(tree.path("tipo").asText("")).filter(s -> !s.isEmpty());

                    ObjectNode derived = mapper.createObjectNode();
                    derived.put("tipo", "EVENTO_DERIVADO");
                    derived.put("schemaVersion", 1);
                    derived.put("timestampIso", Instant.now().toString());
                    derived.put("fonteTopico", INPUT_TOPIC);
                    derived.put("particaoOrigem", record.partition());
                    derived.put("offsetOrigem", record.offset());
                    tipoPrimitive.ifPresent(t -> derived.put("tipoPrimitivo", t));

                    parser.toEvent(tree).ifPresent(ev -> derived.put("eventoParseado", ev.getClass().getSimpleName()));

                    String payload = mapper.writeValueAsString(derived);
                    producer.send(new ProducerRecord<>(OUTPUT_TOPIC, record.key(), payload));
                    System.out.printf(
                            "derivado enviado partition=%d offset=%s -> %s%n",
                            record.partition(),
                            record.offset(),
                            tipoPrimitive.orElse("?"));
                }
                producer.flush();
            }
        }
    }

    private DerivedMonitorMain() {
    }
}
