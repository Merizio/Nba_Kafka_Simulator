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

## Teste rápido (criar tópico, produzir e consumir)
1. Criar um tópico:

```bash
docker exec -it kafka-1 /opt/kafka/bin/kafka-topics.sh \
  --create --topic lab-topico --partitions 3 --replication-factor 3 \
  --bootstrap-server localhost:9092
```

2. Consumidor (em um terminal):

```bash
docker exec -it kafka-1 /opt/kafka/bin/kafka-console-consumer.sh \
  --topic lab-topico --from-beginning --bootstrap-server localhost:9092
```

3. Produtor (em outro terminal):

```bash
docker exec -it kafka-1 /opt/kafka/bin/kafka-console-producer.sh \
  --topic lab-topico --bootstrap-server localhost:9092
```

Digite mensagens no produtor e veja chegando no consumidor.

## Bootstrap servers para clientes externos
- `localhost:19092`
- `localhost:29092`
- `localhost:39092`

## Parar o ambiente
```bash
docker compose down
```

Para remover também os volumes (dados):

```bash
docker compose down -v
```
