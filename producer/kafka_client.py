"""Configuração e envio de mensagens no Kafka (produtor)."""
from __future__ import annotations

import json
import sys
from typing import Any, Callable, Mapping

from confluent_kafka import Producer

BOOTSTRAP_SERVERS = "localhost:19092"
TOPIC_DEFAULT = "nba_game"


def delivery_callback(err, msg) -> None:
    if err:
        sys.stderr.write("%% Erro no envio da mensagem: %s\n" % err)
    else:
        sys.stderr.write(
            "%% Mensagem enviada para %s [%d] @ %d\n"
            % (msg.topic(), msg.partition(), msg.offset())
        )


def create_producer(
    bootstrap_servers: str = BOOTSTRAP_SERVERS,
    extra_config: Mapping[str, Any] | None = None,
) -> Producer:
    cfg: dict[str, Any] = {"bootstrap.servers": bootstrap_servers}
    if extra_config:
        cfg.update(extra_config)
    return Producer(cfg)


def produce_json(
    producer: Producer,
    topic: str,
    payload: dict[str, Any],
    callback: Callable[..., None] | None = None,
) -> None:
    """Serializa o payload em JSON UTF-8 e envia para o tópico."""
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    producer.produce(topic=topic, value=body, callback=callback or delivery_callback)
    producer.poll(0)
