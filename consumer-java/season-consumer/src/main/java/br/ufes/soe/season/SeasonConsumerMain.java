package br.ufes.soe.season;

import java.util.List;
import java.util.Optional;
import java.util.Properties;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;

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

    private static final String BOOTSTRAP = "localhost:19092";
    private static final String TOPIC = "nba_game";
    private static final String GROUP_ID = "season-consumer-grupo";

    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "odd-streams");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:19092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, parser.NbaEventSerde.class);

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
             * FALTA FAZER
             * 
             * HOTSTREAK DE JOGADOR COM WINDOW
             * 
             * ALGEBRA DE ALLEN PARA HOTSTREAKS SIMULTÂNEAS
             */






                    //RAMO CHAVE = JOGADOR
        KStream<String, MatchPlayEvent> stream_jogador = stream_jogadas
            .filter((key, playEvent) -> playEvent.action() instanceof PlayAction.Point)
            .selectKey((key, playEvent) -> {

                PlayAction.Point lanceDePonto = (PlayAction.Point) playEvent.action();
                Player jogador = lanceDePonto.player();
                
                return jogador.getName();
            });

            //RAMO CHAVE = TIME
        KStream<String, MatchPlayEvent> stream_equipe = stream_jogadas
            .selectKey((key, playEvent) -> {
                return playEvent.teamName();
            });

        


        System.err.printf(
                "season-consumer pronto para implementação (topic=%s, bootstrap=%s, group=%s)%n",
                TOPIC, BOOTSTRAP, GROUP_ID);
    }

    private SeasonConsumerMain() {
    }
}


/*

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
            NbaPrimitiveEvent evento = (NbaPrimitiveEvent) opt.get();
            Odds apostas = new Odds();
            
            try {
                regras.apply(evento, state, apostas);
            } catch (Exception e) {
                System.err.println("Erro ao aplicar regras para a chave " + key + ": " + e.getMessage());
            }
            
            try {
                String jsonApostas = mapper.writeValueAsString(apostas);
                return jsonApostas;
            } catch (JsonProcessingException e) {
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
        } catch (IllegalStateException | InterruptedException | StreamsException e) {
            System.exit(1);
        }
        System.exit(0);
    }

}
*/