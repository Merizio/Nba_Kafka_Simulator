# (PARA APAGAR DEPOIS)
## TOPICOS DE ENTRADA
### TOPICO: nba_game
  tipos:
  - INICIO
  - EVENTO
  - FINAL
  - RODADA_INICIO (NOVO)
  - RODADA_FIM (NOVO)
  - SEASON_END (NOVO)

Os novos são apenas para controle e visualização no dashboard, **a rodada_inicio tem a sinalização do numero da rodada, que será importante em um tópico futuro**

### TOPICO: odds_game
  Evento se manteve inalterado

### TOPICO: stats_jogador (NOVO)
  key:
  - Nome do Jogador
  
  value:
  - Pontuação

A atualização de pontos/jogo vai ser em tempo real, então a média é a (pontuação/nº Rodada)

### TOPICO: team_standings (NOVO)
  key:
  - nome do time
  
  value:
  - win
  - lose
  - points_made
  - points_take

O princípio igual o stats jogador, média em relação ao nº da rodada

### TOPICO: hotstreak_player (NOVO)
  key:
  - nome do jogador
  
  value:
  - mensagem "O XXXX está pegando fogo! Fez YY pontos em sequência!"

### TOPICO: simultaneous_streak (NOVO)
  key:
  - "HOTSTREAK" (const)
  
  value:
  - qtd de jogadores em streak no momento


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
