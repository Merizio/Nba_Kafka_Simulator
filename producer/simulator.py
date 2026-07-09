import manipulator
import pandas as pd
import random
import time as tempo
import json
import sys
import threading
from confluent_kafka import Producer

NUM_TIMES = 6

#CONFIGURAÇÃO DO PRODUTOR KAFKA
def delivery_callback(err, msg):
        if err:
            sys.stderr.write('%% Erro no envio da mensagem: %s\n' % err)
        else:
            sys.stderr.write('%% Messagem enviada para %s [%d] @ %d\n' % (msg.topic(), msg.partition(), msg.offset()))

def producer_create():
    topic= 'nba_game'
    cfg = {
        'bootstrap.servers': 'localhost:19092'
    }
    prod = Producer(cfg)
    return prod


def reset_team(time):
    time.pontos = 0
    for jogador in (time.titulares + time.reservas):
        jogador.pts = 0
        jogador.win = False
        jogador.fouls = 0


def teams_selection():
    #ESCOLHA DOS TIMES PARA O SIMULADOR
    csv=pd.read_csv("./data/list_nba_players.csv")
    csv = csv.drop(columns=csv.columns[0])

    teams = manipulator.escolher_times_season(csv, NUM_TIMES)
    matches = manipulator.gerar_confrontos(NUM_TIMES)

    return teams, matches

def simulator_match(time_A, time_B, prod=None):
    topic= "nba_game"
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

    key = f"Game_{random.randrange(100,999)}"
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


def season_maker():
    times_season, matches_season = teams_selection()

    prod = producer_create()
    topic = "nba_game"

    season_start = {
        'tipo': 'SEASON_START',
        'times': [time.nome for time in times_season]
    }
    season_start_json = json.dumps(season_start, ensure_ascii=False)
    prod.produce(topic=topic, value=season_start_json.encode("utf-8"), callback=delivery_callback)
    prod.poll(0)
    print(season_start_json)
    prod.flush()

    #simulator_match(times_season[0], times_season[1])
    for time in times_season:
        time.exibir_time()
    #print(matches_season)

    ##INICIO DA TEMPORADA
    for rodada in range(matches_season.shape[0]):

        #CONTROLE DO INIÍCIO DAS RODADAS
        while 1:
            if input("\n[SERVIDOR]: Digite play para iniciar a simulação!: ")=="play":
                break

        print("INICIANDO A RODADA:", rodada+1)

            #CRIANDO JSON
        round_caller = {
            'tipo': "RODADA_INICIO",
            'rodada': rodada+1
        }
        round_caller_json = json.dumps(round_caller, ensure_ascii=False)
        prod.produce(topic=topic, value=round_caller_json.encode("utf-8"), callback=delivery_callback)
        prod.poll(0)
        print(round_caller_json) #PRODUCE EVENT

        #OS JOGOS ACONTECEM EM SIMULTÂNEO POR MEIO DAS THREADS

        threads = []

        for time_a, time_b in (matches_season[rodada]):
            jogo_thread = threading.Thread(
                target=simulator_match, 
                args=(times_season[time_a], times_season[time_b], prod) # Passa os times como argumentos
            )
            
            threads.append((jogo_thread, time_a, time_b))
            jogo_thread.start()

        for jogo_thread, time_a, time_b in threads:
            jogo_thread.join()

        #RESET OS VALORES DOS TIMES APÓS A RODADA, PARA UMA PRÓXIMA RODADA      
        [reset_team(times_season[i]) for i in range(NUM_TIMES)]
            
        print(f"--- TODOS OS JOGOS DA RODADA {rodada+1} ACABARAM ---")

            #CRIANDO JSON
        round_caller = {
            'tipo': "RODADA_FIM",
            'rodada': rodada+1
        }
        round_caller_json = json.dumps(round_caller, ensure_ascii=False)
        prod.produce(topic=topic, value=round_caller_json.encode("utf-8"), callback=delivery_callback)
        prod.poll(0)
        print(round_caller_json) #PRODUCE EVENT

    #CRIANDO JSON
    season_ender = {
        'tipo': "SEASON_END"
    }
    season_ender_json = json.dumps(season_ender, ensure_ascii=False)
    prod.produce(topic=topic, value=season_ender_json.encode("utf-8"), callback=delivery_callback)
    prod.poll(0)
    print(season_ender_json) #PRODUCE EVENT

    prod.flush()


if __name__ == "__main__":
    init_season = season_maker()