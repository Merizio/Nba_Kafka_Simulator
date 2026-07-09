package br.ufes.soe.season;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.WindowStore;

import br.ufes.soe.model.NbaPrimitiveEvent;
import br.ufes.soe.model.NbaPrimitiveEvent.MatchEndEvent;
import br.ufes.soe.model.NbaPrimitiveEvent.MatchPlayEvent;
import br.ufes.soe.model.PlayAction;
import br.ufes.soe.objects.MatchOutcome;
import br.ufes.soe.objects.TeamStats;
import parser.JsonSerde;
import parser.NbaEventSerde;

/**
 * Kafka Streams: agrega eventos de {@code nba_game} e publica tópicos derivados de estatísticas.
 */
public final class SeasonConsumerMain {

    private static final String INPUT_TOPIC = "nba_game";

    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "season-stream-v4");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:19092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, NbaEventSerde.class);
        props.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG, 1);
        props.put(StreamsConfig.consumerPrefix(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG), "earliest");
        props.put(
                StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
                org.apache.kafka.streams.errors.LogAndContinueExceptionHandler.class);
        props.put(StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, 0);
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 100);
        props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.AT_LEAST_ONCE);

        final StreamsBuilder builder = new StreamsBuilder();

        JsonSerde<MatchOutcome> matchOutcomeSerde = new JsonSerde<>(MatchOutcome.class);
        JsonSerde<TeamStats> teamStatsSerde = new JsonSerde<>(TeamStats.class);

        KStream<String, Optional<NbaPrimitiveEvent>> stream = builder.stream(
                INPUT_TOPIC,
                Consumed.with(Serdes.String(), new NbaEventSerde()));

        KStream<String, MatchPlayEvent> streamJogadas = stream
                .filter((key, opt) -> opt.isPresent() && opt.get() instanceof MatchPlayEvent)
                .mapValues(opt -> (MatchPlayEvent) opt.get());

        // Pontos acumulados por jogador → stats_jogador (key=nome, value=total pts)
        KStream<String, Integer> pontosPorJogador = streamJogadas
                .filter((key, play) -> play.action() instanceof PlayAction.Point)
                .map((key, play) -> {
                    PlayAction.Point point = (PlayAction.Point) play.action();
                    return KeyValue.pair(point.player().getName(), point.pointsValue());
                });

        pontosPorJogador
                .groupByKey(Grouped.with(Serdes.String(), Serdes.Integer()))
                .aggregate(
                        () -> 0,
                        (player, points, total) -> total + points,
                        Materialized.<String, Integer, KeyValueStore<Bytes, byte[]>>as("player-points-store")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(Serdes.Integer()))
                .toStream()
                .peek((player, total) -> System.out.printf("[stats_jogador] %s → %d pts%n", player, total))
                .to("stats_jogador", Produced.with(Serdes.String(), Serdes.Integer()));

        // Standings por time → stats_time
        KStream<String, MatchOutcome> streamResultados = stream
                .filter((key, opt) -> opt.isPresent() && opt.get() instanceof MatchEndEvent)
                .mapValues(opt -> (MatchEndEvent) opt.get())
                .flatMap((key, endEvent) -> parseMatchOutcomes(endEvent.finalScoreboard()));

        streamResultados
                .groupByKey(Grouped.with(Serdes.String(), matchOutcomeSerde))
                .aggregate(
                        TeamStats::new,
                        (teamName, outcome, stats) -> stats.apply(outcome),
                        Materialized.<String, TeamStats, KeyValueStore<Bytes, byte[]>>as("standings-store")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(teamStatsSerde))
                .toStream()
                .peek((team, stats) -> System.out.printf("[stats_time] %s → %s%n", team, stats))
                .to("stats_time", Produced.with(Serdes.String(), teamStatsSerde));

        // Hot streak por janela → hotstreak_player
        KStream<String, Integer> pontosParaStreak = streamJogadas
                .filter((key, play) -> play.action() instanceof PlayAction.Point)
                .map((key, play) -> {
                    PlayAction.Point point = (PlayAction.Point) play.action();
                    return KeyValue.pair(point.player().getName(), point.pointsValue());
                });

        pontosParaStreak
                .groupByKey(Grouped.with(Serdes.String(), Serdes.Integer()))
                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(5)))
                .aggregate(
                        () -> 0,
                        (player, points, total) -> total + points,
                        Materialized.<String, Integer, WindowStore<Bytes, byte[]>>as("hot-streak-store")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(Serdes.Integer()))
                .toStream()
                .filter((windowedKey, total) -> total > 2)
                .map((Windowed<String> windowedKey, Integer total) -> {
                    String player = windowedKey.key();
                    String msg = String.format(
                            "O %s está pegando fogo! Fez %d pontos em sequência!",
                            player, total);
                    return KeyValue.pair(player, msg);
                })
                .peek((player, msg) -> System.out.printf("[hotstreak_player] %s%n", msg))
                .to("hotstreak_player", Produced.with(Serdes.String(), Serdes.String()));

        // Streaks simultâneas → simultaneous_streaks
        builder.stream("hotstreak_player", Consumed.with(Serdes.String(), Serdes.String()))
                .selectKey((key, value) -> "HOTSTREAK")
                .groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(2)))
                .count(Materialized.<String, Long, WindowStore<Bytes, byte[]>>as("overlap_count")
                        .withKeySerde(Serdes.String())
                        .withValueSerde(Serdes.Long()))
                .toStream()
                .filter((windowedKey, count) -> count >= 2)
                .map((Windowed<String> windowedKey, Long count) -> KeyValue.pair(windowedKey.key(), count))
                .peek((key, count) -> System.out.printf("[simultaneous_streaks] %d jogadores%n", count))
                .to("simultaneous_streaks", Produced.with(Serdes.String(), Serdes.Long()));

        Topology topology = builder.build();
        System.out.println(topology.describe());

        final KafkaStreams streams = new KafkaStreams(topology, props);

        System.out.println("Limpando state stores locais (RocksDB)...");
        streams.cleanUp();

        final CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
            @Override
            public void run() {
                streams.close();
                latch.countDown();
            }
        });

        streams.setUncaughtExceptionHandler(exception -> {
            System.err.println("Kafka Streams encerrou com erro:");
            exception.printStackTrace();
            return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.SHUTDOWN_CLIENT;
        });

        try {
            System.out.println("Iniciando season-consumer (Kafka Streams)...");
            streams.start();
            latch.await();
        } catch (IllegalStateException | InterruptedException e) {
            System.exit(1);
        }
        System.exit(0);
    }

    /** Ex.: {@code "IND 103 X 101 BRK"} → dois {@link MatchOutcome}. */
    private static List<KeyValue<String, MatchOutcome>> parseMatchOutcomes(String placarLinha) {
        List<KeyValue<String, MatchOutcome>> out = new ArrayList<>();
        if (placarLinha == null || placarLinha.isBlank()) {
            return out;
        }
        try {
            String[] partes = placarLinha.split(" X ");
            if (partes.length != 2) {
                return out;
            }
            String[] timeAInfo = partes[0].trim().split("\\s+");
            String[] timeBInfo = partes[1].trim().split("\\s+");
            if (timeAInfo.length < 2 || timeBInfo.length < 2) {
                return out;
            }
            String nomeA = timeAInfo[0];
            int scoreA = Integer.parseInt(timeAInfo[1]);
            int scoreB = Integer.parseInt(timeBInfo[0]);
            String nomeB = timeBInfo[1];

            out.add(KeyValue.pair(nomeA, new MatchOutcome(nomeA, scoreA, scoreB, scoreA > scoreB)));
            out.add(KeyValue.pair(nomeB, new MatchOutcome(nomeB, scoreB, scoreA, scoreB > scoreA)));
        } catch (NumberFormatException ex) {
            System.err.printf("[stats_time] placar inválido: '%s'%n", placarLinha);
        }
        return out;
    }

    private SeasonConsumerMain() {
    }
}
