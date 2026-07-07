package br.ufes.soe.derived;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

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
import br.ufes.soe.derived.parser.NbaEventDeserializer;
import br.ufes.soe.derived.parser.NbaEventSerde;
import br.ufes.soe.derived.rules.OddsControlRules;
import br.ufes.soe.model.MatchState;
import br.ufes.soe.model.NbaPrimitiveEvent;
import br.ufes.soe.parse.NbaMessageParser;
import br.ufes.soe.rules.GameMonitoringRules;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;

import java.util.Optional;



/**
 * Orquestra o cliente Kafka: assina o tópico, faz poll e delega parse ({@link NbaMessageParser})
 * e regras ({@link GameMonitoringRules}).
 */

public final class OddConsumerProducer {
    public static void main(String[] args) throws Exception {

        ObjectMapper mapper = new ObjectMapper();

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "odd-streams");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:19092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, NbaEventSerde.class);

        OddsControlRules regras = new OddsControlRules();
        MatchState state = new MatchState();


        final StreamsBuilder builder = new StreamsBuilder();

        builder.stream("nba_game",Consumed.with(Serdes.String(), new NbaEventSerde()))
        //.peek((key, value)->System.out.println(value))
        .filter((key, opt) -> {
             String nomeClasse = opt.get().getClass().getSimpleName();
            // Permite estritamente a classe que gerencia os eventos de jogadas
            return "MatchPlayEvent".equals(nomeClasse);
        })
        .mapValues((key, opt) -> {
            NbaPrimitiveEvent evento = opt.get();
            Odds apostas = new Odds();
            
            try {
                regras.apply(evento, state, apostas);
            } catch (Exception e) {
                System.err.println("Erro ao aplicar regras para a chave " + key + ": " + e.getMessage());
            }
            
            try {
                String jsonApostas = mapper.writeValueAsString(apostas);
                return jsonApostas;
            } catch (Exception e) {
                System.err.println("Erro ao processar Json Saida");
                return "{}";
            }
        })
        .peek((key, evento)->System.out.println(evento))
        .to("odds_game",Produced.with(
            Serdes.String(),
            Serdes.String()
            ));



 
        //para conseguir inspecionar a topologia, podemos criar um objeto do tipo Topology
        final Topology topology = builder.build();

        //printa essa topologia construída
        System.out.println(topology.describe());

        //vamos criar agora uma aplicação kafka streams com essa topologia e propriedades
        final KafkaStreams streams = new KafkaStreams(topology, props);

        //questões de threads em java. necessário para a comunicação entre instâncias da aplicação de streams
        final CountDownLatch latch = new CountDownLatch(1);

        // attach shutdown handler to catch control-c
        Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
            @Override
            public void run() {
                streams.close();
                latch.countDown();
            }
        });

        try {
            //inicia a aplicação de streams, com a topologia definida
            streams.start();
            //ela fica executando, até que seja explicitamente interrompida
            latch.await();
        } catch (Throwable e) {
            System.exit(1);
        }
        System.exit(0);
    }

}