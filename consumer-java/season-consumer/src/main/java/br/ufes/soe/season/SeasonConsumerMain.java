package br.ufes.soe.season;

import java.time.Duration;
import java.util.HashSet;
import java.util.Properties;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.TimeWindows;

import br.ufes.soe.objects.MatchOutcome;
import br.ufes.soe.objects.TeamStats;
import parser.JsonSerde;

public class SeasonConsumerMain {

    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "season-consumer-service");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        StreamsBuilder builder = new StreamsBuilder();

        // Inicialização dos Serdes
        JsonSerde<MatchOutcome> matchSerde = new JsonSerde<>(MatchOutcome.class);
        JsonSerde<TeamStats> teamStatsSerde = new JsonSerde<>(TeamStats.class);
        @SuppressWarnings("unchecked")
        JsonSerde<HashSet<String>> setSerde = new JsonSerde<>((Class<HashSet<String>>) (Class<?>) HashSet.class);

        KStream<String, MatchOutcome> gameStream = builder.stream("nba_game", 
                Consumed.with(Serdes.String(), matchSerde));

        // 1. Stats Jogador: Agrega pontos por jogador
        gameStream
            .groupBy((key, value) -> value.playerName()) // Requer campo playerName
            .aggregate(() -> 0, (key, value, aggregate) -> aggregate + value.points(), // Requer campo points
                       Materialized.with(Serdes.String(), Serdes.Integer()))
            .toStream()
            .to("stats_jogador", Produced.with(Serdes.String(), Serdes.Integer()));

        // 2. Team Standings: Agrega stats do time apenas quando for FIM DE JOGO
        gameStream
            .filter((key, value) -> "FIM DE JOGO".equalsIgnoreCase(value.type())) // Requer campo type
            .groupBy((key, value) -> value.teamName())
            .aggregate(TeamStats::new, (key, value, aggregate) -> aggregate.apply(value),
                       Materialized.with(Serdes.String(), teamStatsSerde))
            .toStream()
            .to("team_standings", Produced.with(Serdes.String(), teamStatsSerde));

        // 3. Hotstreak: Jogador com 5+ pontos em 2 segundos
        KStream<String, Integer> hotStreaks = gameStream
            .groupBy((key, value) -> value.playerName())
            .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(2)))
            .aggregate(() -> 0, (key, value, aggregate) -> aggregate + value.points())
            .toStream()
            .filter((windowedKey, totalPoints) -> totalPoints >= 5)
            .selectKey((windowedKey, totalPoints) -> windowedKey.key()); // Remove o metadado da janela

        hotStreaks.to("hotstreak_player", Produced.with(Serdes.String(), Serdes.Integer()));

        // 4. Simultaneous Streak: 2+ jogadores em hotstreak no mesmo intervalo
        hotStreaks
            .groupBy((key, value) -> "simultaneous-check") // Agrupa tudo para uma mesma chave
            .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(2)))
            .aggregate(HashSet::new, 
                (key, value, set) -> { set.add(key); return set; },
                Materialized.with(Serdes.String(), setSerde))
            .toStream()
            .filter((windowedKey, players) -> players.size() >= 2)
            .mapValues((windowedKey, players) -> "Hotstreak simultâneo: " + players.toString())
            .to("simultaneous_streak");

        // Execução
        KafkaStreams streams = new KafkaStreams(builder.build(), props);
        streams.start();

        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    }
}