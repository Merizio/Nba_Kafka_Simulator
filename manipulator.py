import pandas as pd
import random

class Jogador:
    def __init__(self, Player, Age, Team, Pos, Rank, FG, P3):
        self.Player = Player
        self.Age = Age
        self.Team = Team
        self.Pos = Pos
        self.FG = FG
        self.P3 = P3
        self.pts = 0
        self.Rank = Rank

    def exibir_jogador(self):
        print(f"{self.Player:>25} | {self.Pos:<2} | {self.pts}")
        

class Time:
    def __init__(self, nome, elenco):
        self.nome = nome
        self.titulares = []
        self.reservas = []
        self.pontos = 0

        todos = [Jogador(r.Player, r.Age, r.Team, r.Pos, r.Rank, r.FG, r.P3) for r in elenco.itertuples()]
        self.titulares = todos[:5]
        self.reservas = todos[5:]


    def exibir_time(self):
        print(f"\n{self.nome}\nTitulares:")
        for n in self.titulares:
            n.exibir_jogador()
        print("Reservas:")
        for n in self.reservas:
            n.exibir_jogador()

    def exibir_titulares(self):
        return [n.Player for n in self.titulares]
    def exibir_resevas(self):
        return [n.Player for n in self.reservas]


def escolher_times(nba):
    t1, t2 = pd.Series(nba["Team"].unique()).sample(2)

    elenco1 = nba[nba["Team"] == t1]
    elenco2 = nba[nba["Team"] == t2]
    #print(f"{t1} X {t2}")

    t1 = Time(t1, elenco1)
    t2 = Time(t2, elenco2)

    return t1, t2

def exibir_partida(time1, time2):
    print(f"PARTIDA DE HOJE:{' '*2}{time1.nome} {time1.pontos} X {time2.pontos} {time2.nome}")
    time1.exibir_time()
    time2.exibir_time()

def escolher_jogador(elenco, modo):
    
    if(modo == "equal"):
        return random.choice(elenco)
    
    pesos = []
    for n in elenco:
        if(modo == "min"):
            pesos.append(11-n.Rank)
        elif(modo == "max"):
            pesos.append(n.Rank)
        
    jogador = random.choices(elenco, weights=pesos, k=1)[0]
    return jogador