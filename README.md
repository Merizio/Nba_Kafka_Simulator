# Sistemas orientados a eventos — simulação NBA com Apache Kafka

Pipeline de eventos: produtor Python simula **temporada** (rodadas e partidas em paralelo) e publica em `nba_game`; serviços Java consomem/processam com **Kafka Consumer** e **Kafka Streams**, gerando tópicos derivados (odds, estatísticas, hot streak).

## Requisitos

- **Docker** e **Docker Compose**
- **JDK 17+** e **Apache Maven 3.8+**
- **Python 3.10+** (ambiente virtual em `producer/.venv`)

Portas livres no host: **`19092`**, **`29092`**, **`39092`**.

---

## Arquitetura (resumo)

| Componente | Tecnologia | Entrada | Saída |
|------------|------------|---------|--------|
| `producer/simulator.py` | Python + confluent-kafka | CSV NBA | `nba_game` |
| `NbaGameConsumer` | Kafka Consumer | `nba_game` | painel no terminal |
| `OddConsumerProducer` | Kafka Streams (`odd-streams`) | `nba_game` | `odds_game` |
| `SeasonConsumerMain` | Kafka Streams (`season-stream-v3`) | `nba_game` | `hotstreak_player` (+ pipelines comentadas) |

Bootstrap para clientes na máquina host: **`localhost:19092`**.

---

## Tópicos

### `nba_game` (entrada principal)

| `tipo` no JSON | Descrição |
|----------------|-----------|
| `INICIO` | início de partida (elencos) |
| `EVENTO` | lance (ponto, falta, turnover, substituição) |
| `FINAL` | fim de partida (placar) |
| `RODADA_INICIO` | início da rodada (`rodada`: número) |
| `RODADA_FIM` | fim da rodada |
| `SEASON_END` | fim da temporada |

### `odds_game`

Snapshot JSON de odds por partida (`teamA`, `teamB`, `oddsA`, `oddsB`).

### Tópicos derivados (`season-consumer`)

| Tópico | Status no código | Conteúdo |
|--------|------------------|----------|
| `hotstreak_player` | **ativo** | alerta quando jogador soma >5 pts em janela de 5s |
| `stats_time` | comentado | standings por time (`TeamStats`) |
| `stats_jogador` | comentado | pontos acumulados por jogador |
| `simultaneous_streaks` | comentado | contagem de hot streaks simultâneas |

Crie **todos** os tópicos abaixo antes de rodar (mesmo os ainda comentados no código, se for reativá-los).

---

## 1. Subir o cluster Kafka

Na raiz do repositório:

```bash
docker compose up -d
docker compose ps
```

---

## 2. Criar tópicos

Execute na raiz (use `--if-not-exists` para não falhar se já existirem):

```bash
for TOPIC in nba_game odds_game hotstreak_player stats_time stats_jogador simultaneous_streaks; do
  docker exec kafka-1 /opt/kafka/bin/kafka-topics.sh \
    --create --if-not-exists \
    --topic "$TOPIC" \
    --partitions 3 \
    --replication-factor 3 \
    --bootstrap-server localhost:9092
done
```

Listar tópicos:

```bash
docker exec kafka-1 /opt/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092
```

---

## 3. Ambiente Python (produtor)

```bash
cd producer
python3 -m venv .venv
source .venv/bin/activate          # Windows: .venv\Scripts\activate
pip install -r requirements.txt
cd ..
```

O simulador lê **`data/list_nba_players.csv`** na raiz do repositório. **Rode sempre a partir da raiz** (não use `python -m producer` — não há pacote Python configurado).

---

## 4. Compilar os módulos Java

O `odd-consumer-producer` e o `season-consumer` dependem do JAR do `nba-consumer`:

```bash
cd consumer-java
mvn install -DskipTests
```

---

## 5. Executar o sistema completo

Ordem sugerida: **cluster + tópicos** → **Streams (odds e season)** → **monitor** → **simulador**.

Abra **quatro terminais**:

| Terminal | Pasta | Comando |
|----------|--------|---------|
| **A** — odds | `consumer-java/` | `mvn -pl odd-consumer-producer exec:java` |
| **B** — season / stats | `consumer-java/` | `mvn -pl season-consumer exec:java` |
| **C** — placar ao vivo | `consumer-java/` | `mvn -pl nba-consumer exec:java` |
| **D** — produtor | **raiz do repo** | `./producer/run_simulator.sh` |

No simulador, entre rodadas digite **`play`** quando solicitado.

Alternativa ao script (raiz do repo, com venv ativo):

```bash
python producer/simulator.py
```

Pare cada processo Java com **Ctrl+C**.

### Observações

- **`OddConsumerProducer`** e **`SeasonConsumerMain`** são apps **Kafka Streams** distintas (`odd-streams` e `season-stream-v3`); ambas leem `nba_game` sem conflitar.
- **`NbaGameConsumer`** usa `group.id=nba-monitor-grupo` e só assina `nba_game`.
- O `season-consumer` chama `cleanUp()` ao iniciar — apaga state stores locais (útil em dev; reiniciar zera acumuladores locais).

---

## 6. Parar o ambiente

```bash
docker compose down
```

Remover volumes (dados dos brokers):

```bash
docker compose down -v
```

---

## Estrutura Maven (`consumer-java/`)

```
consumer-java/
├── nba-consumer/           # consumidor + modelo + parser + painel
├── odd-consumer-producer/  # Kafka Streams → odds_game
└── season-consumer/        # Kafka Streams → estatísticas / hot streak
```
