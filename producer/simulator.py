import manipulator
import pandas as pd
import random
import time as tempo
import json
import sys
from confluent_kafka import Producer

"""
SIMULADOR DE UM JOGO DA NBA

UMA SIMULAÇÃO DE 2 TIMES ALEATORIOS EM UMA PARTIDA AOS MOLDES TRADICIONAIS DA NBA 
"""

#CONFIGURAÇÃO DO PRODUTOR KAFKA
def delivery_callback(err, msg):
        if err:
            sys.stderr.write('%% Erro no envio da mensagem: %s\n' % err)
        else:
            sys.stderr.write('%% Messagem enviada para %s [%d] @ %d\n' % (msg.topic(), msg.partition(), msg.offset()))

topic= 'nba_game'
cfg = {
    'bootstrap.servers': 'localhost:19092'
}
prod = Producer(cfg)

#ESCOLHA DOS TIMES PARA O SIMULADOR
csv=pd.read_csv("./data/list_nba_players.csv")

csv = csv.drop(columns=csv.columns[0])

time_A, time_B = manipulator.escolher_times(csv)

manipulator.exibir_partida(time_A, time_B)

key = f"Game_{random.randrange(100,199)}"

#CRIANDO JSON
match = {
    'tipo': "INICIO",
    'match': f"{time_A.nome} X {time_B.nome}",
    time_A.nome:{
        'titulares': time_A.exibir_titulares(),
        'reservas': time_A.exibir_resevas()
    },
    time_B.nome:{
        'titulares': time_B.exibir_titulares(),
        'reservas': time_B.exibir_resevas()
    }
}

match_event = json.dumps(match, ensure_ascii=False)
prod.produce(topic=topic, value=match_event.encode("utf-8"), key=key, callback=delivery_callback)
prod.poll(0)

print(match_event) #PRODUCE EVENT

time_ataque, time_defesa = time_A, time_B
#COMEÇAR O JOGO
print("\nComeça a Partida")
for i in range(4):    
    print(f"\n\nInício do {i+1}o quarto!  {time_A.nome} {time_A.pontos} X {time_B.pontos} {time_B.nome}")
    posses = random.randint(60, 80)

    for n in range(posses):
        model = {
            'tipo': "EVENTO",
            'time': time_ataque.nome,
            'quarto': i+1,
            'placar': f"{time_A.nome} {time_A.pontos} X {time_B.pontos} {time_B.nome}",
            'detalhes': {}
        }
        event = model.copy()

        #ALEATORIZACAO DA JOGADA
        jogada = random.random()

        #SUBTITUICAO
        if(0.236<jogada<0.316):
            jogador1 = manipulator.escolher_jogador(time_ataque.titulares, 'min')
            jogador2 = manipulator.escolher_jogador(time_ataque.reservas, 'max')
            #print(f"Mudança do {time_ataque.nome}: Sai {jogador1.Player} e entra {jogador2.Player}!")
            
            time_ataque.titulares.remove(jogador1)
            time_ataque.reservas.remove(jogador2)
            time_ataque.titulares.append(jogador2)
            time_ataque.reservas.append(jogador1)

            event_subs=model.copy()
            event_subs['detalhes'] = {
                'ação': "SUBSTITUTION",
                'jogador_out': jogador1.Player,
                'jogador_in': jogador2.Player,
            }
            event_subs_json = json.dumps(event_subs, ensure_ascii=False)
            prod.produce(topic=topic, value=event_subs_json.encode("utf-8"), key=key, callback=delivery_callback)
            prod.poll(0)
            print(event_subs_json)

        #TURNOVER
        if(jogada<0.136):
            jogador = manipulator.escolher_jogador(time_ataque.titulares, 'max')
            #print(f"O jogador {jogador.Player} do {time_ataque.nome} acaba de cometer um Turnover!")
            event['detalhes'] = {
                'ação': "TURNOVER",
                'jogador': jogador.Player,
            }
        
        #FALTA
        elif(0.136<jogada<0.236):
            jogador1 = manipulator.escolher_jogador(time_ataque.titulares, 'equal')
            jogador2 = manipulator.escolher_jogador(time_defesa.titulares, 'equal')
            #print(f"Falta cometida pelo {jogador1.Player} no {jogador2.Player}! Bola pro {time_defesa.nome}!")
            event['detalhes'] = {
                'ação': "FOUL",
                'jogador_commit': jogador1.Player,
                'jogador_receive': jogador2.Player,
            }

        #TENTATIVA DE ARREMESSO
        else:
            jogador = manipulator.escolher_jogador(time_ataque.titulares, 'max')
            chance = random.random()
            if(chance<0.585):
                #2 PONTOS
                chance = random.random()
                if(chance <= jogador.FG):
                    #print(f"Jogador {jogador.Player} do {time_ataque.nome} fez uma cesta de 2 Pontos!")
                    time_ataque.pontos+=2
                    jogador.pts+=2
                    event['detalhes'] = {
                        'ação': "POINT",
                        'jogador': jogador.Player,
                        'valor': 2
                    }
            else:
                #3 PONTOS
                chance = random.random()
                if(chance <= jogador.P3):
                    #print(f"Jogador {jogador.Player} do {time_ataque.nome} fez uma cesta de 3 Pontos!")
                    time_ataque.pontos+=3
                    jogador.pts+=3
                    event['detalhes'] = {
                        'ação': "POINT",
                        'jogador': jogador.Player,
                        'valor': 3
                    }

        #INVERSAO DA POSSE
        time_ataque, time_defesa = time_defesa, time_ataque

        if(event!=model):
            event_json = json.dumps(event, ensure_ascii=False)
            prod.produce(topic=topic, value=event_json.encode("utf-8"), key=key, callback=delivery_callback)
            prod.poll(0)
            print(event_json)

        #IDEIA DE TEMPO REAL
        tempo.sleep(0.1)

#CRIANDO JSON
match_end = {
    'tipo': "FINAL",
    'match': f"{time_A.nome} {time_A.pontos} X {time_B.pontos} {time_B.nome}",
}

match_event_end = json.dumps(match_end, ensure_ascii=False)
prod.produce(topic=topic, value=match_event_end.encode("utf-8"), key=key, callback=delivery_callback)
prod.poll(0)

print(match_event_end) #PRODUCE EVENT

prod.flush()

#STATUS FINAL DA PARTIDA
print("\nFinal de Partida!\nEstatísticas Finais:\n")
manipulator.exibir_partida(time_A, time_B)