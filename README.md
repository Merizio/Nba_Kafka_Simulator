# Kafka Lab (3 brokers) - Como rodar

## Requisitos
- Docker e Docker Compose instalados.
- Portas livres: `19092`, `29092`, `39092`.

## Subir o cluster
No diretório onde está o `compose.yaml`, execute:

```bash
docker compose up -d
```

Verifique se os 3 containers estão no ar:

```bash
docker compose ps
```

## Criar tópicos de execução
1. Criar tópico de jogo:

```bash
docker exec -it kafka-1 /opt/kafka/bin/kafka-topics.sh \
  --create --topic nba_game \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 3
```

2. Criar tópico de Odds:
```bash
docker exec -it kafka-1 /opt/kafka/bin/kafka-topics.sh \
  --create --topic odds_game \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 3
```

3. Consumidor (em um terminal):

Rodar o NbaGameConsumer

4. Consumidor/Produtor (em um terminal):

Rodar o OddConsumerProducer

5. Produtor (em outro terminal):

```bash
python -m producer
```

## Parar o ambiente
```bash
docker compose down
```

Para remover também os volumes (dados):

```bash
docker compose down -v
```