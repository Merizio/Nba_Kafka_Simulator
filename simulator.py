import manipulator
import pandas as pd

"""
SIMULADOR DE UM JOGO DA NBA

FEITO POR FRANCISCO MERIZIO E LUCAS DANIEL
DISCIPLINA DE SIST. ORIENTADOS A EVENTO
"""

#ESCOLHA DOS TIMES PARA O SIMULADOR
csv=pd.read_csv("./data/nbaplayersREFORMED.csv")
csv = csv.drop(columns=csv.columns[0])

time_A, time_B = manipulator.escolher_times(csv)

manipulator.exibir_partida(time_A, time_B)