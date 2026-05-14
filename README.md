# Sistemas orientados a eventos — cluster Kafka e pipeline NBA

Simulação de partidas da NBA com **Apache Kafka** (3 brokers): produtor em Python publica em `nba_game`; o serviço Java **OddConsumerProducer** lê esses eventos e publica snapshots em `odds_game`; o **NbaGameConsumer** acompanha os dois tópicos e atualiza o painel no terminal.

## Requisitos

- **Docker** e **Docker Compose**
- **JDK 17+** e **Apache Maven 3.8+**
- **Python 3.10+** (recomendado usar o ambiente virtual abaixo)

Portas livres no host: **`19092`**, **`29092`**, **`39092`** (clientes externos costumam usar `localhost:19092`).

---

## 1. Subir o cluster Kafka

Na raiz do repositório (onde está o `compose.yaml`):

```bash
docker compose up -d
docker compose ps
```

---

## 2. Criar os tópicos da aplicação

Os brokers já definem `num.partitions=3` e `default.replication.factor=3`. Crie **`nba_game`** e **`odds_game`** (execute uma vez; `--if-not-exists` evita erro se já existirem):

```bash
docker exec kafka-1 /opt/kafka/bin/kafka-topics.sh \
  --create --if-not-exists \
  --topic nba_game --partitions 3 --replication-factor 3 \
  --bootstrap-server localhost:9092

docker exec kafka-1 /opt/kafka/bin/kafka-topics.sh \
  --create --if-not-exists \
  --topic odds_game --partitions 3 --replication-factor 3 \
  --bootstrap-server localhost:9092
```

Listar tópicos:

```bash
docker exec kafka-1 /opt/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092
```

---

## 3. Ambiente Python (produtor)

Na pasta `producer/`:

```bash
cd producer
python3 -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
```

O simulador usa o CSV **`data/list_nba_players.csv`** na **raiz do repositório** (caminho resolvido pelo próprio `simulator.py`).

**Importante:** não use o `python3` do sistema sem o venv — faltam `pandas` e `confluent-kafka`. Com o venv já criado, da **raiz do repositório** você pode rodar:

```bash
./producer/run_simulator.sh
```

---

## 4. Compilar e instalar os módulos Maven

O módulo `odd-consumer-producer` depende do JAR do **`nba-consumer`**. Na pasta `consumer-java/`, instale no repositório local (faça isso após clonar ou ao mudar o código Java):

```bash
cd consumer-java
mvn install -DskipTests
```

---

## 5. Executar o sistema completo (três terminais)

Ordem sugerida: **cluster e tópicos prontos** → **pipeline de odds** → **monitor** → **simulador**.

| Terminal                         | Pasta               | Comando                                                                                                |
| -------------------------------- | ------------------- | ------------------------------------------------------------------------------------------------------ |
| **A** — pipeline odds            | `consumer-java/`    | `mvn -pl odd-consumer-producer exec:java`                                                              |
| **B** — monitor + odds no painel | `consumer-java/`    | `mvn -pl nba-consumer exec:java`                                                                       |
| **C** — simulador                | raiz ou `producer/` | `./producer/run_simulator.sh` **ou** `cd producer && source .venv/bin/activate && python simulator.py` |

- O **OddConsumerProducer** usa `group.id=odds-pipeline-grupo` e publica em `odds_game`.
- O **NbaGameConsumer** usa `group.id=nba-monitor-grupo` e consome `nba_game` e `odds_game`.

Pare com **Ctrl+C** em cada processo Java; o simulador encerra ao fim da partida.

---

## 6. Bootstrap para clientes na máquina host

Use qualquer um dos três listeners mapeados para o host, por exemplo:

`localhost:19092`

---

## 7. Parar o ambiente Docker

```bash
docker compose down
```

Para apagar também volumes (dados dos brokers):

```bash
docker compose down -v
```

---

## Teste rápido só com Kafka (console)

Exemplo com um tópico de laboratório `lab-topico`:

```bash
docker exec -it kafka-1 /opt/kafka/bin/kafka-topics.sh \
  --create --topic lab-topico --partitions 3 --replication-factor 3 \
  --bootstrap-server localhost:9092

docker exec -it kafka-1 /opt/kafka/bin/kafka-console-consumer.sh \
  --topic lab-topico --from-beginning --bootstrap-server localhost:9092
```

Em outro terminal, produtor console no mesmo tópico.

---

## Opcional: relay de eventos derivados (`DerivedMonitorMain`)

O módulo `odd-consumer-producer` também contém `br.ufes.soe.derived.DerivedMonitorMain` (tópico de saída `nba_game_derived`), que **não** faz parte do fluxo principal odds acima. Para executá-lo sobrescreva a classe principal:

```bash
cd consumer-java
mvn -pl odd-consumer-producer exec:java -Dexec.mainClass=br.ufes.soe.derived.DerivedMonitorMain
```

(Crie o tópico `nba_game_derived` antes, se ainda não existir.)
