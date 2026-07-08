package br.ufes.soe.season;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.errors.StreamsException;
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
import br.ufes.soe.model.NbaPrimitiveEvent.MatchPlayEvent;
import br.ufes.soe.model.PlayAction;
import br.ufes.soe.model.Player;
import br.ufes.soe.objects.MatchOutcome;
import br.ufes.soe.objects.TeamStats;
/**
 * Modulo que constroi os builders de Kafka Streams
 */
public final class SeasonConsumerMain {
    public record HotStreakWindow(List<String> playersInStreak, int count) {}

    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "season-stream");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:19092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, parser.NbaEventSerde.class);
        props.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG, 3);

        final StreamsBuilder builder = new StreamsBuilder();

        Serde<MatchOutcome> matchOutcomeSerde = new parser.JsonSerde<>(MatchOutcome.class);
        Serde<TeamStats> teamStatsSerde = new parser.JsonSerde<>(TeamStats.class);

        KStream<String, Optional<NbaPrimitiveEvent>> stream = builder.stream("nba_game", Consumed.with(Serdes.String(), new parser.NbaEventSerde()));


        /**
         * CONSOME O EVENTO NBA_GAME
         * BUSCA O TIPO : EVENTO (AÇÃO)
         * ISOLA POR NOME DO JOGADOR
         * ATRIBUI A PONTUAÇÃO AO JOGADOR QUANDO FOR UM EVENTO DE PONTO
         * PRODUZ O EVENTO PARA ATUALIZAÇÃO REMOTA
         */
        KStream<String, MatchPlayEvent> stream_jogadas = stream
            .filter((key, opt) -> opt.isPresent() && "MatchPlayEvent".equals(opt.get().getClass().getSimpleName()))
            .mapValues(opt -> (MatchPlayEvent) opt.get());

        //RETORNA UM MATCHPLAYEVENT

        //TABLE QUE ENTREGA A PONTUAÇÃO TOTAL DE UM JOGADOR
        KTable<String, Integer> pontos_acumulados = stream_jogadas
            .filter((key, playEvent) -> playEvent.action() instanceof PlayAction.Point)
            .groupBy((key, playEvent) -> {
                PlayAction.Point lanceDePonto = (PlayAction.Point) playEvent.action();
                Player jogador = lanceDePonto.player();
                
                return jogador.getName();
            })
            .aggregate(
                () -> 0, // Valor inicial
                (player, playEvent, totalPoints) -> {
                    PlayAction.Point lanceDePonto = (PlayAction.Point) playEvent.action();
                    return totalPoints + lanceDePonto.pointsValue(); // Soma os pontos
                },
                Materialized.as("player-points-store") // Salva na State Store local
            );
 
        pontos_acumulados
        .toStream()
        .to(
            "stats_jogador",
            Produced.with(Serdes.String(), Serdes.Integer())
        );



        /**
         * ESPERA UM EVENTO DE FINAL DE JOGO
         * CONSTROI UM MATCH OUTCOME PARA CADA TIME DO CONFRONTO
         * ABRE EM DUAS BRANCHS COM FLATMAP
         * ATUALIZA A TABLE DO TIME
         * PRODUZ UM EVENTO DE STATS DO TIME
         */
        //RETORNA UM MATCHENDEVENT
        KStream<String, NbaPrimitiveEvent.MatchEndEvent> stream_jogadas_final = stream
            .filter((key, opt) -> opt.isPresent() && (opt.get() instanceof NbaPrimitiveEvent.MatchEndEvent))
            .mapValues(opt->(NbaPrimitiveEvent.MatchEndEvent) opt.get());
            
        //TABLE QUE ENTREGA A PONTUAÇÃO TOTAL DE UM TIME

        KStream<String, MatchOutcome> stream_resultados = stream_jogadas_final
            .flatMap((key, endEvent) -> {
                String placarLinha = endEvent.finalScoreboard(); // Ex: "IND 103 X 101 BRK"
                String[] partes = placarLinha.split(" X ");
                String[] timeAInfo = partes[0].trim().split(" "); // ["IND", "103"]
                String[] timeBInfo = partes[1].trim().split(" "); // ["101", "BRK"]

                String nomeA = timeAInfo[0];
                int scoreA = Integer.parseInt(timeAInfo[1]);
                
                String nomeB = timeBInfo[1];
                int scoreB = Integer.parseInt(timeBInfo[0]);

                // Retorna uma lista de resultados para o Kafka Streams
                return List.of(
                    KeyValue.pair(nomeA, new MatchOutcome(nomeA, scoreA, scoreB, scoreA > scoreB)),
                    KeyValue.pair(nomeB, new MatchOutcome(nomeB, scoreB, scoreA, scoreB > scoreA))
                );
            });

        KTable<String, TeamStats> team_standings = stream_resultados
            .groupByKey(Grouped.with(Serdes.String(), matchOutcomeSerde))
            .aggregate(
                () -> new TeamStats(), // Construtor do seu objeto acumulador
                (teamName, outcome, currentStats) -> {
                    return currentStats.apply(outcome); 
                },
                Materialized.<String, TeamStats, KeyValueStore<Bytes, byte[]>>as("standings-store")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(teamStatsSerde)
            );

        team_standings
            .toStream()
            .to("stats_time",
                Produced.with(Serdes.String(), teamStatsSerde)
            );




        /**
         * REUTILIZA A STREAM DE JOGADAS
         * FAZ UMA TABLE COM TIME WINDOW
         * RETORNA PARA STREAM SE A PONTUAÇÃO FOR MAIOR QUE UM TRESHOLD
         * TRANSFORMA NO EVENTO
         * PRODUZ HOTSTREAK
         */
        
        KTable<Windowed<String>,Integer> hot_streak = stream_jogadas
            .filter((key, playEvent) -> playEvent.action() instanceof PlayAction.Point)
            .groupBy((key, playEvent) -> {
                PlayAction.Point lanceDePonto = (PlayAction.Point) playEvent.action();
                Player jogador = lanceDePonto.player();
                
                return jogador.getName();
            }).windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(2))) // VALOR ALTERAVEL COMO TRESHOLD
            .aggregate(
                () -> 0, // Valor inicial
                (player, playEvent, totalPoints) -> {
                    PlayAction.Point lanceDePonto = (PlayAction.Point) playEvent.action();
                    return totalPoints + lanceDePonto.pointsValue(); // Soma os pontos
                },
                Materialized.<String, Integer, WindowStore<Bytes, byte[]>>as("hot-streak-store")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(Serdes.Integer())
            );

        hot_streak
            .toStream()
            .filter((key,value)->value > 5) // VALOR ALTERAVEL COMO TRESHOLD
            .map((Windowed<String> windowedKey, Integer pontosNaWindow) -> {
                String nomeJogador = windowedKey.key(); // Extrai o nome de dentro do objeto Windowed
                String mensagemAlerta = String.format(
                    "O %s está pegando fogo! Fez %d pontos em sequência!", 
                    nomeJogador, pontosNaWindow
                );
                return KeyValue.pair(nomeJogador, mensagemAlerta);
            })
            .to("hotstreak_player",
                Produced.with(Serdes.String(), Serdes.String())
            );


        /**
         * RECEBE TOPICO HOTSTREAK
         * COUNT EM UMA TIME WINDOW
         * SE TIVER MAIS DE UMA STREAK
         * PRODUZ EVENTO STREAK SIMULTANEA
         */
        KStream<String, String> allen = builder.stream("hotstreak_player", 
            Consumed.with(Serdes.String(), Serdes.String()));

        KStream<Windowed<String>, Long> hot_simultaneo = allen
            .selectKey((key, value) -> "HOTSTREAK")
            .groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
            .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(2)))
            .count(
            Materialized.<String, Long, WindowStore<Bytes, byte[]>>as("overlap_count")
                .withKeySerde(Serdes.String())
                .withValueSerde(Serdes.Long())
            )
            .toStream();

        hot_simultaneo
        .filter((key, value)->value>=2)
        .map((key, value)-> KeyValue.pair(key.key(), value))
        .to("simultaneous_streaks",
        Produced.with(
            Serdes.String(), 
            Serdes.Long())
        );


        

        //vamos criar agora uma aplicação kafka streams com essa topologia e propriedades
        final KafkaStreams streams = new KafkaStreams(builder.build(), props);

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
        } catch (IllegalStateException | InterruptedException | StreamsException e) {
            System.exit(1);
        }
        System.exit(0);
    }

}