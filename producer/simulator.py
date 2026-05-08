"""
Simulador de partida NBA — produz eventos JSON no tópico Kafka configurado em kafka_client.
"""
from __future__ import annotations

import json
import random
import time as tempo
from pathlib import Path

import pandas as pd

from . import manipulator
from .kafka_client import TOPIC_DEFAULT, create_producer, delivery_callback, produce_json

_REPO_ROOT = Path(__file__).resolve().parent.parent
_DATA_CSV = _REPO_ROOT / "data" / "list_nba_players.csv"


def main() -> None:
    topic = TOPIC_DEFAULT
    prod = create_producer()

    csv = pd.read_csv(_DATA_CSV)
    csv = csv.drop(columns=csv.columns[0])

    time_A, time_B = manipulator.escolher_times(csv)
    manipulator.exibir_partida(time_A, time_B)

    match = {
        "tipo": "INICIO",
        "match": f"{time_A.nome} X {time_B.nome}",
        time_A.nome: {
            "titulares": time_A.exibir_titulares(),
            "reservas": time_A.exibir_resevas(),
        },
        time_B.nome: {
            "titulares": time_B.exibir_titulares(),
            "reservas": time_B.exibir_resevas(),
        },
    }

    produce_json(prod, topic, match, delivery_callback)
    print(json.dumps(match, ensure_ascii=False))

    time_ataque, time_defesa = time_A, time_B
    print("\nComeça a Partida")
    for i in range(4):
        print(
            f"\n\nInício do {i + 1}o quarto!  "
            f"{time_A.nome} {time_A.pontos} X {time_B.pontos} {time_B.nome}"
        )
        posses = random.randint(60, 80)

        for _ in range(posses):
            model = {
                "tipo": "EVENTO",
                "time": time_ataque.nome,
                "quarto": i + 1,
                "placar": f"{time_A.nome} {time_A.pontos} X {time_B.pontos} {time_B.nome}",
                "detalhes": {},
            }
            event = model.copy()

            jogada = random.random()

            if 0.236 < jogada < 0.316:
                jogador1 = manipulator.escolher_jogador(time_ataque.titulares, "min")
                jogador2 = manipulator.escolher_jogador(time_ataque.reservas, "max")

                time_ataque.titulares.remove(jogador1)
                time_ataque.reservas.remove(jogador2)
                time_ataque.titulares.append(jogador2)
                time_ataque.reservas.append(jogador1)

                event_subs = model.copy()
                event_subs["detalhes"] = {
                    "ação": "SUBSTITUTION",
                    "jogador_out": jogador1.Player,
                    "jogador_in": jogador2.Player,
                }
                produce_json(prod, topic, event_subs, delivery_callback)
                print(event_subs)

            if jogada < 0.136:
                jogador = manipulator.escolher_jogador(time_ataque.titulares, "max")
                event["detalhes"] = {
                    "ação": "TURNOVER",
                    "jogador": jogador.Player,
                }

            elif 0.136 < jogada < 0.236:
                jogador1 = manipulator.escolher_jogador(time_ataque.titulares, "equal")
                jogador2 = manipulator.escolher_jogador(time_defesa.titulares, "equal")
                event["detalhes"] = {
                    "ação": "FOUL",
                    "jogador_commit": jogador1.Player,
                    "jogador_receive": jogador2.Player,
                }

            else:
                jogador = manipulator.escolher_jogador(time_ataque.titulares, "max")
                chance = random.random()
                if chance < 0.585:
                    chance = random.random()
                    if chance <= jogador.FG:
                        time_ataque.pontos += 2
                        jogador.pts += 2
                        event["detalhes"] = {
                            "ação": "POINT",
                            "jogador": jogador.Player,
                            "valor": 2,
                        }
                else:
                    chance = random.random()
                    if chance <= jogador.P3:
                        time_ataque.pontos += 3
                        jogador.pts += 3
                        event["detalhes"] = {
                            "ação": "POINT",
                            "jogador": jogador.Player,
                            "valor": 3,
                        }

            time_ataque, time_defesa = time_defesa, time_ataque

            if event != model:
                produce_json(prod, topic, event, delivery_callback)
                print(event)

            tempo.sleep(0.1)

    prod.flush()

    print("\nFinal de Partida!\nEstatísticas Finais:\n")
    manipulator.exibir_partida(time_A, time_B)
