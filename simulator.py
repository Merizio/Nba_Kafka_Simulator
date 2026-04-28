import manipulator
import pandas as pd
import random
import time as tempo

"""
SIMULADOR DE UM JOGO DA NBA

FEITO POR FRANCISCO MERIZIO E LUCAS DANIEL
DISCIPLINA DE SIST. ORIENTADOS A EVENTO
"""

#ESCOLHA DOS TIMES PARA O SIMULADOR
csv=pd.read_csv("./data/list_nba_players.csv")

csv = csv.drop(columns=csv.columns[0])

time_A, time_B = manipulator.escolher_times(csv)

manipulator.exibir_partida(time_A, time_B)

time_ataque, time_defesa = time_A, time_B
#COMEÇAR O JOGO
print("\nComeça a Partida")
for i in range(4):
    print(f"\n\nInício do {i+1}o quarto!  {time_A.nome} {time_A.pontos} X {time_B.pontos} {time_B.nome}")
    posses = random.randint(60, 80)

    for n in range(posses):
        #ALEATORIZACAO DA JOGADA
        jogada = random.random()

        #SUBTITUICAO
        if(0.236<jogada<0.296):
            jogador1 = manipulator.escolher_jogador(time_ataque.titulares, 'min')
            jogador2 = manipulator.escolher_jogador(time_ataque.reservas, 'max')
            print(f"Mudança do {time_ataque.nome}: Sai {jogador1.Player} e entra {jogador2.Player}!")
            
            time_ataque.titulares.remove(jogador1)
            time_ataque.reservas.remove(jogador2)
            time_ataque.titulares.append(jogador2)
            time_ataque.reservas.append(jogador1)

        #TURNOVER
        if(jogada<0.136):
            jogador = manipulator.escolher_jogador(time_ataque.titulares, 'max')
            print(f"O jogador {jogador.Player} do {time_ataque.nome} acaba de cometer um Turnover!")
        
        #FALTA
        elif(0.136<jogada<0.236):
            jogador1 = manipulator.escolher_jogador(time_ataque.titulares, 'equal')
            jogador2 = manipulator.escolher_jogador(time_defesa.titulares, 'equal')
            print(f"Falta cometida pelo {jogador1.Player} no {jogador2.Player}! Bola pro {time_defesa.nome}!")

        #TENTATIVA DE ARREMESSO
        else:
            jogador = manipulator.escolher_jogador(time_ataque.titulares, 'max')
            chance = random.random()
            if(chance<0.585):
                #2 PONTOS
                chance = random.random()
                if(chance <= jogador.FG):
                    print(f"Jogador {jogador.Player} do {time_ataque.nome} fez uma cesta de 2 Pontos!")
                    time_ataque.pontos+=2
                    jogador.pts+=2
            else:
                #3 PONTOS
                chance = random.random()
                if(chance <= jogador.P3):
                    print(f"Jogador {jogador.Player} do {time_ataque.nome} fez uma cesta de 3 Pontos!")
                    time_ataque.pontos+=3
                    jogador.pts+=3

        #INVERSAO DA POSSE
        time_ataque, time_defesa = time_defesa, time_ataque
        #tempo.sleep(0.1)

#STATUS FINAL DA PARTIDA
print("\nFinal de Partida!\n")
manipulator.exibir_partida(time_A, time_B)